package app.tileshell.tiles.api

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.engine.BadgeStore

/**
 * Samsung's badge provider `content://com.sec.badge/apps` (R5 §2.4, source S4): columns package, class, badgecount.
 * Read-only: the shell never writes it. Whether a third-party app may read it on One UI 8 is UNVERIFIED (phase 01 P6),
 * so [probe] reports the actual outcome for the onboarding checklist, and counts are ingested only when readable.
 */
object SamsungBadgeReader {
    const val AUTHORITY = "com.sec.badge"
    val URI: Uri = Uri.parse("content://$AUTHORITY/apps")
    private val PROJECTION = arrayOf("package", "class", "badgecount")

    @Volatile var status: String = "not probed"
        private set

    private var observer: ContentObserver? = null
    private val known = HashMap<String, Int>()

    /**
     * The badge authority is only trusted when a system package serves it: on a device where Samsung's provider is
     * absent the authority is free for any app to claim, and this source reports counts for every package
     * (adversarial review F5).
     */
    private fun systemProvider(context: Context): android.content.pm.ProviderInfo? {
        val provider = context.packageManager.resolveContentProvider(AUTHORITY, 0) ?: return null
        val app = provider.applicationInfo
        val isSystem = app != null && (app.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        if (!isSystem) {
            Diagnostics.add("livetile", "samsung badge authority is served by ${provider.packageName}, which is not a system package: ignored")
            return null
        }
        return provider
    }

    /** "readable" / "not present" / "not permitted: <reason>" for the onboarding checklist row. */
    fun probe(context: Context): String {
        val result = if (systemProvider(context) == null) {
            "not present"
        } else {
            try {
                context.contentResolver.query(URI, PROJECTION, "package=?", arrayOf(context.packageName), null)
                    ?.use { "readable" } ?: "not present"
            } catch (e: SecurityException) {
                "not permitted: ${e.message}"
            } catch (e: Exception) {
                "not permitted: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
        if (result != status) Diagnostics.add("livetile", "samsung badge provider probe: $result")
        status = result
        return result
    }

    /** Probes once and, when readable, ingests all rows and re-reads on every provider change. */
    @Synchronized
    fun start(context: Context, handler: Handler) {
        if (probe(context) != "readable" || observer != null) return
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                readAll(context)
            }
        }
        try {
            context.contentResolver.registerContentObserver(URI, true, obs)
            observer = obs
        } catch (e: SecurityException) {
            Diagnostics.add("livetile", "samsung badge provider observer refused: ${e.message}")
        }
        readAll(context)
    }

    @Synchronized
    fun readAll(context: Context) {
        if (systemProvider(context) == null) return
        val counts = HashMap<String, Int>()
        try {
            context.contentResolver.query(URI, PROJECTION, null, null, null)?.use { c ->
                val pkgCol = c.getColumnIndex("package")
                val countCol = c.getColumnIndex("badgecount")
                if (pkgCol < 0 || countCol < 0) {
                    Diagnostics.add("livetile", "samsung badge provider: unexpected columns ${c.columnNames.toList()}")
                    return
                }
                while (c.moveToNext()) {
                    val pkg = c.getString(pkgCol) ?: continue
                    // One row per launcher activity; the app's tile shows the largest, never a sum (R5 §4a rule 1).
                    counts[pkg] = maxOf(counts[pkg] ?: 0, c.getInt(countCol))
                }
            }
        } catch (e: SecurityException) {
            status = "not permitted: ${e.message}"
            Diagnostics.add("livetile", "samsung badge provider read refused: ${e.message}")
            return
        }
        for ((pkg, count) in counts) {
            if (known[pkg] != count) BadgeStore.set(pkg, BadgeStore.Source.SAMSUNG_PROVIDER, count)
        }
        for (pkg in known.keys - counts.keys) BadgeStore.set(pkg, BadgeStore.Source.SAMSUNG_PROVIDER, 0)
        known.clear()
        known.putAll(counts)
        Diagnostics.add("livetile", "samsung badge provider read ${counts.size} packages")
    }
}
