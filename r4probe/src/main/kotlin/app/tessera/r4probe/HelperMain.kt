package app.tessera.r4probe

import android.content.AttributionSource
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * R4 parts 1-4, the helper itself (PLAN.md R4: "app_process under shell uid on One UI 8 + daemonise; binder handoff to
 * the app; each toggle via shell uid; survival when Wireless debugging goes off / the app is killed").
 *
 * Started by the host script, never by the app:
 *   CLASSPATH=<this APK> app_process /system/bin app.tessera.r4probe.HelperMain <mode>
 * Modes:
 *   --probe          print who this process is (uid, SELinux context, build) and whether the hidden calls the handoff
 *                    needs are reachable; exit.
 *   --list-toggles   print the allow-list (Toggles.ALL), one tab-separated line each:
 *                    name, read, on, off, on-pattern, off-pattern, probe (or "-"), the plan item.
 *   --daemon <tag>   stay up: write a pid file and a log under /data/local/tmp, and whenever the probe app has a new
 *                    process, hand it [HelperBinder] through the app's provider (the way `content call` reaches a
 *                    provider: IActivityManager.getContentProviderExternal, then IContentProvider.call).
 *
 * The binder answers two calls, both for the app's own uid only: PING (who am I, since when) and TOGGLE (read / on /
 * off for a toggle NAMED in Toggles.ALL — never a command string from the caller).
 */
object HelperMain {
    const val APP = "app.tessera.r4probe"
    const val AUTHORITY = "app.tessera.r4probe.helper"
    const val PING = IBinder.FIRST_CALL_TRANSACTION
    const val TOGGLE = IBinder.FIRST_CALL_TRANSACTION + 1

