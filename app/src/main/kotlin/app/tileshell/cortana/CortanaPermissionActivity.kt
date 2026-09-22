package app.tileshell.cortana

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.tileshell.diag.Diagnostics

/**
 * Grants a checklist row's permission, or asks for the assistant role, then gets out of the way.
 *
 * A VoiceInteractionSession is not an Activity, so it cannot raise a permission prompt or a role
 * request; this is the smallest thing that can. It draws nothing and finishes as soon as the prompt is
 * answered. The page that asked re-reads the live state when it comes back — nothing is cached.
 */
class CortanaPermissionActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        Diagnostics.add("cortana", "permission result: $result")
        finish()
    }

    private val role = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // The settings page does not report a result, so the resultCode says nothing about the
        // outcome. The page that asked re-reads the live role holder when it comes back.
        Diagnostics.add("cortana", "back from the assistant settings page (result ${it.resultCode})")
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val wanted = intent?.getStringArrayExtra(EXTRA_PERMISSIONS)
        when {
            intent?.getBooleanExtra(EXTRA_ROLE, false) == true -> requestAssistant()
            wanted.isNullOrEmpty() -> finish()
            else -> {
                // Background location cannot be asked for in the same prompt as the foreground one:
                // Android refuses the pair outright and grants neither. Foreground first, then the
                // upgrade, which is what "Allow all the time" really is.
                val background = Manifest.permission.ACCESS_BACKGROUND_LOCATION
                val ordered = if (background in wanted && wanted.size > 1) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        arrayOf(background)
                    } else {
                        wanted.filterNot { it == background }.toTypedArray()
                    }
                } else wanted
                Diagnostics.add("cortana", "requesting ${ordered.joinToString()}")
                permissions.launch(ordered)
            }
        }
    }

    /**
     * Send the user to where the assistant is actually chosen.
     *
     * This used to call `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)`, and on a real phone the
     * "Set as default" button then did NOTHING AT ALL (Jeremy, 2026-09-21, One UI). ROLE_ASSISTANT is
     * not a requestable role: an app may ask to become the dialer, the SMS app or the launcher, but the
     * assistant is the user's to set in Settings, so the role controller finishes the request straight
     * away with RESULT_CANCELED and never draws anything. `isRoleAvailable` does not catch it either —
     * the role exists on the device, it just cannot be requested — which is why this failed silently
     * instead of falling through to the branch below it.
     *
     * So: open the settings page that owns the choice, trying the most specific first and only ever
     * launching one that actually resolves, because these screens differ by OEM and Android version.
     * Which one was used goes to the diagnostics, so "nothing happened" can never again be invisible.
     */
    private fun requestAssistant() {
        val candidates = listOf(
            // The assist & voice input page: where "Digital assistant app" lives on AOSP and One UI.
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            // Some builds only have the default-apps list, which holds the same choice one level in.
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            // Last resort: this app's own page, from which Settings' search can reach the rest.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
        val available = getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
        val target = candidates.firstOrNull { packageManager.resolveActivity(it, 0) != null }
        if (target == null) {
            Diagnostics.add("cortana", "no settings page for the assistant role (roleAvailable=$available)")
            finish()
            return
        }
        Diagnostics.add("cortana", "assistant role: opening ${target.action} (roleAvailable=$available)")
        role.launch(target)
    }

    companion object {
        const val EXTRA_PERMISSIONS = "permissions"
        const val EXTRA_ROLE = "role"

        fun request(context: android.content.Context, permissions: List<String>) {
            if (permissions.isEmpty()) return
            context.startActivity(
                android.content.Intent(context, CortanaPermissionActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_PERMISSIONS, permissions.toTypedArray())
            )
        }

        fun requestAssistantRole(context: android.content.Context) {
            context.startActivity(
                android.content.Intent(context, CortanaPermissionActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_ROLE, true)
            )
        }
    }
}
