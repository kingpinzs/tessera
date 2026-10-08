package app.tileshell.music

import androidx.media3.common.C
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

/**
 * The music player's HTTP, where [MusicConnectRule] is carried out (phase 20, the review's R20-1; the owner's ruling
 * 2026-10-08): a station, everything its playlist names, every redirect of any of them, and the shell's own fetch of
 * a station's logo connect only to an address that was judged public.
 *
 * OkHttp, and not the platform's `HttpURLConnection` Media3 uses by default: that one resolves a name and connects
 * inside itself, with no place to stand between the two. Here the two places are the shell's own:
 *
 *  - **the name** — [judged]: every address the name gave is asked of [MusicConnectRule.mayConnect]; one private
 *    answer, or none, and the look-up throws. Nothing is connected.
 *  - **the socket** — [JudgedSockets]: the client's every TCP socket is one of these, and its `connect` asks the rule
 *    about the very address it was handed before it connects to it. This is what binds the check to the connection:
 *    whatever chose the address — the look-up above, an address literal (OkHttp does not ask its `Dns` about one), a
 *    second address tried after the first failed — it is judged at `connect`, and there is no look-up after that.
 *    A connection kept for re-use was opened by this same `connect`. No proxy is used ([Proxy.NO_PROXY]): through one
 *    the phone would connect to the proxy and the proxy would resolve the name, unjudged. HTTP/1.1 only, as before.
 *  - **the redirect** — [Hops]: the client follows none itself; each is [MusicConnectRule.hop]'s to judge, and the
 *    next request connects through the two places above like the first.
 *
 * Two lanes, so the home server's exception cannot leak: [strict] is everything above. [home] — its own connection
 * pool, no judging — carries ONLY a request whose address the shell queued for the home server ([servers]) and a
 * redirect that stays on that host; a redirect to any other host is handed to [strict]. A station that redirects to
 * the home server's host is in [strict], where a private address is refused.
 *
 * A refusal is a [RefusedAddress] — an `IOException` that names no address.
 *
 * @param qaHost the debug-only fixture host (`RadioNet.qaHost`), null in a release build
 * @param servers the addresses the shell queued for the home server (`MusicSources.servers`)
 * @param resolve a name's addresses; the platform's, but for a test
 */
