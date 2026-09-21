package app.tileshell.tiles.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.tileshell.diag.Diagnostics
import java.util.concurrent.atomic.AtomicBoolean

/**
 * System-side events for the Live Tile API: the expiry / scheduled-delivery alarm (explicit, from the store's own
 * PendingIntent) and package lifecycle. Declared non-exported in the manifest for PACKAGE_FULLY_REMOVED (exempt from
 * the implicit-broadcast limits, so it reaches a stopped shell) and registered at runtime for added / removed /
 * replaced, which only runtime receivers get on API 26+.
 */
class LiveTileSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext ?: context
        val store = LiveTileStore.get(app)
        val pkg = intent.data?.schemeSpecificPart
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        when (intent.action) {
            ACTION_SWEEP -> store.requestSweep("alarm")
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> pkg?.let { p ->
                val pending = goAsync()
                // The tiles leave Start before the state is wiped: the layout is what names them (R5 §1.9).
                SecondaryTiles.onOwnerRemoved(app, p)
                store.handler.post {
                    try { store.onPackageRemoved(p, "package fully removed") } finally { pending.finish() }
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> if (pkg != null && !replacing) {
                SecondaryTiles.onOwnerRemoved(app, pkg)
                store.handler.post { store.onPackageRemoved(pkg, "package removed") }
            }
            Intent.ACTION_PACKAGE_ADDED -> pkg?.let { p ->
                Diagnostics.add("livetile", "package added $p replacing=$replacing")
                store.handler.post { store.onPackageAdded(p, replacing) }
                SecondaryTiles.onOwnerChanged(app, p)
            }
            Intent.ACTION_PACKAGE_REPLACED -> pkg?.let { p ->
                store.handler.post { store.onPackageAdded(p, true) }
                SecondaryTiles.onOwnerChanged(app, p)
            }
        }
    }

    companion object {
        const val ACTION_SWEEP = "app.tileshell.livetile.action.SWEEP"
        private val registered = AtomicBoolean(false)

        fun registerRuntime(app: Context) {
            if (!registered.compareAndSet(false, true)) return
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            // Protected system broadcasts reach a not-exported receiver; no other app can send these to it.
            app.registerReceiver(LiveTileSystemReceiver(), filter, Context.RECEIVER_NOT_EXPORTED)
        }
    }
}