    private var logFile: File? = null
    private val startedAt = SystemClock.elapsedRealtime()

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            // Tab-separated: the commands and patterns contain "|" (pipes, alternations); none contains a tab.
            "--list-toggles" -> Toggles.ALL.forEach {
                println(listOf(it.name, it.read, it.on, it.off, it.onPattern, it.offPattern, it.probe ?: "-", it.forItem)
                    .joinToString("\t"))
            }
            "--daemon" -> daemon(args.getOrNull(1) ?: "usb")
            else -> probe()
        }
    }

    private fun probe() {
        println("uid            ${Process.myUid()}")
        println("pid            ${Process.myPid()}")
        println("selinux        ${runCatching { File("/proc/self/attr/current").readText().trim('\u0000', '\n') }.getOrElse { "unreadable (${it.javaClass.simpleName})" }}")
        println("sdk            ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
        println("model          ${android.os.Build.MODEL}")
        println("oneui          ${prop("ro.build.version.oneui").ifBlank { "not a One UI build" }}")
        println("activity svc   ${runCatching { if (activityManager() != null) "reachable" else "null" }.getOrElse { "FAILED ${it.javaClass.simpleName}: ${it.message}" }}")
    }

    private fun daemon(tag: String) {
        logFile = File("/data/local/tmp/r4helper-$tag.log")
        File("/data/local/tmp/r4helper-$tag.pid").writeText("${Process.myPid()}\n")
        log("started: uid ${Process.myUid()} pid ${Process.myPid()} tag $tag sdk ${android.os.Build.VERSION.SDK_INT}")
        val binder = HelperBinder(tag)
        var deliveredTo = -1
        while (true) {
            val pid = appPid()
            if (pid != null && pid != deliveredTo) {
                val uid = uidOf(pid)
                runCatching { deliver(binder, tag) }
                    .onSuccess { deliveredTo = pid; binder.appUid = uid; log("binder delivered to $APP pid $pid uid $uid") }
                    .onFailure { log("delivery to pid $pid FAILED: ${it.javaClass.name}: ${it.cause?.message ?: it.message}") }
            }
            if (pid == null && deliveredTo != -1) { log("$APP gone (was pid $deliveredTo); waiting for it"); deliveredTo = -1 }
            Thread.sleep(2000)
        }
    }

    /** IActivityManager.getContentProviderExternal + IContentProvider.call: what the `content call` shell command does. */
    private fun deliver(binder: IBinder, tag: String) {
        val am = activityManager() ?: error("no activity service")
        val token = Binder()
        val holder = am.javaClass.getMethod(
            "getContentProviderExternal", String::class.java, Int::class.javaPrimitiveType, IBinder::class.java, String::class.java,
        ).invoke(am, AUTHORITY, 0, token, "r4helper") ?: error("provider $AUTHORITY not found")
        try {
            val provider = holder.javaClass.getField("provider").get(holder) ?: error("holder has no provider")
            val extras = Bundle().apply { putBinder("binder", binder); putString("tag", tag) }
            val source = AttributionSource.Builder(Process.myUid()).setPackageName("com.android.shell").build()
            provider.javaClass.getMethod(
                "call", AttributionSource::class.java, String::class.java, String::class.java, String::class.java, Bundle::class.java,
            ).invoke(provider, source, AUTHORITY, "helper_binder", null, extras)
        } finally {
            runCatching {
                am.javaClass.getMethod("removeContentProviderExternalAsUser", String::class.java, IBinder::class.java, Int::class.javaPrimitiveType)
                    .invoke(am, AUTHORITY, token, 0)
            }
        }
    }

    private fun activityManager(): Any? {
        val service = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "activity") as? IBinder ?: return null
        return Class.forName("android.app.IActivityManager\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, service)
    }

    private fun appPid(): Int? = sh("pidof $APP").second.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()

    private fun uidOf(pid: Int): Int = runCatching {
        File("/proc/$pid/status").readLines().first { it.startsWith("Uid:") }.split(Regex("\\s+"))[1].toInt()
    }.getOrDefault(-1)

    /** Runs one fixed command string as this process's uid; the exit code and the combined output. */
    fun sh(cmd: String, timeoutS: Long = 20): Pair<Int, String> = try {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        // Read on a thread: reading first would block until the command ended, so a hung command never timed out.
        val buf = StringBuffer()
        val reader = Thread { runCatching { p.inputStream.bufferedReader().forEachLine { buf.append(it).append('\n') } } }
            .apply { isDaemon = true; start() }
        if (!p.waitFor(timeoutS, TimeUnit.SECONDS)) {
            p.destroyForcibly(); reader.join(1000)
            -2 to "$buf[timed out after ${timeoutS}s]"
        } else { reader.join(2000); p.exitValue() to buf.toString() }
    } catch (e: Exception) { -1 to "${e.javaClass.simpleName}: ${e.message}" }

    fun log(text: String) {
        val line = "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $text"
        println(line)
        runCatching { logFile?.appendText("$line\n") }
    }

    private fun prop(name: String): String = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    }.getOrDefault("")

    private class HelperBinder(val tag: String) : Binder() {
        @Volatile var appUid = -1

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val caller = Binder.getCallingUid()
            if (code != PING && code != TOGGLE) return super.onTransact(code, data, reply, flags)
            if (caller != appUid) {
                log("refused code $code from uid $caller (the app is uid $appUid)")
                reply?.writeInt(-100); reply?.writeString("refused: uid $caller is not the probe app")
                return true
            }
            when (code) {
                PING -> {
                    reply?.writeInt(0)
                    reply?.writeString("helper tag=$tag uid=${Process.myUid()} pid=${Process.myPid()} " +
                        "up=${(SystemClock.elapsedRealtime() - startedAt) / 1000}s")
                }
                TOGGLE -> {
                    val name = data.readString() ?: ""
                    val action = data.readString() ?: ""
                    val t = Toggles.named(name)
                    val cmd = when (action) {
                        "read" -> t?.read
                        "on" -> t?.on?.takeIf { t.probe == null }
                        "off" -> t?.off?.takeIf { t.probe == null }
                        "probe" -> t?.probe
                        else -> null
                    }
                    if (cmd == null) {
                        reply?.writeInt(-101); reply?.writeString("unknown toggle or action: $name $action")
                    } else {
                        val (exit, out) = sh(cmd)
                        log("toggle $name $action -> exit $exit")
                        reply?.writeInt(exit); reply?.writeString(out.trim().take(2000))
                    }
                }
            }
            return true
        }
    }
}
