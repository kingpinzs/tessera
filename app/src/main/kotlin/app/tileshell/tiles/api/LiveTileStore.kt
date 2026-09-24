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
import app.tileshell.tiles.TileSize
import app.tileshell.tiles.engine.BadgeStore
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.PackageSource
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Live Tile API state per owner package (R5 §4b "Storage"): the 5-deep queue, scheduled notifications, the API badge
 * (source S1) and the owner's install identity. One JSON file plus copied images per owner under
 * files/livetile/<package>/, all owned by the shell. Publishes to [LiveTileEngine] and [BadgeStore].
 *
 * Phase 02 build task 6 adds the owner's SECONDARY TILES (R5 §1.9 / §4b): each one is a record
 * (displayName, arguments, logo, size, showName) plus its own queue, schedule and badge, keyed (owner, tileId) and
 * held beside the owner's primary state in the same file, under the same 16 MB image quota. Deleting an owner
 * (uninstall, or an install identity that changed) takes its secondary tiles and their stored state with it.
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

    /**
     * One pinned secondary tile of [owner] (R5 §1.9). [size] is the size the owner asked to be pinned at; once the
     * tile is on Start the user owns its size, so the layout — not this — decides how it is drawn.
     */
    data class SecondaryRecord(
        val owner: String,
        val tileId: String,
        val displayName: String,
        val arguments: String,
        val logo: StoredImage?,
        val size: TileSize,
        val showName: Boolean,
    )

    /** The live-tile state of ONE tile: an owner's primary tile, or one of its secondary tiles. */
    private class TileState {
        var queueEnabled = false
        var queue: List<QueueEntry> = emptyList()
        var scheduled: List<ScheduledEntry> = emptyList()
        var badge: Int? = null
        var badgeExpiresAtMs: Long? = null
        var nextSeq = 1L

        fun images(): List<StoredImage> = queue.flatMap { it.images } + scheduled.flatMap { it.images }
    }

    private class Secondary(var record: SecondaryRecord, val state: TileState = TileState())

    private class Owner(val pkg: String, var identity: Identity) {
        val primary = TileState()
        /** tileId -> secondary tile, in creation order (secondary.findAll answers in this order). */
        val secondaries = LinkedHashMap<String, Secondary>()
        var updatedAtMs = 0L

        fun states(): List<TileState> = listOf(primary) + secondaries.values.map { it.state }
        fun images(): List<StoredImage> = states().flatMap { it.images() } + secondaries.values.mapNotNull { it.record.logo }
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

    private val secondaryState = MutableStateFlow<Map<String, List<SecondaryRecord>>>(emptyMap())

    /** Every owner's secondary tiles, owner package -> records. Start renders from this (through SecondaryTiles). */
    val secondaries: StateFlow<Map<String, List<SecondaryRecord>>> = secondaryState.asStateFlow()

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
            publishSecondariesLocked()
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
        val owner = owners.remove(pkg)
        val secondaryIds = owner?.secondaries?.keys?.toList().orEmpty()
        val dir = ownerDir(pkg)
        val existed = dir.exists()
        dir.deleteRecursively()
        // The API's own slot only: notifications or a playing session keep showing (L11-1).
        LiveTileEngine.publishPackage(pkg, PackageSource.API, null)
        BadgeStore.set(pkg, BadgeStore.Source.API, 0)
        // A secondary tile's content and badge hang off their own keys; the owner's tiles go with the owner (R5 §1.9).
        for (tileId in secondaryIds) {
            LiveTileEngine.publish(secondaryContentKey(pkg, tileId), null)
            BadgeStore.clearPackage(secondaryBadgeKey(pkg, tileId))
        }
        if (owner != null || existed) {
            Diagnostics.add("livetile", "wipe $pkg: $why (tiles, queue, schedule, badge cleared; ${secondaryIds.size} secondary tile(s): $secondaryIds)")
        }
        publishSecondariesLocked()
        rescheduleLocked()
    }

    // ---------- verbs ----------

    private fun ownerFor(pkg: String): Owner? {
        owners[pkg]?.let { return it }
        val identity = identityOf(pkg) ?: return null
        return Owner(pkg, identity).also { owners[pkg] = it }
    }

    /**
     * The state a verb addresses: the owner's primary tile ([tileId] null) or one of its secondary tiles.
     * A tileId that names no pinned secondary tile of this owner is "not-found" — Windows throws for exactly this
     * ("If you try to create a tile updater for a secondary tile that doesn't exist", R5 §1.9).
     */
    private fun stateLocked(owner: Owner, tileId: String?): TileState? =
        if (tileId == null) owner.primary else owner.secondaries[tileId]?.state

    private fun notFound(tileId: String) = Outcome.Rejected(LiveTileProtocol.Error.NOT_FOUND, "no secondary tile '$tileId' is pinned for this app")

    /** Packages that have used the Live Tile API (Settings > Live tile access lists them with their kill switch). */
    fun callerPackages(): Set<String> = synchronized(lock) { ensureLoaded(); owners.keys.toSet() }

    fun ownerDir(pkg: String) = File(root, pkg)

    /** Every byte this owner stores, over its primary tile, its secondary tiles and their logos: one quota per owner. */
    fun imageBytesUsed(pkg: String): Long = synchronized(lock) {
        owners[pkg]?.images()?.sumOf { it.bytes } ?: 0
    }

    fun update(pkg: String, tileId: String?, xml: String, images: List<StoredImage>, tag: String?, expiresAtMs: Long?, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = ownerFor(pkg) ?: return Outcome.Rejected(LiveTileProtocol.Error.IDENTITY, "package not installed")
            val state = stateLocked(owner, tileId) ?: return notFound(tileId!!)
            quotaLocked(owner, images)?.let { return it }
            val entry = QueueEntry(state.nextSeq++, tag, xml, images, expiresAtMs, nowMs)
            val result = TileQueuePolicy.insert(state.queue, entry, state.queueEnabled) { it.tag }
            state.queue = result.queue
            deleteImages(pkg, result.evicted.flatMap { it.images })
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            republishTileLocked(pkg, tileId)
            rescheduleLocked()
            return Outcome.Ok
        }
    }

    fun clear(pkg: String, tileId: String?, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = owners[pkg] ?: return if (tileId == null) Outcome.Ok else notFound(tileId)
            val state = stateLocked(owner, tileId) ?: return notFound(tileId!!)
            deleteImages(pkg, state.queue.flatMap { it.images })
            state.queue = emptyList()
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            republishTileLocked(pkg, tileId)
            rescheduleLocked()
            return Outcome.Ok
        }
    }

    fun enableQueue(pkg: String, tileId: String?, enabled: Boolean, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = ownerFor(pkg) ?: return Outcome.Rejected(LiveTileProtocol.Error.IDENTITY, "package not installed")
            val state = stateLocked(owner, tileId) ?: return notFound(tileId!!)
            state.queueEnabled = enabled
            if (!enabled) {
                val result = TileQueuePolicy.disable(state.queue)
                state.queue = result.queue
                deleteImages(pkg, result.evicted.flatMap { it.images })
            }
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            republishTileLocked(pkg, tileId)
            return Outcome.Ok
        }
    }

    fun schedule(pkg: String, tileId: String?, entry: ScheduledEntry, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = ownerFor(pkg) ?: return Outcome.Rejected(LiveTileProtocol.Error.IDENTITY, "package not installed")
            val state = stateLocked(owner, tileId) ?: return notFound(tileId!!)
            val replaced = state.scheduled.filter { it.id.equals(entry.id, ignoreCase = true) }
            if (replaced.isEmpty() && state.scheduled.size >= MAX_SCHEDULED) {
                return Outcome.Rejected(LiveTileProtocol.Error.QUOTA, "${state.scheduled.size} scheduled notifications (max $MAX_SCHEDULED)")
            }
            quotaLocked(owner, entry.images, replaced.flatMap { it.images })?.let { return it }
            deleteImages(pkg, replaced.flatMap { it.images })
            state.scheduled = state.scheduled - replaced.toSet() + entry
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            rescheduleLocked()
            return Outcome.Ok
        }
    }

    fun setBadge(pkg: String, tileId: String?, count: Int, expiresAtMs: Long?, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = ownerFor(pkg) ?: return Outcome.Rejected(LiveTileProtocol.Error.IDENTITY, "package not installed")
            val state = stateLocked(owner, tileId) ?: return notFound(tileId!!)
            if (count <= 0) {
                state.badge = null
                state.badgeExpiresAtMs = null
            } else {
                state.badge = minOf(count, MAX_BADGE)
                state.badgeExpiresAtMs = expiresAtMs
            }
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            publishBadgeLocked(pkg, tileId, state)
            rescheduleLocked()
            return Outcome.Ok
        }
    }

    fun hasOwner(pkg: String): Boolean = synchronized(lock) { owners.containsKey(pkg) }

    // ---------- secondary tiles ----------

    fun secondaryRecords(pkg: String): List<SecondaryRecord> = synchronized(lock) {
        ensureLoaded()
        owners[pkg]?.secondaries?.values?.map { it.record }.orEmpty()
    }

    fun secondary(pkg: String, tileId: String): SecondaryRecord? = synchronized(lock) {
        ensureLoaded()
        owners[pkg]?.secondaries?.get(tileId)?.record
    }

    /**
     * Creates the tile the user confirmed, or replaces an existing record in place (Windows' UpdateAsync: "Assign ALL
     * properties, including ones you aren't changing", and a duplicate tileId on requestCreate updates the tile
     * instead of making a second one, phase 02 Decisions). A replaced tile keeps its queue, schedule and badge: it is
     * the same tile.
     */
    fun putSecondary(record: SecondaryRecord, nowMs: Long): Outcome {
        ensureLoaded()
        synchronized(lock) {
            val owner = ownerFor(record.owner) ?: return Outcome.Rejected(LiveTileProtocol.Error.IDENTITY, "package not installed")
            val existing = owner.secondaries[record.tileId]
            if (existing == null && owner.secondaries.size >= MAX_SECONDARY_TILES) {
                return Outcome.Rejected(LiveTileProtocol.Error.QUOTA, "${owner.secondaries.size} secondary tiles (max $MAX_SECONDARY_TILES)")
            }
            quotaLocked(owner, listOfNotNull(record.logo), listOfNotNull(existing?.record?.logo))?.let { return it }
            if (existing == null) {
                owner.secondaries[record.tileId] = Secondary(record)
            } else {
                val oldLogo = existing.record.logo
                if (oldLogo != null && oldLogo.file != record.logo?.file) deleteImages(record.owner, listOf(oldLogo))
                existing.record = record
            }
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            publishSecondariesLocked()
            republishTileLocked(record.owner, record.tileId)
            return Outcome.Ok
        }
    }

    /** Deletes one secondary tile and everything stored under it (queue, schedule, badge, logo). */
    fun deleteSecondary(pkg: String, tileId: String, nowMs: Long): SecondaryRecord? {
        ensureLoaded()
        synchronized(lock) {
            val owner = owners[pkg] ?: return null
            val removed = owner.secondaries.remove(tileId) ?: return null
            deleteImages(pkg, removed.state.images() + listOfNotNull(removed.record.logo))
            LiveTileEngine.publish(secondaryContentKey(pkg, tileId), null)
            BadgeStore.clearPackage(secondaryBadgeKey(pkg, tileId))
            owner.updatedAtMs = nowMs
            persistLocked(owner)
            publishSecondariesLocked()
            rescheduleLocked()
            Diagnostics.add("livetile", "secondary tile $pkg/$tileId deleted")
            return removed.record
        }
    }

    /**
     * Keeps only the secondary tiles [pinned] (owner -> tileIds) still holds, so a tile the user unpinned stops
     * existing for its owner too (Windows: "Users can delete their secondary tiles at any time", R5 §1.9, and
     * `SecondaryTile.Exists` then answers false). Called with the live layout, never with a remembered one.
     */
    fun retainSecondaries(pinned: Map<String, Set<String>>) {
        ensureLoaded()
        synchronized(lock) {
            var changed = false
            for (owner in owners.values.toList()) {
                val keep = pinned[owner.pkg].orEmpty()
                for (tileId in owner.secondaries.keys.toList()) {
                    if (tileId in keep) continue
                    val removed = owner.secondaries.remove(tileId) ?: continue
                    deleteImages(owner.pkg, removed.state.images() + listOfNotNull(removed.record.logo))
                    LiveTileEngine.publish(secondaryContentKey(owner.pkg, tileId), null)
                    BadgeStore.clearPackage(secondaryBadgeKey(owner.pkg, tileId))
                    persistLocked(owner)
                    changed = true
                    Diagnostics.add("livetile", "secondary tile ${owner.pkg}/$tileId is no longer on Start: state dropped")
                }
            }
            if (changed) {
                publishSecondariesLocked()
                rescheduleLocked()
            }
        }
    }

    /**
     * The `arguments` attributes of the notifications currently queued on a tile, newest first: Windows' chaseable
     * tiles contract, handed to the owner as TILE_ACTIVATED_ARGS on launch (R5 §4b "Launch arguments", §1.7).
     */
    fun activatedArgs(pkg: String, tileId: String?): List<String> = synchronized(lock) {
        ensureLoaded()
        val owner = owners[pkg] ?: return emptyList()
        val state = stateLocked(owner, tileId) ?: return emptyList()
        state.queue.asReversed().mapNotNull { entry ->
            val payload = (TileXmlValidator.validate(entry.xml) as? TileXmlResult.Valid)?.payload ?: return@mapNotNull null
            payload.arguments ?: payload.bindings.firstNotNullOfOrNull { it.arguments }
        }
    }

    private fun quotaLocked(owner: Owner, adding: List<StoredImage>, freeing: List<StoredImage> = emptyList()): Outcome.Rejected? {
        val used = owner.images().sumOf { it.bytes } - freeing.sumOf { it.bytes }
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

    /** After the API master switch changes, every owner's tile and badge are published again. */
    fun republishAll() {
        ensureLoaded()
        synchronized(lock) { owners.keys.toList().forEach { republishLocked(it) } }
    }

    /** The owner's primary tile and every secondary tile it owns. */
    private fun republishLocked(pkg: String) {
        republishTileLocked(pkg, null)
        owners[pkg]?.secondaries?.keys?.toList()?.forEach { republishTileLocked(pkg, it) }
    }

    private fun republishTileLocked(pkg: String, tileId: String?) {
        val owner = owners[pkg]
        val state = owner?.let { stateLocked(it, tileId) }
        val disabled = LiveTileSettings.isDisabled(context, pkg) || !LiveTileSettings.isApiEnabled(context)
        val content = if (state == null || disabled) null else render(pkg, state)
        // A primary tile's queue is one of three producers of the app's tile (L11-1): it publishes its own slot, and the
        // engine's precedence puts it above notifications and below a playing session. A cleared queue clears only it.
        if (tileId == null) LiveTileEngine.publishPackage(pkg, PackageSource.API, content)
        else LiveTileEngine.publish(secondaryContentKey(pkg, tileId), content)
        if (state != null) publishBadgeLocked(pkg, tileId, state) else BadgeStore.set(badgeKeyFor(pkg, tileId), BadgeStore.Source.API, 0)
    }

    private fun publishBadgeLocked(pkg: String, tileId: String?, state: TileState) {
        val disabled = LiveTileSettings.isDisabled(context, pkg) || !LiveTileSettings.isApiEnabled(context)
        BadgeStore.set(badgeKeyFor(pkg, tileId), BadgeStore.Source.API, if (disabled) 0 else state.badge ?: 0)
    }

    private fun badgeKeyFor(pkg: String, tileId: String?) = if (tileId == null) pkg else secondaryBadgeKey(pkg, tileId)

    private fun publishSecondariesLocked() {
        secondaryState.value = owners.values
            .filter { it.secondaries.isNotEmpty() }
            .associate { o -> o.pkg to o.secondaries.values.map { it.record } }
    }

    private fun render(pkg: String, state: TileState): TileContent? {
        val dir = ownerDir(pkg)
        val faces = mutableListOf<TileFace>()
        var peeks = false
        for (entry in state.queue.asReversed()) {
            val payload = (TileXmlValidator.validate(entry.xml) as? TileXmlResult.Valid)?.payload ?: continue
            val files = entry.images.associate { it.src to File(dir, it.file) }
            faces += TileRenderer.faces(payload) { src -> files[src] }
            peeks = peeks || TileRenderer.peeks(payload)
            if (faces.size >= MAX_FACES) break
        }
        if (faces.isEmpty()) return null
        val newest = state.queue.lastOrNull()?.addedAtMs ?: 0L
        val transition = when {
            peeks -> FaceTransition.PEEK
            faces.all { it is TileFace.Photo } -> FaceTransition.CROSSFADE
            else -> FaceTransition.FLIP
        }
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
            var ownerChanged = false
            for ((tileId, state) in tilesOf(owner)) {
                val name = if (tileId == null) owner.pkg else "${owner.pkg}/$tileId"
                var changed = false
                val due = state.scheduled.filter { it.deliverAtMs <= nowMs }.sortedBy { it.deliverAtMs }
                if (due.isNotEmpty()) {
                    state.scheduled = state.scheduled - due.toSet()
                    for (s in due) {
                        // Windows: scheduled notifications expire three days after delivery unless the app set a time (R5 §1.4).
                        val expires = s.expiresAtMs ?: (s.deliverAtMs + SCHEDULED_DEFAULT_EXPIRY_MS)
                        val entry = QueueEntry(state.nextSeq++, s.tag, s.xml, s.images, expires, nowMs)
                        val result = TileQueuePolicy.insert(state.queue, entry, state.queueEnabled) { it.tag }
                        state.queue = result.queue
                        deleteImages(owner.pkg, result.evicted.flatMap { it.images })
                        Diagnostics.add("livetile", "deliver $name scheduled id=${s.id} due=${s.deliverAtMs} late=${nowMs - s.deliverAtMs}ms ($why)")
                    }
                    changed = true
                }
                val pruned = TileQueuePolicy.prune(state.queue, nowMs) { it.expiresAtMs }
                if (pruned.expired.isNotEmpty()) {
                    state.queue = pruned.kept
                    deleteImages(owner.pkg, pruned.expired.flatMap { it.images })
                    Diagnostics.add("livetile", "expire $name ${pruned.expired.size} queued (${pruned.expired.map { it.seq }}) ($why)")
                    changed = true
                }
                val badgeExpiry = state.badgeExpiresAtMs
                if (state.badge != null && badgeExpiry != null && badgeExpiry <= nowMs) {
                    state.badge = null
                    state.badgeExpiresAtMs = null
                    Diagnostics.add("livetile", "expire $name api badge ($why)")
                    changed = true
                }
                if (changed) {
                    republishTileLocked(owner.pkg, tileId)
                    ownerChanged = true
                }
            }
            if (ownerChanged) persistLocked(owner)
        }
        rescheduleLocked()
    }

    /** The owner's tiles as (tileId, state) pairs; the primary tile's id is null. */
    private fun tilesOf(owner: Owner): List<Pair<String?, TileState>> =
        listOf<Pair<String?, TileState>>(null to owner.primary) + owner.secondaries.map { (id, s) -> id to s.state }

    private fun rescheduleLocked() {
        val next = owners.values.flatMap { o ->
            o.states().flatMap { s ->
                s.queue.mapNotNull { it.expiresAtMs } + s.scheduled.map { it.deliverAtMs } + listOfNotNull(s.badgeExpiresAtMs.takeIf { s.badge != null })
            }
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
            .put("updatedAt", owner.updatedAtMs)
        stateJson(owner.primary, json)
        json.put("secondaries", JSONArray().apply {
            owner.secondaries.values.forEach { s ->
                val o = JSONObject()
                    .put("tileId", s.record.tileId)
                    .put("displayName", s.record.displayName)
                    .put("arguments", s.record.arguments)
                    .put("size", s.record.size.name)
                    .put("showName", s.record.showName)
                s.record.logo?.let { o.put("logo", imageJson(it)) }
                stateJson(s.state, o)
                put(o)
            }
        })
        val file = File(dir, STATE_FILE)
        val tmp = File(dir, "$STATE_FILE.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) Diagnostics.add("livetile", "store: rename failed for ${owner.pkg}")
        val referenced = owner.images().map { it.file }.toSet()
        dir.listFiles { f -> f.name.startsWith(IMAGE_PREFIX) && f.name !in referenced && f.lastModified() < System.currentTimeMillis() - ORPHAN_GRACE_MS }
            ?.forEach { it.delete() }
    }

    /** One tile's live state, written flat so a version-1 file (primary only) still reads back unchanged. */
    private fun stateJson(state: TileState, into: JSONObject): JSONObject {
        into.put("queueEnabled", state.queueEnabled)
            .put("nextSeq", state.nextSeq)
            .put("queue", JSONArray().apply {
                state.queue.forEach { e ->
                    put(JSONObject().put("seq", e.seq).put("tag", e.tag ?: JSONObject.NULL).put("xml", e.xml)
                        .put("expiresAt", e.expiresAtMs ?: JSONObject.NULL).put("addedAt", e.addedAtMs).put("images", imagesJson(e.images)))
                }
            })
            .put("scheduled", JSONArray().apply {
                state.scheduled.forEach { s ->
                    put(JSONObject().put("id", s.id).put("deliverAt", s.deliverAtMs).put("tag", s.tag ?: JSONObject.NULL).put("xml", s.xml)
                        .put("expiresAt", s.expiresAtMs ?: JSONObject.NULL).put("images", imagesJson(s.images)))
                }
            })
        state.badge?.let { into.put("badge", it) }
        state.badgeExpiresAtMs?.let { into.put("badgeExpiresAt", it) }
        return into
    }

    private fun imageJson(image: StoredImage) = JSONObject().put("src", image.src).put("file", image.file).put("bytes", image.bytes)

    private fun imagesJson(images: List<StoredImage>) = JSONArray().apply { images.forEach { put(imageJson(it)) } }

    private fun read(dir: File): Owner? {
        val file = File(dir, STATE_FILE)
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val pkg = json.getString("pkg")
        if (pkg != dir.name) return null
        val owner = Owner(pkg, Identity(json.getString("signer"), json.getLong("firstInstall")))
        owner.updatedAtMs = json.optLong("updatedAt", 0L)
        readState(json, owner.primary)
        val secondaries = json.optJSONArray("secondaries") ?: JSONArray()
        for (o in secondaries.objects()) {
            val tileId = o.getString("tileId")
            // A restored or hand-edited file cannot introduce a tile id the API itself would refuse (adversarial review F8).
            if (!LiveTileProtocol.isValidTileId(tileId)) {
                Diagnostics.add("livetile", "stored secondary tile id '$tileId' for $pkg is not one this shell wrote; dropped")
                continue
            }
            val record = SecondaryRecord(
                owner = pkg,
                tileId = tileId,
                displayName = o.getString("displayName"),
                arguments = o.optString("arguments", ""),
                logo = o.optJSONObject("logo")?.let { image(it) },
                size = runCatching { TileSize.valueOf(o.getString("size")) }.getOrDefault(TileSize.MEDIUM),
                showName = o.optBoolean("showName", false),
            )
            val secondary = Secondary(record)
            readState(o, secondary.state)
            owner.secondaries[tileId] = secondary
        }
        return owner
    }

    private fun readState(json: JSONObject, state: TileState) {
        state.queueEnabled = json.optBoolean("queueEnabled", false)
        state.nextSeq = json.optLong("nextSeq", 1L)
        state.queue = (json.optJSONArray("queue") ?: JSONArray()).objects().map { o ->
            QueueEntry(o.getLong("seq"), o.optStringOrNull("tag"), o.getString("xml"), images(o), o.optLongOrNull("expiresAt"), o.getLong("addedAt"))
        }
        state.scheduled = (json.optJSONArray("scheduled") ?: JSONArray()).objects().map { o ->
            ScheduledEntry(o.getString("id"), o.getLong("deliverAt"), o.optStringOrNull("tag"), o.getString("xml"), images(o), o.optLongOrNull("expiresAt"))
        }
        if (json.has("badge")) state.badge = json.getInt("badge")
        state.badgeExpiresAtMs = json.optLongOrNull("badgeExpiresAt")
    }

    /**
     * A stored file name must be one the shell itself wrote (IMAGE_PREFIX + a UUID, no path separators): the state
     * file is in the app's data directory, which a device backup can restore, and the name is used both to read and
     * to delete files under the owner's directory (adversarial review F8).
     */
    private fun images(o: JSONObject) = (o.optJSONArray("images") ?: JSONArray()).objects().mapNotNull { image(it) }

    private fun image(o: JSONObject): StoredImage? {
        val file = o.getString("file")
        return if (!IMAGE_FILE.matches(file)) {
            Diagnostics.add("livetile", "stored image name $file is not one this shell wrote; dropped")
            null
        } else {
            StoredImage(o.getString("src"), file, o.getLong("bytes"))
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optStringOrNull(k: String) = if (isNull(k)) null else optString(k)
    private fun JSONObject.optLongOrNull(k: String) = if (isNull(k)) null else optLong(k)

    /**
     * One shared change URI for every owner. A per-package URI on an exported provider with no read permission let any
     * app observe when a particular app updated its tile - when a messaging app got a message, say (adversarial review
     * F7). An observer now learns only that some tile changed, and an app still hears about changes the shell makes to
     * its own tile (expiry, queue eviction, the kill switch). A secondary tile changing notifies the same URI, so a
     * pin, an unpin or a per-tile update tells no one which app or which tile it was.
     */
    fun changeUri(pkg: String): Uri = Uri.parse("content://${LiveTileProtocol.AUTHORITY}/tiles")

    companion object {
        const val MAX_SCHEDULED = 32
        const val MAX_BADGE = 999
        const val MAX_FACES = 9
        /** Secondary tiles one app may have pinned at once (Windows has no documented cap; the store needs one). */
        const val MAX_SECONDARY_TILES = 16
        /** The shell's own image file names: the prefix plus a UUID, nothing else. */
        private val IMAGE_FILE = Regex("^" + Regex.escape(IMAGE_PREFIX) + "[0-9a-fA-F-]{36}$")
        const val MAX_OWNER_IMAGE_BYTES = 16L * 1024 * 1024
        const val SCHEDULED_DEFAULT_EXPIRY_MS = 3L * 24 * 60 * 60 * 1000
        const val IMAGE_PREFIX = "img-"
        private const val STATE_FILE = "state.json"
        private const val ORPHAN_GRACE_MS = 10 * 60 * 1000L

        /**
         * A secondary tile's key in [LiveTileEngine] and [BadgeStore]. Both are keyed by package name today, and a
         * package name can hold no '#', so these can never collide with an app's own primary tile.
         */
        fun secondaryContentKey(owner: String, tileId: String): String = "${LiveTileEngine.packageKey(owner)}#$tileId"
        fun secondaryBadgeKey(owner: String, tileId: String): String = "$owner#$tileId"

        @Volatile private var instance: LiveTileStore? = null

        fun get(context: Context): LiveTileStore =
            instance ?: synchronized(this) { instance ?: LiveTileStore(context.applicationContext ?: context).also { instance = it } }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