class MusicHttp(
    private val qaHost: String?,
    private val servers: () -> Set<String> = { emptySet() },
    private val resolve: (String) -> List<InetAddress> = { Dns.SYSTEM.lookup(it) },
) {
    /** A connection, or a redirect, [MusicConnectRule] refused. */
    class RefusedAddress : IOException(MusicConnectRule.REFUSED)

    private val judged = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val answers = resolve(hostname)
            if (!MusicConnectRule.mayConnect(hostname, answers, homeServer = false, qaHost)) throw RefusedAddress()
            // Each answer keeps the name it was asked for, so the socket below reads the same host this did.
            return answers.map { InetAddress.getByAddress(hostname, it.address) }
        }
    }

    /** Sockets that connect only to an address [MusicConnectRule.mayConnect] takes — asked at `connect`, of that address. */
    private inner class JudgedSockets : SocketFactory() {
        override fun createSocket(): Socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?) = connect(endpoint, 0)

            override fun connect(endpoint: SocketAddress?, timeout: Int) {
                val to = endpoint as? InetSocketAddress
                val address = to?.address
                if (to == null || address == null || !MusicConnectRule.mayConnect(to.hostString, listOf(address), homeServer = false, qaHost)) throw RefusedAddress()
                super.connect(endpoint, timeout)
            }
        }

        // The forms that connect as they are made: OkHttp uses none of them, and nothing here may.
        override fun createSocket(host: String?, port: Int): Socket = throw RefusedAddress()
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = throw RefusedAddress()
        override fun createSocket(host: InetAddress?, port: Int): Socket = throw RefusedAddress()
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = throw RefusedAddress()
    }

    /** How many redirects a request has behind it, carried from [home] into [strict]. */
    private class Followed(val count: Int)

    /** The redirects of one request, each judged before it is followed ([MusicConnectRule.hop]). */
    private inner class Hops(private val homeServer: Boolean) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            var followed = request.tag(Followed::class.java)?.count ?: 0
            while (true) {
                val response = chain.proceed(request)
                if (response.code !in REDIRECTS) return response
                val to = response.header("Location")?.let { request.url.resolve(it) }
                response.close()
                val target = to?.let { MusicConnectRule.Target(it.toString(), it.scheme, it.host) }
                when (val hop = MusicConnectRule.hop(request.url.scheme, request.url.host, target, followed, homeServer, qaHost)) {
                    is MusicConnectRule.Hop.Refused -> throw if (hop.message == MusicConnectRule.REFUSED) RefusedAddress() else IOException(hop.message)
                    is MusicConnectRule.Hop.Follow -> {
                        followed++
                        // The same request — its range, its Icy-MetaData — to the new address, as Media3's own source sent it on.
                        val next = request.newBuilder().url(to!!).tag(Followed::class.java, Followed(followed)).build()
                        // Off the home server's host: the rest of the way is a station's.
                        if (homeServer && !hop.homeServer) return strict.newCall(next).execute()
                        request = next
                    }
                }
            }
        }
    }

    private val bare: OkHttpClient = OkHttpClient.Builder()
        .dns(judged)
        .socketFactory(JudgedSockets())
        .proxy(Proxy.NO_PROXY)
        .protocols(listOf(Protocol.HTTP_1_1))
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val strict: OkHttpClient = bare.newBuilder().addInterceptor(Hops(homeServer = false)).build()

    /** The home server's lane: the platform's own look-up and sockets, as before — and a pool nothing else draws from. */
    private val home: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool())
        .protocols(listOf(Protocol.HTTP_1_1))
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .addInterceptor(Hops(homeServer = true))
        .build()

    /** A queued home-server address as the HTTP stack writes it, so the two are compared in one form. */
    private val written = ConcurrentHashMap<String, String>()

    private fun isHomeServer(url: HttpUrl): Boolean {
        val asked = url.toString()
        return servers().any { queued -> written.getOrPut(queued) { queued.toHttpUrlOrNull()?.toString().orEmpty() } == asked }
    }

    /** The player's calls — the factory of its ONE http data source: the home server's own queued address in [home], all else in [strict]. */
    val calls: Call.Factory = Call.Factory { request -> (if (isHomeServer(request.url)) home else strict).newCall(request) }

    /**
     * One picture — a station's logo — through the judged client, or null: at most [maxBytes], the whole answer
     * inside [totalMs], and NO redirect followed (the request carries the shell's headers, and a logo's host is not
     * to send the phone on). No exception leaves it, and nothing is logged: an address may carry a query.
     */
    fun bytes(url: String, headers: Map<String, String>, maxBytes: Long, totalMs: Long = TOTAL_MS): ByteArray? = try {
        val request = Request.Builder().url(url).apply { for ((k, v) in headers) header(k, v) }.build()
        bare.newBuilder().callTimeout(totalMs, TimeUnit.MILLISECONDS).build().newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null || body.contentLength() > maxBytes) return@use null
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            val input = body.byteStream()
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n.toLong() > maxBytes) return@use null
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        // A header or an address the client will not write: its message quotes the value, so it goes nowhere.
        null
    }

    companion object {
        /** Media3's own http source's connect and read timeouts, kept. */
        const val TIMEOUT_MS = 8_000L

        /** The longest a logo's whole answer may take (`VideoHttp.TOTAL_READ_MS`'s bound). */
        const val TOTAL_MS = 60_000L

        /** The answers Media3's own http source followed for a GET. */
        private val REDIRECTS = setOf(300, 301, 302, 303, 307, 308)

        private const val CAUSES_READ = 8

        /**
         * The platform's own User-Agent, as Media3's default source sent it — less any character a header may not
         * carry (the client throws for one, and a phone's model name is in this text); null when there is none.
         */
        fun platformAgent(): String? = System.getProperty("http.agent")?.filter { it in ' '..'~' }?.takeIf { it.isNotBlank() }

        /** Whether [error], or a cause of it, is a [RefusedAddress]. */
        fun refused(error: Throwable?): Boolean = generateSequence(error) { it.cause }.take(CAUSES_READ).any { it is RefusedAddress }

        /**
         * The player's load-error policy: Media3's default, but a [RefusedAddress] is never loaded again — the player
         * would otherwise ask for a refused address three or six more times before it reported the error.
         */
        fun refusalIsFinal(): LoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
                if (refused(loadErrorInfo.exception)) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
        }
    }
}
