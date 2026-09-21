package app.tileshell.tiles.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Bundle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.apps.AppCatalog
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.TileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Secondary tiles on Start (R5 §1.9 / §4b, phase 02 build task 6): the one seam between the Live Tile API and the
 * Start UI. Start never talks to [LiveTileStore] about secondary tiles; it reads [pending] to know whether to draw
 * the pin confirmation, [tiles] / [info] to draw a pinned secondary tile, [contentKey] / [badgeKey] to find that
 * tile's live faces and badge in the engine, and [launch] for a tap.
 *
 * What is NOT here on purpose: pinning itself. Accepting a request calls
 * `LayoutStore.pin(TileKey.SecondaryTile(owner, tileId), size)` — the same store every other pin goes through.
 */
object SecondaryTiles {

    /** A pin request waiting for the user (nothing is pinned until they say Pin). */
    data class PendingRequest(
        val owner: String,
        /** The requesting app's own label, as the confirmation band shows it ("<app> would like to pin…"). */
        val ownerLabel: String,
        val tileId: String,
        val displayName: String,
        val size: TileSize,
        val showName: Boolean,
        val logoFile: File?,
        val requestedAtMs: Long,
    ) {
        val key: TileKey.SecondaryTile get() = TileKey.SecondaryTile(owner, tileId)
    }

    /** What Start needs to draw one pinned secondary tile. */
    data class SecondaryTileInfo(
        val owner: String,
        val tileId: String,
        val displayName: String,
        val arguments: String,
        /** The size the OWNER asked to be pinned at; once it is on Start the layout owns the size. */
        val size: TileSize,
        val showName: Boolean,
        val logoFile: File?,
    ) {
        val key: TileKey.SecondaryTile get() = TileKey.SecondaryTile(owner, tileId)
        val contentKey: String get() = LiveTileStore.secondaryContentKey(owner, tileId)
        val badgeKey: String get() = LiveTileStore.secondaryBadgeKey(owner, tileId)
    }

    sealed interface RequestResult {
        data class Queued(val waiting: Int) : RequestResult
        data object Full : RequestResult
    }

    private val queue = SecondaryPinQueue<PendingRequest>({ it.key.id })
    private val pendingState = MutableStateFlow<PendingRequest?>(null)
    private val tilesState = MutableStateFlow<Map<String, SecondaryTileInfo>>(emptyMap())
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logoCache = HashMap<String, ImageBitmap?>()

    @Volatile private var appContext: Context? = null

    /** The request the confirmation band should be showing, or null. */
    val pending: StateFlow<PendingRequest?> = pendingState.asStateFlow()

    /** Every pinned secondary tile, keyed by `TileKey.SecondaryTile(owner, tileId).id`. */
    val tiles: StateFlow<Map<String, SecondaryTileInfo>> = tilesState.asStateFlow()

    /** Called once per process from [LiveTileProvider.onCreate]. */
    fun start(context: Context) {
        val app = context.applicationContext ?: context
        appContext = app
        if (!started.compareAndSet(false, true)) return
        val store = LiveTileStore.get(app)
        scope.launch {
            store.secondaries.collect { byOwner ->
                tilesState.value = byOwner.values.flatten().associate { r ->
                    TileKey.SecondaryTile(r.owner, r.tileId).id to SecondaryTileInfo(
                        owner = r.owner,
                        tileId = r.tileId,
                        displayName = r.displayName,
                        arguments = r.arguments,
                        size = r.size,
                        showName = r.showName,
                        logoFile = r.logo?.let { store.ownerDir(r.owner).resolve(it.file) },
                    )
                }
            }
        }
        // A secondary tile exists for its owner exactly as long as it is on Start: unpinning one (by hand, with the
        // folder it was in, or with the app that owned it) drops its stored state too, and secondary.exists then
        // answers false, as Windows does when the user deletes a secondary tile (R5 §1.9).
        scope.launch {
            LayoutStore.get(app).layout.collect {
                store.handler.post { store.retainSecondaries(pinnedByOwner(app)) }
            }
        }
    }

    private fun pinnedByOwner(context: Context): Map<String, Set<String>> =
        LayoutStore.get(context).layout.value.allKeys()
            .filterIsInstance<TileKey.SecondaryTile>()
            .groupBy({ it.owner }, { it.tileId })
            .mapValues { (_, ids) -> ids.toSet() }

    // ---------- the API side ----------

