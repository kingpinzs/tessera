package app.tileshell.tiles.api

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.engine.BadgeStore
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Live Tile API state per owner package (R5 §4b "Storage"): the 5-deep queue, scheduled notifications, the API badge
 * (source S1) and the owner's install identity. One JSON file plus copied images per owner under
 * files/livetile/<package>/, all owned by the shell. Publishes to [LiveTileEngine] and [BadgeStore].
 *
 * Identity: state is bound to the owner's signing certificate and first-install time. A package that was uninstalled
 * (broadcast) or that reappears with a different signer or a new install (checked on load, on package-added and on
 * every call) inherits nothing: its tiles, queue, schedule and badges are wiped.
 */
class LiveTileStore private constructor(private val context: Context) {
    data class StoredImage(val src: String, val file: String, val bytes: Long)
    data class QueueEntry(val seq: Long, val tag: String?, val xml: String, val images: List<StoredImage>, val expiresAtMs: Long?, val addedAtMs: Long)
    data class ScheduledEntry(val id: String, val deliverAtMs: Long, val tag: String?, val xml: String, val images: List<StoredImage>, val expiresAtMs: Long?)
    data class Identity(val signerSha256: String, val firstInstallMs: Long)

    private class Owner(val pkg: String, var identity: Identity) {
        var queueEnabled = false
        var queue: List<QueueEntry> = emptyList()
        var scheduled: List<ScheduledEntry> = emptyList()
        var badge: Int? = null
        var badgeExpiresAtMs: Long? = null
        var nextSeq = 1L
        var updatedAtMs = 0L
    }

    sealed interface Outcome {
        data object Ok : Outcome
        data class Rejected(val reason: String, val detail: String) : Outcome
    }

    private val root = File(context.filesDir, "livetile")
    private val owners = HashMap<String, Owner>()
    private val lock = Any()
    private var loaded = false
    private val thread = HandlerThread("livetile").apply { start() }
    val handler = Handler(thread.looper)
    private val sweepRunnable = Runnable { sweep("timer") }

    /** Packages whose Start preview currently comes from the API queue (read by the notification feed). */
    @Volatile var previewOwners: Set<String> = emptySet()
        private set

    fun start() {
        handler.post { ensureLoaded() }
    }

