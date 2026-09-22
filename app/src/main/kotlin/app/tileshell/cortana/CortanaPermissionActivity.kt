package app.tileshell.cortana

import android.Manifest
import android.app.role.RoleManager
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
        Diagnostics.add("cortana", "assistant role request returned ${it.resultCode}")
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val wanted = intent?.getStringArrayExtra(EXTRA_PERMISSIONS)
        when {
            intent?.getBooleanExtra(EXTRA_ROLE, false) == true -> {
                val manager = getSystemService(RoleManager::class.java)
                if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    Diagnostics.add("cortana", "assistant role is not available on this device")
                    finish()
                    return
                }
                role.launch(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            }
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
