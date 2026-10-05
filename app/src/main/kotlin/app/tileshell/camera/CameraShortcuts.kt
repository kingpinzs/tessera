package app.tileshell.camera

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import app.tileshell.R
import app.tileshell.diag.Diagnostics

/**
 * Camera's DYNAMIC App Shortcuts (build task 15; Decisions C-9 applied to Camera): Panorama (rank 2) and Slow motion
 * (rank 3), published on each start only where the mode's gate admits it and removed otherwise — a manifest shortcut
 * cannot be withdrawn, so a static entry would burst a mode the viewfinder then hides. Each names CameraActivity
 * (`setActivity`, C-21), or it would attach to the package's first launcher activity and burst on the Music tile.
 * Photo and Video are static (`res/xml/shortcuts_camera.xml`). Which ids are published is `CameraGates.dynamicShortcuts`.
 */
object CameraShortcuts {
    private val ALL = listOf("camera_panorama", "camera_slowmo")

    fun publish(context: Context, caps: CameraCaps?, env: CameraEnv) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val wanted = CameraGates.dynamicShortcuts(caps, env)
        val activity = ComponentName(context, CameraActivity::class.java)
        val infos = wanted.map { (id, rank) ->
            val mode = id.removePrefix("camera_")
            val label = context.getString(if (mode == "panorama") R.string.camera_shortcut_panorama else R.string.camera_shortcut_slowmo)
            ShortcutInfo.Builder(context, id)
                .setActivity(activity)
                .setShortLabel(label).setLongLabel(label)
                .setRank(rank)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_camera_glyph))
                .setIntent(Intent(Intent.ACTION_VIEW).setClass(context, CameraActivity::class.java).putExtra(CameraNav.EXTRA_MODE, mode))
                .build()
        }
        runCatching {
            val gone = ALL - wanted.map { it.first }.toSet()
            if (gone.isNotEmpty()) manager.removeDynamicShortcuts(gone)
            if (infos.isNotEmpty()) manager.addDynamicShortcuts(infos)
            Diagnostics.add("camera", "shortcuts dynamic: ${wanted.joinToString(",") { it.first }.ifEmpty { "none" }}")
        }.onFailure { Diagnostics.add("camera", "shortcuts dynamic: failed (${it.javaClass.simpleName})") }
    }
}
