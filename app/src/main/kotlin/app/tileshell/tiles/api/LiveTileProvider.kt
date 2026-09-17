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
 * receiver, package-change listening and the Samsung badge reader (providers are created at every process start).
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
            if (extras?.getString(LiveTileProtocol.EXTRA_TILE_ID) != null) {
                return reject(pkg, uid, method, Error.SECONDARY_UNSUPPORTED, "secondary tiles are not available in this version")
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
                LiveTileProtocol.TILE_UPDATE -> tileUpdate(ctx, store, pkg, uid, args)
                LiveTileProtocol.TILE_CLEAR -> outcome(pkg, uid, method, store.clear(pkg, System.currentTimeMillis()), "queue cleared", store)
                LiveTileProtocol.TILE_ENABLE_QUEUE -> enableQueue(store, pkg, uid, args)
                LiveTileProtocol.TILE_SCHEDULE -> schedule(ctx, store, pkg, uid, args)
                LiveTileProtocol.BADGE_UPDATE -> badgeUpdate(store, pkg, uid, args)
                LiveTileProtocol.BADGE_CLEAR -> outcome(pkg, uid, method, store.setBadge(pkg, 0, null, System.currentTimeMillis()), "badge cleared", store)
                LiveTileProtocol.TILE_SETTING -> tileSetting(ctx, pkg, uid, disabled)
                else -> reject(pkg, uid, method, Error.UNKNOWN_VERB, method)
            }
        } catch (e: RuntimeException) {
            return reject(pkg, uid, method, Error.INTERNAL, "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun tileUpdate(ctx: Context, store: LiveTileStore, pkg: String, uid: Int, args: Bundle): Bundle {
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
        val stored = store.update(pkg, xml, images, tag, expiresAtMs, now)
        if (stored is LiveTileStore.Outcome.Rejected) images.forEach { store.ownerDir(pkg).resolve(it.file).delete() }
        val bindings = payload.bindings.joinToString(",") { it.template.xmlName }
        return outcome(pkg, uid, method, stored, "bindings=$bindings images=${images.size} tag=$tag expiresAt=$expiresAtMs xmlBytes=${xml.length}", store)
    }

    private fun enableQueue(store: LiveTileStore, pkg: String, uid: Int, args: Bundle): Bundle {
        val method = LiveTileProtocol.TILE_ENABLE_QUEUE
        @Suppress("DEPRECATION")
        val enabled = args.get(LiveTileProtocol.EXTRA_ENABLED) as? Boolean
            ?: return reject(pkg, uid, method, Error.ENABLED, "enabled must be a boolean")
        return outcome(pkg, uid, method, store.enableQueue(pkg, enabled, System.currentTimeMillis()), "queue enabled=$enabled", store)
    }

    private fun schedule(ctx: Context, store: LiveTileStore, pkg: String, uid: Int, args: Bundle): Bundle {
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
        val stored = store.schedule(pkg, entry, now)
        if (stored is LiveTileStore.Outcome.Rejected) images.forEach { store.ownerDir(pkg).resolve(it.file).delete() }
        return outcome(pkg, uid, method, stored, "id=$id deliveryAt=$deliverAtMs in=${deliverAtMs - now}ms images=${images.size}", store)
    }

    private fun badgeUpdate(store: LiveTileStore, pkg: String, uid: Int, args: Bundle): Bundle {
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
        return outcome(pkg, uid, method, store.setBadge(pkg, count, expiresAtMs, System.currentTimeMillis()), "badge=$count expiresAt=$expiresAtMs", store)
    }

    private fun tileSetting(ctx: Context, pkg: String, uid: Int, disabled: Boolean): Bundle {
        val setting = when {
            disabled -> LiveTileProtocol.SETTING_DISABLED_FOR_APPLICATION
            isPinned(ctx, pkg) -> LiveTileProtocol.SETTING_ENABLED
            else -> LiveTileProtocol.SETTING_NOT_PINNED
        }
        Diagnostics.add("livetile", "accept $pkg uid=$uid ${LiveTileProtocol.TILE_SETTING} -> $setting")
        return result(true, null, null).apply { putString(LiveTileProtocol.RESULT_SETTING, setting) }
    }

    private fun isPinned(ctx: Context, pkg: String): Boolean = runCatching {
        val layout = LayoutStore.get(ctx).layout.value
        val resolver by lazy { SlotResolver(ctx, AppCatalog.get(ctx)) }
        layout.placements.any { p ->
            when (val key = p.key) {
                is TileKey.AppTile -> key.component.packageName == pkg
                is TileKey.SlotTile -> resolver.resolve(key.slot, layout.explicitSlots)?.component?.packageName == pkg
                is TileKey.ShellTile -> false
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
