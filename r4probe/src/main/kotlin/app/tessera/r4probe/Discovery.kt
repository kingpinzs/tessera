package app.tessera.r4probe

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Finds the phone's OWN Wireless-debugging services by mDNS: `_adb-tls-pairing._tcp` (only while the pairing dialog is
 * open) and `_adb-tls-connect._tcp` (while Wireless debugging is on). Other devices on the same Wi-Fi advertise the same
 * types, so a service counts only when it resolves to one of this phone's own addresses.
 */
object Discovery {
    const val PAIRING = "_adb-tls-pairing._tcp"
    const val CONNECT = "_adb-tls-connect._tcp"

    private fun ownAddresses(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().map { it.hostAddress ?: "" }.toSet()
    }.getOrDefault(emptySet())

    /** Debug: every service of [type] seen in [timeoutS], with its addresses and port, and this phone's own addresses. */
    fun dump(ctx: Context, type: String, timeoutS: Long): String {
        val nsd = ctx.getSystemService(NsdManager::class.java)
        val sb = StringBuffer("own ${ownAddresses()}; $type:\n")
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { sb.append("  started\n") }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { sb.append("  start failed $errorCode\n") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                sb.append("  found ${service.serviceName}\n")
                runCatching { nsd.registerServiceInfoCallback(service, ctx.mainExecutor, object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) { sb.append("    resolve failed $errorCode\n") }
                    override fun onServiceLost() {}
                    override fun onServiceInfoCallbackUnregistered() {}
                    override fun onServiceUpdated(info: NsdServiceInfo) { sb.append("    ${info.serviceName} ${info.hostAddresses} port ${info.port}\n") }
                }) }.onFailure { sb.append("    register failed ${it.message}\n") }
            }
        }
        runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { sb.append("  discover failed ${it.message}\n") }
        Thread.sleep(timeoutS * 1000)
        runCatching { nsd.stopServiceDiscovery(listener) }
        return sb.toString()
    }

    /**
     * Every host:port this phone advertises for [type] within [timeoutS] (newest first). After Wi-Fi drops and returns,
     * mDNS keeps the dead adbd's record for up to its TTL (~2 min) beside the new one, so callers try each in turn.
     */
    fun findAll(ctx: Context, type: String, timeoutS: Long): List<Pair<String, Int>> {
        val nsd = ctx.getSystemService(NsdManager::class.java)
        val mine = ownAddresses()
        val seen = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Int>>()
        val callbacks = mutableListOf<NsdManager.ServiceInfoCallback>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                val cb = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                    override fun onServiceLost() {}
                    override fun onServiceInfoCallbackUnregistered() {}
                    override fun onServiceUpdated(info: NsdServiceInfo) {
                        val host = info.hostAddresses.filterIsInstance<Inet4Address>().map { it.hostAddress ?: "" }
                            .firstOrNull { it in mine } ?: return
                        val hp = host to info.port
                        if (info.port > 0 && hp !in seen) seen.add(0, hp)
                    }
                }
                synchronized(callbacks) { callbacks += cb }
                runCatching { nsd.registerServiceInfoCallback(service, ctx.mainExecutor, cb) }
            }
        }
        runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        Thread.sleep(timeoutS * 1000)
        runCatching { nsd.stopServiceDiscovery(listener) }
        synchronized(callbacks) { callbacks.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } } }
        return seen.toList()
    }

    /** host:port of this phone's service of [type], or null after [timeoutS]. Blocks; call off the main thread. */
    fun find(ctx: Context, type: String, timeoutS: Long): Pair<String, Int>? {
        val nsd = ctx.getSystemService(NsdManager::class.java)
        val mine = ownAddresses()
        val done = CountDownLatch(1)
        var found: Pair<String, Int>? = null
        val callbacks = mutableListOf<NsdManager.ServiceInfoCallback>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { done.countDown() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                val cb = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                    override fun onServiceLost() {}
                    override fun onServiceInfoCallbackUnregistered() {}
                    override fun onServiceUpdated(info: NsdServiceInfo) {
                        val host = info.hostAddresses.filterIsInstance<Inet4Address>().map { it.hostAddress ?: "" }
                            .firstOrNull { it in mine } ?: return
                        if (found == null && info.port > 0) { found = host to info.port; done.countDown() }
                    }
                }
                synchronized(callbacks) { callbacks += cb }
                runCatching { nsd.registerServiceInfoCallback(service, ctx.mainExecutor, cb) }
            }
        }
        runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        done.await(timeoutS, TimeUnit.SECONDS)
        runCatching { nsd.stopServiceDiscovery(listener) }
        synchronized(callbacks) { callbacks.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } } }
        return found
    }
}
