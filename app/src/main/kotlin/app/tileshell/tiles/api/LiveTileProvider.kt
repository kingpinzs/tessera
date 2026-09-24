package app.tileshell.tiles.api

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.SystemClock
import app.tileshell.apps.AppCatalog
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.SlotResolver
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.api.LiveTileProtocol.Error

/**
 * The public Live Tile API (R5 §4b, phase 01 Decisions): one exported provider, authority app.tileshell.livetile,
 * no read/write permission; every verb authorises on Binder identity in code.
 *
 * Trust rules, in order, for every call:
 * 1. the shell uid (2000, adb) and root are refused: error "identity";
 * 2. the caller is getCallingPackage(), which the framework verifies belongs to the calling uid;
 * 3. a uid shared by more than one installed package is refused: error "shared-uid";
 * 4. an `owner` extra naming anyone but the caller is refused: error "identity";
 * 5. 60 verbs per minute per caller, excess: error "rate";
 * 6. stored state from an earlier install or another signer of the same package name is wiped first;
 * 7. the per-app kill switch refuses writes: error "disabled" (tile.setting answers DISABLED_FOR_APPLICATION).
 * Every decision is written to the diagnostics ring buffer under tag "livetile".
 *
 * onCreate is the shell process's start hook for the API's companions: the store load, the runtime legacy badge
 * receiver, package-change listening, the Samsung badge reader and the secondary-tile holder (providers are created
 * at every process start).
 *
 * Secondary tiles (phase 02 build task 6): every verb may carry a `tileId` naming one of the CALLER'S OWN secondary
 * tiles - rule 4 above already refuses an `owner` that is not the caller, so a tileId can never reach another app's
 * tile - and the five secondary.* verbs create, update, delete and list them. Creating one is a request: the user
 * confirms it on Start (SecondaryTiles / SecondaryPinPrompt) and nothing is pinned until they do.
 */
class LiveTileProvider : ContentProvider() {
    private val limiter = RateLimiter(limit = 60, windowMs = 60_000L)
    private val rateLog = HashMap<String, Pair<Long, Int>>() // pkg -> (last logged at, suppressed since)

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        val app = ctx.applicationContext ?: ctx
        val store = LiveTileStore.get(app)
        store.start()
        LegacyBadgeReceiver.registerRuntime(app)
        LiveTileSystemReceiver.registerRuntime(app)
        SecondaryTiles.start(app)
        store.handler.post { SamsungBadgeReader.start(app, store.handler) }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return result(false, Error.INTERNAL, "no context")
        val uid = Binder.getCallingUid()
        val appId = uid % PER_USER_RANGE
        if (appId == ROOT_APP_ID || appId == SHELL_APP_ID) {
            return reject(null, uid, method, Error.IDENTITY, if (appId == ROOT_APP_ID) "root uid" else "adb shell uid")
        }
        val pkg = try {
            callingPackage
        } catch (e: SecurityException) {
            return reject(null, uid, method, Error.IDENTITY, "calling package does not belong to uid: ${e.message}")
        } ?: return reject(null, uid, method, Error.IDENTITY, "no calling package")