    private fun ensureLoaded() {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            root.mkdirs()
            val dirs = root.listFiles { f -> f.isDirectory }.orEmpty()
            for (dir in dirs) {
                val owner = runCatching { read(dir) }.onFailure {
                    Diagnostics.add("livetile", "store: unreadable state for ${dir.name}, wiped: $it")
                }.getOrNull()
                if (owner == null) {
                    dir.deleteRecursively()
                    continue
                }
                owners[owner.pkg] = owner
            }
            Diagnostics.add("livetile", "store loaded ${owners.size} owners")
            for (pkg in owners.keys.toList()) {
                if (checkIdentityLocked(pkg, "load")) republishLocked(pkg)
            }
            sweepLocked(System.currentTimeMillis(), "load")
        }
    }

    // ---------- identity ----------

    fun identityOf(pkg: String): Identity? = try {
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo
        val cert = when {
            signing == null -> null
            signing.hasMultipleSigners() -> signing.apkContentsSigners.firstOrNull()
            else -> signing.signingCertificateHistory.lastOrNull()
        }
        cert?.let { Identity(sha256(it.toByteArray()), info.firstInstallTime) }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * Returns true when [pkg] has no stored state or its stored state belongs to the installed package.
     * Otherwise wipes the state (and every badge source for the package) and returns false.
     */
    fun checkIdentity(pkg: String, why: String): Boolean {
        ensureLoaded()
        synchronized(lock) { return checkIdentityLocked(pkg, why) }
    }

    private fun checkIdentityLocked(pkg: String, why: String): Boolean {
        val owner = owners[pkg] ?: return true
        val current = identityOf(pkg)
        val same = current != null && current.firstInstallMs == owner.identity.firstInstallMs &&
            (current.signerSha256 == owner.identity.signerSha256 || hasCertificate(pkg, owner.identity.signerSha256))
        if (!same) {
            wipeLocked(pkg, if (current == null) "not installed ($why)" else "install identity changed ($why)")
            BadgeStore.clearPackage(pkg)
        }
        return same
    }

    private fun hasCertificate(pkg: String, sha256Hex: String): Boolean = runCatching {
        context.packageManager.hasSigningCertificate(pkg, hexToBytes(sha256Hex), PackageManager.CERT_INPUT_SHA256)
    }.getOrDefault(false)

    fun onPackageRemoved(pkg: String, why: String) {
        ensureLoaded()
        synchronized(lock) {
            wipeLocked(pkg, why)
            BadgeStore.clearPackage(pkg)
        }
        LegacyBadgeReceiver.forget(pkg)
    }

    fun onPackageAdded(pkg: String, replacing: Boolean) {
        ensureLoaded()
        synchronized(lock) {
            val hadState = owners.containsKey(pkg)
            val same = checkIdentityLocked(pkg, if (replacing) "package replaced" else "package added")
            if (!replacing && !hadState) BadgeStore.clearPackage(pkg)
            if (same && hadState) republishLocked(pkg)
        }
    }

    private fun wipeLocked(pkg: String, why: String) {
        val had = owners.remove(pkg) != null
        val dir = ownerDir(pkg)
        val existed = dir.exists()
        dir.deleteRecursively()
        if (pkg in previewOwners) {
            previewOwners = previewOwners - pkg
            LiveTileEngine.publish(LiveTileEngine.packageKey(pkg), null)
        }
        BadgeStore.set(pkg, BadgeStore.Source.API, 0)
        if (had || existed) Diagnostics.add("livetile", "wipe $pkg: $why (tiles, queue, schedule, badge cleared)")
        rescheduleLocked()
    }

    // ---------- verbs ----------

    private fun ownerFor(pkg: String): Owner? {
        owners[pkg]?.let { return it }
        val identity = identityOf(pkg) ?: return null
        return Owner(pkg, identity).also { owners[pkg] = it }
    }

    /** Packages that have used the Live Tile API (Settings > Live tile access lists them with their kill switch). */
    fun callerPackages(): Set<String> = synchronized(lock) { ensureLoaded(); owners.keys.toSet() }

    fun ownerDir(pkg: String) = File(root, pkg)

    fun imageBytesUsed(pkg: String): Long = synchronized(lock) {
        val o = owners[pkg] ?: return 0
        (o.queue.flatMap { it.images } + o.scheduled.flatMap { it.images }).sumOf { it.bytes }
    }

    fun update(pkg: String, xml: String, images: List<StoredImage>, tag: String?, expiresAtMs: Long?, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = ownerFor(pkg) ?: return Outcome.Rejected(LiveTileProtocol.Error.IDENTITY, "package not installed")
            quotaLocked(owner, images)?.let { return it }
            val entry = QueueEntry(owner.nextSeq++, tag, xml, images, expiresAtMs, nowMs)
            val result = TileQueuePolicy.insert(owner.queue, entry, owner.queueEnabled) { it.tag }
            owner.queue = result.queue
            deleteImages(pkg, result.evicted.flatMap { it.images })
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            republishLocked(pkg)
            rescheduleLocked()
            return Outcome.Ok
        }
    }

    fun clear(pkg: String, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = owners[pkg] ?: return Outcome.Ok
            deleteImages(pkg, owner.queue.flatMap { it.images })
            owner.queue = emptyList()
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            republishLocked(pkg)
            rescheduleLocked()
            return Outcome.Ok
        }
    }

    fun enableQueue(pkg: String, enabled: Boolean, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = ownerFor(pkg) ?: return Outcome.Rejected(LiveTileProtocol.Error.IDENTITY, "package not installed")
            owner.queueEnabled = enabled
            if (!enabled) {
                val result = TileQueuePolicy.disable(owner.queue)
                owner.queue = result.queue
                deleteImages(pkg, result.evicted.flatMap { it.images })
            }
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            republishLocked(pkg)
            return Outcome.Ok
        }
    }

    fun schedule(pkg: String, entry: ScheduledEntry, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = ownerFor(pkg) ?: return Outcome.Rejected(LiveTileProtocol.Error.IDENTITY, "package not installed")
            val replaced = owner.scheduled.filter { it.id.equals(entry.id, ignoreCase = true) }
            if (replaced.isEmpty() && owner.scheduled.size >= MAX_SCHEDULED) {
                return Outcome.Rejected(LiveTileProtocol.Error.QUOTA, "${owner.scheduled.size} scheduled notifications (max $MAX_SCHEDULED)")
            }
            quotaLocked(owner, entry.images, replaced.flatMap { it.images })?.let { return it }
            deleteImages(pkg, replaced.flatMap { it.images })
            owner.scheduled = owner.scheduled - replaced.toSet() + entry
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            rescheduleLocked()
            return Outcome.Ok
        }
    }

    fun setBadge(pkg: String, count: Int, expiresAtMs: Long?, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = ownerFor(pkg) ?: return Outcome.Rejected(LiveTileProtocol.Error.IDENTITY, "package not installed")
            if (count <= 0) {
                owner.badge = null
                owner.badgeExpiresAtMs = null
            } else {
                owner.badge = minOf(count, MAX_BADGE)
                owner.badgeExpiresAtMs = expiresAtMs
            }
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            publishBadgeLocked(owner)
            rescheduleLocked()
            return Outcome.Ok
        }
    }

    fun hasOwner(pkg: String): Boolean = synchronized(lock) { owners.containsKey(pkg) }

    private fun quotaLocked(owner: Owner, adding: List<StoredImage>, freeing: List<StoredImage> = emptyList()): Outcome.Rejected? {
        val used = (owner.queue.flatMap { it.images } + owner.scheduled.flatMap { it.images }).sumOf { it.bytes } - freeing.sumOf { it.bytes }
        val next = used + adding.sumOf { it.bytes }
        return if (next > MAX_OWNER_IMAGE_BYTES) Outcome.Rejected(LiveTileProtocol.Error.QUOTA, "images $next bytes > $MAX_OWNER_IMAGE_BYTES") else null
    }

    private fun deleteImages(pkg: String, images: List<StoredImage>) {
        val dir = ownerDir(pkg)
        images.forEach { File(dir, it.file).delete() }
    }

    // ---------- publishing ----------

    fun republish(pkg: String) {
        ensureLoaded()
        synchronized(lock) { republishLocked(pkg) }
    }

    private fun republishLocked(pkg: String) {
        val owner = owners[pkg]
        val disabled = LiveTileSettings.isDisabled(context, pkg)
        val key = LiveTileEngine.packageKey(pkg)
        val content = if (owner == null || disabled) null else render(owner)
        if (content != null) {
            previewOwners = previewOwners + pkg
            LiveTileEngine.publish(key, content)
        } else if (pkg in previewOwners) {
            previewOwners = previewOwners - pkg
            LiveTileEngine.publish(key, null)
        }
        if (owner != null) publishBadgeLocked(owner) else BadgeStore.set(pkg, BadgeStore.Source.API, 0)
    }

    private fun publishBadgeLocked(owner: Owner) {
        val disabled = LiveTileSettings.isDisabled(context, owner.pkg)
        BadgeStore.set(owner.pkg, BadgeStore.Source.API, if (disabled) 0 else owner.badge ?: 0)
    }

    private fun render(owner: Owner): TileContent? {
        val dir = ownerDir(owner.pkg)
        val faces = mutableListOf<TileFace>()
        for (entry in owner.queue.asReversed()) {
            val payload = (TileXmlValidator.validate(entry.xml) as? TileXmlResult.Valid)?.payload ?: continue
            val files = entry.images.associate { it.src to File(dir, it.file) }
            faces += TileRenderer.faces(payload) { src -> files[src] }
            if (faces.size >= MAX_FACES) break
        }
        if (faces.isEmpty()) return null
        val newest = owner.queue.lastOrNull()?.addedAtMs ?: 0L
        val transition = if (faces.all { it is TileFace.Photo }) FaceTransition.CROSSFADE else FaceTransition.FLIP
        return TileContent(faces.take(MAX_FACES), transition, sourceTimeMs = newest, sourceTag = "livetile")
    }

    // ---------- expiry and scheduled delivery ----------

    fun requestSweep(why: String) {
        handler.post { sweep(why) }
    }

    private fun sweep(why: String) {
        ensureLoaded()
        synchronized(lock) { sweepLocked(System.currentTimeMillis(), why) }
    }

    private fun sweepLocked(nowMs: Long, why: String) {
        for (owner in owners.values.toList()) {
            var changed = false
            val due = owner.scheduled.filter { it.deliverAtMs <= nowMs }.sortedBy { it.deliverAtMs }
            if (due.isNotEmpty()) {
                owner.scheduled = owner.scheduled - due.toSet()
                for (s in due) {
                    // Windows: scheduled notifications expire three days after delivery unless the app set a time (R5 §1.4).
                    val expires = s.expiresAtMs ?: (s.deliverAtMs + SCHEDULED_DEFAULT_EXPIRY_MS)
                    val entry = QueueEntry(owner.nextSeq++, s.tag, s.xml, s.images, expires, nowMs)
                    val result = TileQueuePolicy.insert(owner.queue, entry, owner.queueEnabled) { it.tag }
                    owner.queue = result.queue
                    deleteImages(owner.pkg, result.evicted.flatMap { it.images })
                    Diagnostics.add("livetile", "deliver ${owner.pkg} scheduled id=${s.id} due=${s.deliverAtMs} late=${nowMs - s.deliverAtMs}ms ($why)")
                }
                changed = true
            }
            val pruned = TileQueuePolicy.prune(owner.queue, nowMs) { it.expiresAtMs }
            if (pruned.expired.isNotEmpty()) {
                owner.queue = pruned.kept
                deleteImages(owner.pkg, pruned.expired.flatMap { it.images })
                Diagnostics.add("livetile", "expire ${owner.pkg} ${pruned.expired.size} queued (${pruned.expired.map { it.seq }}) ($why)")
                changed = true
            }
            val badgeExpiry = owner.badgeExpiresAtMs
            if (owner.badge != null && badgeExpiry != null && badgeExpiry <= nowMs) {
                owner.badge = null
                owner.badgeExpiresAtMs = null
                Diagnostics.add("livetile", "expire ${owner.pkg} api badge ($why)")
                changed = true
            }
            if (changed) {
                persistLocked(owner)
                republishLocked(owner.pkg)
            }
        }
        rescheduleLocked()
    }

    private fun rescheduleLocked() {
        val next = owners.values.flatMap { o ->
            o.queue.mapNotNull { it.expiresAtMs } + o.scheduled.map { it.deliverAtMs } + listOfNotNull(o.badgeExpiresAtMs.takeIf { o.badge != null })
        }.minOrNull()
        handler.removeCallbacks(sweepRunnable)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, LiveTileSystemReceiver::class.java).setAction(LiveTileSystemReceiver.ACTION_SWEEP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (next == null) {
            alarms.cancel(pending)
            return
        }
        // In-process timer for on-time delivery while the shell runs; the alarm covers a killed process.
        handler.postDelayed(sweepRunnable, (next - System.currentTimeMillis()).coerceAtLeast(0L) + 50L)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
    }

    // ---------- persistence ----------

    private fun persistLocked(owner: Owner) {
        val dir = ownerDir(owner.pkg)
        dir.mkdirs()
        val json = JSONObject()
            .put("v", 1)
            .put("pkg", owner.pkg)
            .put("signer", owner.identity.signerSha256)
            .put("firstInstall", owner.identity.firstInstallMs)
            .put("queueEnabled", owner.queueEnabled)
            .put("nextSeq", owner.nextSeq)
            .put("updatedAt", owner.updatedAtMs)
            .put("queue", JSONArray().apply {
                owner.queue.forEach { e ->
                    put(JSONObject().put("seq", e.seq).put("tag", e.tag ?: JSONObject.NULL).put("xml", e.xml)
                        .put("expiresAt", e.expiresAtMs ?: JSONObject.NULL).put("addedAt", e.addedAtMs).put("images", imagesJson(e.images)))
                }
            })
            .put("scheduled", JSONArray().apply {
                owner.scheduled.forEach { s ->
                    put(JSONObject().put("id", s.id).put("deliverAt", s.deliverAtMs).put("tag", s.tag ?: JSONObject.NULL).put("xml", s.xml)
                        .put("expiresAt", s.expiresAtMs ?: JSONObject.NULL).put("images", imagesJson(s.images)))
                }
            })
        owner.badge?.let { json.put("badge", it) }
        owner.badgeExpiresAtMs?.let { json.put("badgeExpiresAt", it) }
        val file = File(dir, STATE_FILE)
        val tmp = File(dir, "$STATE_FILE.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) Diagnostics.add("livetile", "store: rename failed for ${owner.pkg}")
        val referenced = (owner.queue.flatMap { it.images } + owner.scheduled.flatMap { it.images }).map { it.file }.toSet()
        dir.listFiles { f -> f.name.startsWith(IMAGE_PREFIX) && f.name !in referenced && f.lastModified() < System.currentTimeMillis() - ORPHAN_GRACE_MS }
            ?.forEach { it.delete() }
    }

    private fun imagesJson(images: List<StoredImage>) = JSONArray().apply {
        images.forEach { put(JSONObject().put("src", it.src).put("file", it.file).put("bytes", it.bytes)) }
    }

    private fun read(dir: File): Owner? {
        val file = File(dir, STATE_FILE)
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val pkg = json.getString("pkg")
        if (pkg != dir.name) return null
        val owner = Owner(pkg, Identity(json.getString("signer"), json.getLong("firstInstall")))
        owner.queueEnabled = json.optBoolean("queueEnabled", false)
        owner.nextSeq = json.optLong("nextSeq", 1L)
        owner.updatedAtMs = json.optLong("updatedAt", 0L)
        owner.queue = json.getJSONArray("queue").objects().map { o ->
            QueueEntry(o.getLong("seq"), o.optStringOrNull("tag"), o.getString("xml"), images(o), o.optLongOrNull("expiresAt"), o.getLong("addedAt"))
        }
        owner.scheduled = json.getJSONArray("scheduled").objects().map { o ->
            ScheduledEntry(o.getString("id"), o.getLong("deliverAt"), o.optStringOrNull("tag"), o.getString("xml"), images(o), o.optLongOrNull("expiresAt"))
        }
        if (json.has("badge")) owner.badge = json.getInt("badge")
        owner.badgeExpiresAtMs = json.optLongOrNull("badgeExpiresAt")
        return owner
    }

    private fun images(o: JSONObject) = o.getJSONArray("images").objects().map { StoredImage(it.getString("src"), it.getString("file"), it.getLong("bytes")) }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optStringOrNull(k: String) = if (isNull(k)) null else optString(k)
    private fun JSONObject.optLongOrNull(k: String) = if (isNull(k)) null else optLong(k)

    fun changeUri(pkg: String): Uri = Uri.parse("content://${LiveTileProtocol.AUTHORITY}/tiles/$pkg/primary")

    companion object {
        const val MAX_SCHEDULED = 32
        const val MAX_BADGE = 999
        const val MAX_FACES = 9
        const val MAX_OWNER_IMAGE_BYTES = 16L * 1024 * 1024
        const val SCHEDULED_DEFAULT_EXPIRY_MS = 3L * 24 * 60 * 60 * 1000
        const val IMAGE_PREFIX = "img-"
        private const val STATE_FILE = "state.json"
        private const val ORPHAN_GRACE_MS = 10 * 60 * 1000L

        @Volatile private var instance: LiveTileStore? = null

        fun get(context: Context): LiveTileStore =
            instance ?: synchronized(this) { instance ?: LiveTileStore(context.applicationContext ?: context).also { instance = it } }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