    /**
     * Takes a `secondary.requestCreate` (the provider has already validated it and copied the logo). The request is
     * held until Start is next shown — it is never drawn over the app that asked, and never dropped.
     */
    @Synchronized
    fun request(context: Context, record: LiveTileStore.SecondaryRecord): RequestResult {
        val app = context.applicationContext ?: context
        appContext = app
        val pending = PendingRequest(
            owner = record.owner,
            ownerLabel = appLabel(app, record.owner),
            tileId = record.tileId,
            displayName = record.displayName,
            size = record.size,
            showName = record.showName,
            logoFile = record.logo?.let { LiveTileStore.get(app).ownerDir(record.owner).resolve(it.file) },
            requestedAtMs = System.currentTimeMillis(),
        )
        // The record itself is only written when the user accepts; until then nothing about the tile is stored.
        pendingRecords[pending.key.id] = record
        return when (val offer = queue.offer(pending)) {
            is SecondaryPinQueue.Offer.Queued -> {
                publish(offer.presentable)
                RequestResult.Queued(queue.waiting().size)
            }
            SecondaryPinQueue.Offer.Full -> {
                pendingRecords.remove(pending.key.id)
                RequestResult.Full
            }
        }
    }

    /**
     * `secondary.requestDelete`: the tile leaves Start and its stored state goes with it. Done on the calling thread
     * (the verb's binder thread), so `secondary.exists` is already false when the call returns.
     */
    fun delete(context: Context, owner: String, tileId: String) {
        val app = context.applicationContext ?: context
        LayoutStore.get(app).unpin(TileKey.SecondaryTile(owner, tileId))
        LiveTileStore.get(app).deleteSecondary(owner, tileId, System.currentTimeMillis())
        dropRequest(TileKey.SecondaryTile(owner, tileId).id)
    }

    /**
     * The owner was uninstalled: its secondary tiles leave Start, wherever they are (the grid, the bottom row or
     * inside a folder), and a pin request it was still waiting on is dropped. Windows: secondary tiles "are
     * automatically deleted when the app is uninstalled" (R5 §1.9); [LiveTileStore.onPackageRemoved] drops the stored
     * side. Phase 02 build task 5 owns package handling for app tiles in general; this is only the secondary ones,
     * and `LayoutStore.onPackagesRemoved` doing the same thing later is harmless.
     */
    fun onOwnerRemoved(context: Context, owner: String) {
        val app = context.applicationContext ?: context
        LiveTileStore.get(app).handler.post {
            val layout = LayoutStore.get(app)
            val gone = layout.layout.value.allKeys().filterIsInstance<TileKey.SecondaryTile>().filter { it.owner == owner }
            gone.forEach { layout.unpin(it) }
            if (gone.isNotEmpty()) Diagnostics.add("livetile", "owner $owner removed: ${gone.size} secondary tile(s) unpinned ${gone.map { it.tileId }}")
        }
        val (dropped, next) = queue.removeIf { it.owner == owner }
        dropped.forEach { pendingRecords.remove(it.key.id) }
        if (dropped.isNotEmpty()) Diagnostics.add("livetile", "owner $owner removed: ${dropped.size} pin request(s) dropped")
        publish(next)
    }

    /**
     * The owner was added or replaced: a secondary tile still on Start whose stored record is gone (the install
     * identity changed, so the store wiped it) is unpinned, because it is not the same app's tile any more.
     */
    fun onOwnerChanged(context: Context, owner: String) {
        val app = context.applicationContext ?: context
        val store = LiveTileStore.get(app)
        store.handler.post {
            val layout = LayoutStore.get(app)
            val stale = layout.layout.value.allKeys().filterIsInstance<TileKey.SecondaryTile>()
                .filter { it.owner == owner && store.secondary(owner, it.tileId) == null }
            stale.forEach { layout.unpin(it) }
            if (stale.isNotEmpty()) Diagnostics.add("livetile", "owner $owner changed: ${stale.size} secondary tile(s) with no stored record unpinned")
        }
    }

    /** The records of requests waiting for the user, by tile key id (written from binder threads and the UI thread). */
    private val pendingRecords = java.util.concurrent.ConcurrentHashMap<String, LiveTileStore.SecondaryRecord>()

    // ---------- the Start side (the seam the shell calls) ----------

    /** Start became visible (onResume) or left the screen (onPause / onStop). */
    @Synchronized
    fun setStartVisible(visible: Boolean) {
        publish(queue.onStartVisible(visible))
    }

    /** The user tapped Pin: the tile is stored and pinned at the size the app asked for. */
    @Synchronized
    fun accept() {
        val request = pendingState.value ?: return
        val app = appContext ?: return
        val record = pendingRecords.remove(request.key.id)
        publish(queue.remove(request.key.id))
        if (record == null) {
            Diagnostics.add("livetile", "pin request ${request.owner}/${request.tileId} accepted but its record is gone; nothing pinned")
            return
        }
        val store = LiveTileStore.get(app)
        store.handler.post {
            when (val stored = store.putSecondary(record, System.currentTimeMillis())) {
                is LiveTileStore.Outcome.Rejected -> {
                    record.logo?.let { store.ownerDir(record.owner).resolve(it.file).delete() }
                    Diagnostics.add("livetile", "pin ${record.owner}/${record.tileId} refused by the store: ${stored.reason} ${stored.detail}")
                }
                LiveTileStore.Outcome.Ok -> {
                    val pinned = LayoutStore.get(app).pin(request.key, request.size)
                    Diagnostics.add("livetile", "pin ${record.owner}/${record.tileId} accepted by the user: size=${request.size} pinned=$pinned")
                    app.contentResolver.notifyChange(store.changeUri(record.owner), null)
                }
            }
        }
    }