        val token = Binder.clearCallingIdentity()
        try {
            val uidPackages = ctx.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
            if (pkg !in uidPackages) return reject(pkg, uid, method, Error.IDENTITY, "package not in uid's packages $uidPackages")
            if (uidPackages.size > 1) return reject(pkg, uid, method, Error.SHARED_UID, "uid $uid shared by ${uidPackages.sorted()}")
            val owner = extras?.getString(LiveTileProtocol.EXTRA_OWNER)
            if (owner != null && owner != pkg) return reject(pkg, uid, method, Error.IDENTITY, "owner $owner is not the caller")
            if (!limiter.tryAcquire(pkg, SystemClock.elapsedRealtime())) return rateReject(pkg, uid, method)
            if (method !in LiveTileProtocol.VERBS) return reject(pkg, uid, method, Error.UNKNOWN_VERB, "verb not in protocol v${LiveTileProtocol.VERSION}")
            // A tileId addresses one of the CALLER'S OWN secondary tiles: the owner is always getCallingPackage(), so
            // there is no id an app can write here that reaches another app's tile (R5 §4b identity rule).
            val tileId = extras?.getString(LiveTileProtocol.EXTRA_TILE_ID)
            if (tileId != null && !LiveTileProtocol.isValidTileId(tileId)) {
                return reject(pkg, uid, method, Error.TILE_ID, "tileId must be 1-64 characters of letters, digits, '.', '_' or '-', and not only dots")
            }
            val store = LiveTileStore.get(ctx)
            store.checkIdentity(pkg, "call $method")
            val disabled = LiveTileSettings.isDisabled(ctx, pkg) || !LiveTileSettings.isApiEnabled(ctx)
            if (disabled && method != LiveTileProtocol.TILE_SETTING) {
                val why = if (LiveTileSettings.isApiEnabled(ctx)) "user turned this app's live tile off" else "the user turned live tiles off for every app"
                return reject(pkg, uid, method, Error.DISABLED, why)
            }
            val args = extras ?: Bundle.EMPTY
            return when (method) {
                LiveTileProtocol.TILE_UPDATE -> tileUpdate(ctx, store, pkg, uid, tileId, args)
                LiveTileProtocol.TILE_CLEAR -> outcome(pkg, uid, method, store.clear(pkg, tileId, System.currentTimeMillis()), "queue cleared tileId=$tileId", store)
                LiveTileProtocol.TILE_ENABLE_QUEUE -> enableQueue(store, pkg, uid, tileId, args)
                LiveTileProtocol.TILE_SCHEDULE -> schedule(ctx, store, pkg, uid, tileId, args)
                LiveTileProtocol.BADGE_UPDATE -> badgeUpdate(store, pkg, uid, tileId, args)
                LiveTileProtocol.BADGE_CLEAR -> outcome(pkg, uid, method, store.setBadge(pkg, tileId, 0, null, System.currentTimeMillis()), "badge cleared tileId=$tileId", store)
                LiveTileProtocol.TILE_SETTING -> tileSetting(ctx, store, pkg, uid, tileId, disabled)
                LiveTileProtocol.SECONDARY_REQUEST_CREATE -> secondaryRequestCreate(ctx, store, pkg, uid, args)
                LiveTileProtocol.SECONDARY_UPDATE -> secondaryUpdate(ctx, store, pkg, uid, args)
                LiveTileProtocol.SECONDARY_REQUEST_DELETE -> secondaryRequestDelete(ctx, store, pkg, uid, tileId)
                LiveTileProtocol.SECONDARY_EXISTS -> secondaryExists(store, pkg, uid, tileId)
                LiveTileProtocol.SECONDARY_FIND_ALL -> secondaryFindAll(store, pkg, uid)
                else -> reject(pkg, uid, method, Error.UNKNOWN_VERB, method)
            }
        } catch (e: RuntimeException) {
            return reject(pkg, uid, method, Error.INTERNAL, "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun tileUpdate(ctx: Context, store: LiveTileStore, pkg: String, uid: Int, tileId: String?, args: Bundle): Bundle {
        val method = LiveTileProtocol.TILE_UPDATE
        val now = System.currentTimeMillis()
        val tag = args.getString(LiveTileProtocol.EXTRA_TAG)
        if (tag != null && !LiveTileProtocol.TAG_PATTERN.matches(tag)) return reject(pkg, uid, method, Error.TAG, "tag must be 1-16 alphanumeric characters")
        val expiresAt = longExtra(args, LiveTileProtocol.EXTRA_EXPIRES_AT)
        if (expiresAt is LongExtra.Invalid) return reject(pkg, uid, method, Error.EXPIRES_AT, "expiresAt must be a long (epoch ms)")
        val expiresAtMs = (expiresAt as? LongExtra.Present)?.value
        if (expiresAtMs != null && expiresAtMs <= now) return reject(pkg, uid, method, Error.EXPIRES_AT, "expiresAt $expiresAtMs is not in the future")
        val xml = args.getString(LiveTileProtocol.EXTRA_XML) ?: return reject(pkg, uid, method, TileXmlValidator.Reason.MALFORMED, "missing xml")
        val payload = when (val v = TileXmlValidator.validate(xml)) {
            is TileXmlResult.Invalid -> return reject(pkg, uid, method, v.reason, v.detail)
            is TileXmlResult.Valid -> v.payload
        }
        val images = when (val r = ImageIngest(ctx).ingest(pkg, payload.images(), store.ownerDir(pkg), store.imageBytesUsed(pkg))) {
            is ImageIngest.Result.Rejected -> return reject(pkg, uid, method, r.reason, r.detail)
            is ImageIngest.Result.Ok -> r.images
        }
        val stored = store.update(pkg, tileId, xml, images, tag, expiresAtMs, now)
        if (stored is LiveTileStore.Outcome.Rejected) images.forEach { store.ownerDir(pkg).resolve(it.file).delete() }
        val bindings = payload.bindings.joinToString(",") { it.template.xmlName }
        return outcome(pkg, uid, method, stored, "tileId=$tileId bindings=$bindings images=${images.size} tag=$tag expiresAt=$expiresAtMs xmlBytes=${xml.length}", store)
    }

    private fun enableQueue(store: LiveTileStore, pkg: String, uid: Int, tileId: String?, args: Bundle): Bundle {
        val method = LiveTileProtocol.TILE_ENABLE_QUEUE
        @Suppress("DEPRECATION")
        val enabled = args.get(LiveTileProtocol.EXTRA_ENABLED) as? Boolean
            ?: return reject(pkg, uid, method, Error.ENABLED, "enabled must be a boolean")
        return outcome(pkg, uid, method, store.enableQueue(pkg, tileId, enabled, System.currentTimeMillis()), "tileId=$tileId queue enabled=$enabled", store)
    }

    private fun schedule(ctx: Context, store: LiveTileStore, pkg: String, uid: Int, tileId: String?, args: Bundle): Bundle {
        val method = LiveTileProtocol.TILE_SCHEDULE
        val now = System.currentTimeMillis()
        val id = args.getString(LiveTileProtocol.EXTRA_ID)
        if (id == null || !LiveTileProtocol.TAG_PATTERN.matches(id)) return reject(pkg, uid, method, Error.ID, "id must be 1-16 alphanumeric characters")
        val tag = args.getString(LiveTileProtocol.EXTRA_TAG)
        if (tag != null && !LiveTileProtocol.TAG_PATTERN.matches(tag)) return reject(pkg, uid, method, Error.TAG, "tag must be 1-16 alphanumeric characters")
        val deliverAtMs = (longExtra(args, LiveTileProtocol.EXTRA_DELIVERY_AT) as? LongExtra.Present)?.value
            ?: return reject(pkg, uid, method, Error.DELIVERY_AT, "deliveryAt (long, epoch ms) is required")
        if (deliverAtMs <= now) return reject(pkg, uid, method, Error.DELIVERY_AT, "deliveryAt $deliverAtMs is not in the future")
        val expiresAt = longExtra(args, LiveTileProtocol.EXTRA_EXPIRES_AT)
        if (expiresAt is LongExtra.Invalid) return reject(pkg, uid, method, Error.EXPIRES_AT, "expiresAt must be a long (epoch ms)")
        val expiresAtMs = (expiresAt as? LongExtra.Present)?.value
        if (expiresAtMs != null && expiresAtMs <= deliverAtMs) return reject(pkg, uid, method, Error.EXPIRES_AT, "expiresAt must be after deliveryAt")
        val xml = args.getString(LiveTileProtocol.EXTRA_XML) ?: return reject(pkg, uid, method, TileXmlValidator.Reason.MALFORMED, "missing xml")
        val payload = when (val v = TileXmlValidator.validate(xml)) {
            is TileXmlResult.Invalid -> return reject(pkg, uid, method, v.reason, v.detail)
            is TileXmlResult.Valid -> v.payload
        }
        val images = when (val r = ImageIngest(ctx).ingest(pkg, payload.images(), store.ownerDir(pkg), store.imageBytesUsed(pkg))) {
            is ImageIngest.Result.Rejected -> return reject(pkg, uid, method, r.reason, r.detail)
            is ImageIngest.Result.Ok -> r.images
        }
        val entry = LiveTileStore.ScheduledEntry(id, deliverAtMs, tag, xml, images, expiresAtMs)
        val stored = store.schedule(pkg, tileId, entry, now)
        if (stored is LiveTileStore.Outcome.Rejected) images.forEach { store.ownerDir(pkg).resolve(it.file).delete() }
        return outcome(pkg, uid, method, stored, "tileId=$tileId id=$id deliveryAt=$deliverAtMs in=${deliverAtMs - now}ms images=${images.size}", store)
    }

    private fun badgeUpdate(store: LiveTileStore, pkg: String, uid: Int, tileId: String?, args: Bundle): Bundle {
        val method = LiveTileProtocol.BADGE_UPDATE
        val expiresAt = longExtra(args, LiveTileProtocol.EXTRA_EXPIRES_AT)
        if (expiresAt is LongExtra.Invalid) return reject(pkg, uid, method, Error.EXPIRES_AT, "expiresAt must be a long (epoch ms)")
        val expiresAtMs = (expiresAt as? LongExtra.Present)?.value
        if (expiresAtMs != null && expiresAtMs <= System.currentTimeMillis()) return reject(pkg, uid, method, Error.EXPIRES_AT, "expiresAt is not in the future")
        @Suppress("DEPRECATION")
        val count: Int = when (val raw = args.get(LiveTileProtocol.EXTRA_VALUE)) {
            is Int -> raw
            is Long -> raw.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            is String -> when {
                raw.toIntOrNull() != null -> raw.toInt()
                raw == "none" -> 0
                raw in LiveTileProtocol.GLYPHS -> return reject(pkg, uid, method, Error.GLYPH_UNSUPPORTED, "glyph '$raw' is not rendered in this version; numbers and 'none' are")
                else -> return reject(pkg, uid, method, Error.VALUE, "value '$raw' is neither a number nor a badge glyph")
            }
            else -> return reject(pkg, uid, method, Error.VALUE, "value must be an int >= 0 or a glyph name")
        }
        if (count < 0) return reject(pkg, uid, method, Error.VALUE, "value $count < 0")
        return outcome(pkg, uid, method, store.setBadge(pkg, tileId, count, expiresAtMs, System.currentTimeMillis()), "tileId=$tileId badge=$count expiresAt=$expiresAtMs", store)
    }

    private fun tileSetting(ctx: Context, store: LiveTileStore, pkg: String, uid: Int, tileId: String?, disabled: Boolean): Bundle {
        val pinned = if (tileId == null) isPinned(ctx, pkg) else store.secondary(pkg, tileId) != null
        val setting = when {
            disabled -> LiveTileProtocol.SETTING_DISABLED_FOR_APPLICATION
            pinned -> LiveTileProtocol.SETTING_ENABLED
            else -> LiveTileProtocol.SETTING_NOT_PINNED
        }
        Diagnostics.add("livetile", "accept $pkg uid=$uid ${LiveTileProtocol.TILE_SETTING} tileId=$tileId -> $setting")
        return result(true, null, null).apply { putString(LiveTileProtocol.RESULT_SETTING, setting) }
    }

    // ---------- secondary tiles (R5 §1.9 / §4b, phase 02 build task 6) ----------

    /**
     * Windows' `RequestCreateAsync`: the app asks, the user confirms. The confirmation is a band on Start
     * ([app.tileshell.start.SecondaryPinPrompt]); this call only takes the request. A tileId the caller already has
     * pinned is UPDATED in place with no confirmation (phase 02 Decisions: "a duplicate tileId updates the existing
     * tile (no second tile)").
     */
    private fun secondaryRequestCreate(ctx: Context, store: LiveTileStore, pkg: String, uid: Int, args: Bundle): Bundle {
        val method = LiveTileProtocol.SECONDARY_REQUEST_CREATE
        val fields = when (val v = validateSecondary(args)) {
            is SecondaryFieldsResult.Invalid -> return reject(pkg, uid, method, v.reason, v.detail)
            is SecondaryFieldsResult.Ok -> v.fields
        }
        if (store.secondary(pkg, fields.tileId) != null) {
            val stored = storeSecondary(ctx, store, pkg, fields)
            if (stored is LiveTileStore.Outcome.Rejected) return reject(pkg, uid, method, stored.reason, stored.detail)
            Diagnostics.add("livetile", "accept $pkg uid=$uid $method tileId=${fields.tileId} -> already pinned, updated in place")
            ctx.contentResolver.notifyChange(store.changeUri(pkg), null)
            return result(true, null, "the tile was already pinned and was updated").apply { putBoolean(LiveTileProtocol.RESULT_PENDING, false) }
        }
        val logo = when (val r = ingestLogo(ctx, store, pkg, fields)) {
            is LogoResult.Rejected -> return reject(pkg, uid, method, r.reason, r.detail)
            is LogoResult.Ok -> r.image
        }
        val record = LiveTileStore.SecondaryRecord(pkg, fields.tileId, fields.displayName, fields.arguments, logo, fields.size, fields.showName)
        return when (val offered = SecondaryTiles.request(ctx, record)) {
            is SecondaryTiles.RequestResult.Queued -> {
                Diagnostics.add(
                    "livetile",
                    "accept $pkg uid=$uid $method tileId=${fields.tileId} name=${fields.displayName} size=${fields.size} showName=${fields.showName} " +
                        "logo=${logo != null} -> waiting for the user (${offered.waiting} request(s) held)",
                )
                result(true, null, "waiting for the user to confirm on Start").apply { putBoolean(LiveTileProtocol.RESULT_PENDING, true) }
            }
            SecondaryTiles.RequestResult.Full -> {
                logo?.let { store.ownerDir(pkg).resolve(it.file).delete() }
                reject(pkg, uid, method, Error.QUOTA, "too many pin requests are waiting for the user")
            }
            // Cannot happen here (the fields passed validateSecondary above); the seam checks them again for its
            // in-process callers (phase 15 T15-40), and a second refusal is still a refusal.
            is SecondaryTiles.RequestResult.Invalid -> {
                logo?.let { store.ownerDir(pkg).resolve(it.file).delete() }
                reject(pkg, uid, method, offered.reason, offered.detail)
            }
        }
    }

    /** Windows' `UpdateAsync`: the tile must exist, and every property is assigned (R5 §1.9). */
    private fun secondaryUpdate(ctx: Context, store: LiveTileStore, pkg: String, uid: Int, args: Bundle): Bundle {
        val method = LiveTileProtocol.SECONDARY_UPDATE
        val fields = when (val v = validateSecondary(args)) {
            is SecondaryFieldsResult.Invalid -> return reject(pkg, uid, method, v.reason, v.detail)
            is SecondaryFieldsResult.Ok -> v.fields
        }
        if (store.secondary(pkg, fields.tileId) == null) {
            return reject(pkg, uid, method, Error.NOT_FOUND, "no secondary tile '${fields.tileId}' is pinned for this app")
        }
        val stored = storeSecondary(ctx, store, pkg, fields)
        return outcome(pkg, uid, method, stored, "tileId=${fields.tileId} name=${fields.displayName} size=${fields.size} showName=${fields.showName}", store)
    }

    /** Windows' `RequestDeleteAsync`: the app removes its own tile; the user needs no dialog to lose a tile. */
    private fun secondaryRequestDelete(ctx: Context, store: LiveTileStore, pkg: String, uid: Int, tileId: String?): Bundle {
        val method = LiveTileProtocol.SECONDARY_REQUEST_DELETE
        if (tileId == null) return reject(pkg, uid, method, Error.TILE_ID, "tileId is required")
        if (store.secondary(pkg, tileId) == null) return reject(pkg, uid, method, Error.NOT_FOUND, "no secondary tile '$tileId' is pinned for this app")
        SecondaryTiles.delete(ctx, pkg, tileId)
        Diagnostics.add("livetile", "accept $pkg uid=$uid $method tileId=$tileId")
        ctx.contentResolver.notifyChange(store.changeUri(pkg), null)
        return result(true, null, null)
    }

    /** Windows' `SecondaryTile.Exists`: only ever about the caller's own tiles. */
    private fun secondaryExists(store: LiveTileStore, pkg: String, uid: Int, tileId: String?): Bundle {
        val method = LiveTileProtocol.SECONDARY_EXISTS
        if (tileId == null) return reject(pkg, uid, method, Error.TILE_ID, "tileId is required")
        val exists = store.secondary(pkg, tileId) != null
        Diagnostics.add("livetile", "accept $pkg uid=$uid $method tileId=$tileId -> $exists")
        return result(true, null, null).apply { putBoolean(LiveTileProtocol.RESULT_EXISTS, exists) }
    }

    /** Windows' `SecondaryTile.FindAllAsync`: the caller's own tile ids, in the order they were pinned. */
    private fun secondaryFindAll(store: LiveTileStore, pkg: String, uid: Int): Bundle {
        val ids = store.secondaryRecords(pkg).map { it.tileId }
        Diagnostics.add("livetile", "accept $pkg uid=$uid ${LiveTileProtocol.SECONDARY_FIND_ALL} -> ${ids.size} tile(s)")
        return result(true, null, null).apply { putStringArray(LiveTileProtocol.RESULT_TILE_IDS, ids.toTypedArray()) }
    }

    @Suppress("DEPRECATION")
    private fun validateSecondary(args: Bundle): SecondaryFieldsResult = SecondaryTileRules.validate(
        tileId = args.get(LiveTileProtocol.EXTRA_TILE_ID),
        displayName = args.get(LiveTileProtocol.EXTRA_DISPLAY_NAME),
        arguments = args.get(LiveTileProtocol.EXTRA_ARGUMENTS),
        logo = args.get(LiveTileProtocol.EXTRA_LOGO),
        size = args.get(LiveTileProtocol.EXTRA_SIZE),
        showName = args.get(LiveTileProtocol.EXTRA_SHOW_NAME),
    )

    private sealed interface LogoResult {
        data class Ok(val image: LiveTileStore.StoredImage?) : LogoResult
        data class Rejected(val reason: String, val detail: String) : LogoResult
    }

    /**
     * The logo is copied into the shell's own storage exactly as a payload image is: the authority must belong to the
     * caller, the read has a deadline and a concurrency cap, and the owner's image quota is checked before anything
     * is written (R5 §4b; adversarial review F2 / F4 / F6).
     */
    private fun ingestLogo(ctx: Context, store: LiveTileStore, pkg: String, fields: SecondaryFields): LogoResult {
        val image = fields.logo ?: return LogoResult.Ok(null)
        return when (val r = ImageIngest(ctx).ingest(pkg, listOf(image), store.ownerDir(pkg), store.imageBytesUsed(pkg))) {
            is ImageIngest.Result.Rejected -> LogoResult.Rejected(r.reason, r.detail)
            is ImageIngest.Result.Ok -> LogoResult.Ok(r.images.firstOrNull())
        }
    }

    /** Ingests the logo (when there is one) and writes the record; used by both update paths. */
    private fun storeSecondary(ctx: Context, store: LiveTileStore, pkg: String, fields: SecondaryFields): LiveTileStore.Outcome {
        val logo = when (val r = ingestLogo(ctx, store, pkg, fields)) {
            is LogoResult.Rejected -> return LiveTileStore.Outcome.Rejected(r.reason, r.detail)
            is LogoResult.Ok -> r.image
        }
        val record = LiveTileStore.SecondaryRecord(pkg, fields.tileId, fields.displayName, fields.arguments, logo, fields.size, fields.showName)
        val stored = store.putSecondary(record, System.currentTimeMillis())
        if (stored is LiveTileStore.Outcome.Rejected && logo != null) store.ownerDir(pkg).resolve(logo.file).delete()
        return stored
    }

    /** Pinned = the package owns any tile the layout holds: the grid, a folder's members or the bottom row (phase 02). */
    private fun isPinned(ctx: Context, pkg: String): Boolean = runCatching {
        val layout = LayoutStore.get(ctx).layout.value
        val resolver by lazy { SlotResolver(ctx, AppCatalog.get(ctx)) }
        layout.allKeys().any { key ->
            when (key) {
                is TileKey.AppTile -> key.component.packageName == pkg
                is TileKey.SecondaryTile -> key.owner == pkg
                is TileKey.SlotTile -> resolver.resolve(key.slot, layout.explicitSlots)?.component?.packageName == pkg
                is TileKey.ShellTile, is TileKey.FolderTile -> false
            }
        }
    }.getOrDefault(false)

    // ---------- results ----------

    private fun outcome(pkg: String, uid: Int, method: String, outcome: LiveTileStore.Outcome, detail: String, store: LiveTileStore): Bundle =
        when (outcome) {
            is LiveTileStore.Outcome.Rejected -> reject(pkg, uid, method, outcome.reason, outcome.detail)
            LiveTileStore.Outcome.Ok -> {
                Diagnostics.add("livetile", "accept $pkg uid=$uid $method $detail")
                context?.contentResolver?.notifyChange(store.changeUri(pkg), null)
                result(true, null, null)
            }
        }

    private fun reject(pkg: String?, uid: Int, method: String, reason: String, detail: String): Bundle {
        Diagnostics.add("livetile", "reject ${pkg ?: "?"} uid=$uid $method reason=$reason: $detail")
        return result(false, reason, detail)
    }

    /** Rate rejections are counted, and logged at most once per window per caller, so a flood cannot evict the ring buffer. */
    private fun rateReject(pkg: String, uid: Int, method: String): Bundle {
        val now = SystemClock.elapsedRealtime()
        val retry = limiter.retryAfterMs(pkg, now)
        synchronized(rateLog) {
            val (lastLogged, suppressed) = rateLog[pkg] ?: (Long.MIN_VALUE to 0)
            if (lastLogged == Long.MIN_VALUE || now - lastLogged >= 60_000L) {
                Diagnostics.add("livetile", "reject $pkg uid=$uid $method reason=${Error.RATE}: over 60 verbs/min, retry in ${retry}ms" +
                    if (suppressed > 0) " ($suppressed earlier rate rejections not logged individually)" else "")
                rateLog[pkg] = now to 0
            } else {
                rateLog[pkg] = lastLogged to suppressed + 1
            }
        }
        return result(false, Error.RATE, "over 60 verbs/min, retry in ${retry}ms")
    }

    private fun result(ok: Boolean, error: String?, detail: String?) = Bundle().apply {
        putBoolean(LiveTileProtocol.RESULT_OK, ok)
        putInt(LiveTileProtocol.EXTRA_VERSION, LiveTileProtocol.VERSION)
        if (error != null) putString(LiveTileProtocol.RESULT_ERROR, error)
        if (detail != null) putString(LiveTileProtocol.RESULT_DETAIL, detail)
    }

    private sealed interface LongExtra {
        data object Absent : LongExtra
        data class Present(val value: Long) : LongExtra
        data object Invalid : LongExtra
    }

    @Suppress("DEPRECATION")
    private fun longExtra(args: Bundle, key: String): LongExtra = when (val raw = args.get(key)) {
        null -> LongExtra.Absent
        is Long -> LongExtra.Present(raw)
        else -> LongExtra.Invalid
    }

    // The API is call()-only; the table methods expose nothing.
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val PER_USER_RANGE = 100_000
        const val ROOT_APP_ID = 0
        const val SHELL_APP_ID = 2000
    }
}
