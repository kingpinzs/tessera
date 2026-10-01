package app.tileshell.qa.pickprobe

import android.app.Activity
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log

/**
 * A caller for People's `ACTION_PICK` that holds no permission (phase 16, Trust (c): "PICK grants its caller the one
 * contact or phone URI picked and nothing else").
 *
 *   am start -n app.tileshell.qa.pickprobe/.ProbeActivity --es kind contact|phone --es mode result|newtask|plain
 *
 * `result`  startActivityForResult — the way a real caller picks.
 * `newtask` startActivityForResult with FLAG_ACTIVITY_NEW_TASK — Android answers CANCELED at once; no grant may follow.
 * `plain`   startActivity — no caller to return to; the shell must open its plain list and grant nothing.
 *
 * Every fact is one logcat line under the tag PICKPROBE, `<name>: <value>`, so the row greps them.
 */
class ProbeActivity : Activity() {
    private var kind = "contact"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = intent.getStringExtra("kind") ?: "contact"
        val mode = intent.getStringExtra("mode") ?: "result"
        val pick = Intent(Intent.ACTION_PICK)
            .setType(if (kind == "phone") ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE else ContactsContract.Contacts.CONTENT_TYPE)
            .setComponent(ComponentName("app.tileshell", "app.tileshell.people.PeopleActivity"))
        say("start", "kind=$kind mode=$mode")
        // Before any pick: this app can read nothing of the Contacts provider.
        say("before contacts table", attempt { query(ContactsContract.Contacts.CONTENT_URI) })
        when (mode) {
            "plain" -> startActivity(pick)
            "newtask" -> startActivityForResult(pick.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), REQUEST)
            else -> startActivityForResult(pick, REQUEST)
        }
    }

    @Deprecated("the platform's own result callback is what a caller of ACTION_PICK uses")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        say("result code", if (resultCode == RESULT_OK) "RESULT_OK" else if (resultCode == RESULT_CANCELED) "RESULT_CANCELED" else resultCode.toString())
        val uri = data?.data
        say("result data", uri?.toString() ?: "none")
        say("result flags", "0x" + Integer.toHexString(data?.flags ?: 0))
        say("result clip", if (data?.clipData == null) "none" else "${data.clipData!!.itemCount} item(s)")
        say("result extras", data?.extras?.keySet()?.sorted()?.joinToString(",").orEmpty().ifEmpty { "none" })
        if (uri != null) {
            // The one row picked: readable.
            say("granted uri", attempt { query(uri) })
            // Everything else: not.
            say("contacts table", attempt { query(ContactsContract.Contacts.CONTENT_URI) })
            say("data table", attempt { query(ContactsContract.Data.CONTENT_URI) })
            say("phones table", attempt { query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI) })
            say("raw contacts table", attempt { query(ContactsContract.RawContacts.CONTENT_URI) })
            say("under the granted uri (/data)", attempt { query(Uri.withAppendedPath(uri, "data")) })
            say("under the granted uri (/entities)", attempt { query(Uri.withAppendedPath(uri, "entities")) })
            say("the photo under it", attempt { contentResolver.openInputStream(Uri.withAppendedPath(uri, "photo"))?.use { "${it.available()} bytes" } ?: "no stream" })
            say("write through the grant", attempt { "updated ${contentResolver.update(uri, ContentValues().apply { put("starred", 1) }, null, null)} row(s)" })
            say("delete through the grant", attempt { "deleted ${contentResolver.delete(uri, null, null)} row(s)" })
            say("persist the grant", attempt { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); "taken" })
            say("persisted grants", contentResolver.persistedUriPermissions.size.toString())
        }
        say("done", "kind=$kind")
        finish()
    }

    private fun query(uri: Uri): String = contentResolver.query(uri, null, null, null, null)?.use { c ->
        val names = c.columnNames.filter { it in SHOWN }
        val first = if (c.moveToFirst()) names.joinToString(" ") { n -> "$n=${runCatching { c.getString(c.getColumnIndexOrThrow(n)) }.getOrNull()}" } else ""
        "${c.count} row(s) $first".trim()
    } ?: "null cursor"

    private fun attempt(block: () -> String): String = try {
        "OK " + block()
    } catch (e: SecurityException) {
        "DENIED SecurityException"
    } catch (e: Exception) {
        "FAILED ${e.javaClass.simpleName}: ${e.message?.take(120)}"
    }

    private fun say(name: String, value: String) {
        Log.i(TAG, "$name: $value")
    }

    private companion object {
        const val TAG = "PICKPROBE"
        const val REQUEST = 16
        val SHOWN = setOf("display_name", "data1", "lookup", "_id")
    }
}