    /** The user tapped Cancel: nothing is pinned and nothing is kept (phase 02 Decisions). */
    @Synchronized
    fun decline() {
        val request = pendingState.value ?: return
        Diagnostics.add("livetile", "pin ${request.owner}/${request.tileId} declined by the user: nothing pinned")
        dropRequest(request.key.id)
    }

    @Synchronized
    private fun dropRequest(keyId: String) {
        val record = pendingRecords.remove(keyId)
        val app = appContext
        if (record != null && app != null) {
            // The logo was copied into the shell's storage for the preview; a request that dies takes it with it.
            record.logo?.let { LiveTileStore.get(app).ownerDir(record.owner).resolve(it.file).delete() }
        }
        publish(queue.remove(keyId))
    }

    /**
     * Publishes what the band should show. A request whose owner has been uninstalled (or reinstalled under another
     * identity) is dropped here rather than shown: the check happens at every presentation decision, so a held
     * request can never outlive its app.
     */
    @Synchronized
    private fun publish(next: PendingRequest?) {
        var candidate = next
        val app = appContext
        while (candidate != null && app != null && !isInstalled(app, candidate.owner)) {
            Diagnostics.add("livetile", "pin request ${candidate.owner}/${candidate.tileId} dropped: the app is no longer installed")
            pendingRecords.remove(candidate.key.id)
            candidate = queue.remove(candidate.key.id)
        }
        pendingState.value = candidate
    }

    /** What Start needs to draw the pinned tile (owner, tileId), or null when it is not pinned. */
    fun info(context: Context, owner: String, tileId: String): SecondaryTileInfo? {
        if (appContext == null) start(context)
        return tilesState.value[TileKey.SecondaryTile(owner, tileId).id]
    }

    /** The live-tile engine key a secondary tile's faces are published under. */
    fun contentKey(owner: String, tileId: String): String = LiveTileStore.secondaryContentKey(owner, tileId)

    /** The [app.tileshell.tiles.engine.BadgeStore] key a secondary tile's badge is published under. */
    fun badgeKey(owner: String, tileId: String): String = LiveTileStore.secondaryBadgeKey(owner, tileId)

    /** The tile's logo, decoded and cached at [sizePx]; null when the owner gave none (draw the app's own icon). */
    fun logo(info: SecondaryTileInfo, sizePx: Int): ImageBitmap? = synchronized(logoCache) {
        val file = info.logoFile ?: return null
        if (!file.exists()) return null
        logoCache.getOrPut("${file.path}@${file.lastModified()}@$sizePx") {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@getOrPut null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxOf(sizePx, 1) * 2) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }

    /**
     * A tap on a secondary tile starts the OWNER'S CATEGORY_LAUNCHER activity with the tile's identity and arguments
     * (R5 §4b "Launch arguments"): `<app>.extra.TILE_ID`, `<app>.extra.ARGUMENTS` and `<app>.extra.TILE_ACTIVATED_ARGS`
     * (the `arguments` of the notifications on the tile right now — Windows' chaseable tiles). The API never accepts
     * an intent from an app, so this is built here from the resolved launcher component and nothing else.
     *
     * Returns false when the tile or the owner's launcher activity is gone; the caller logs and does nothing.
     */
    fun launch(context: Context, owner: String, tileId: String, sourceBounds: Rect? = null, options: Bundle? = null): Boolean {
        val info = info(context, owner, tileId) ?: run {
            Diagnostics.add("launch", "secondary tile $owner/$tileId is not pinned")
            return false
        }
        val component: ComponentName = AppCatalog.get(context).firstForPackage(owner)?.component ?: run {
            Diagnostics.add("launch", "secondary tile $owner/$tileId: the owner has no launcher activity")
            return false
        }
        val activated = LiveTileStore.get(context).activatedArgs(owner, tileId)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            .putExtra(LiveTileProtocol.EXTRA_LAUNCH_TILE_ID, tileId)
            .putExtra(LiveTileProtocol.EXTRA_LAUNCH_ARGUMENTS, info.arguments)
            .putExtra(LiveTileProtocol.EXTRA_LAUNCH_ACTIVATED_ARGS, activated.toTypedArray())
        intent.sourceBounds = sourceBounds
        return runCatching {
            context.startActivity(intent, options)
            Diagnostics.add("launch", "secondary tile $owner/$tileId -> ${component.flattenToShortString()} args=${info.arguments.length} chars activated=${activated.size}")
            true
        }.getOrElse {
            Diagnostics.add("launch", "secondary tile $owner/$tileId could not be started: $it")
            false
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pkg, 0)
    }.isSuccess

    private fun appLabel(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
