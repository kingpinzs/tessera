package app.tileshell.testclient.qatunes

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.widget.TextView

/**
 * The fixture "music service". Every intent it is opened with — the first one and each later one (singleTop) — is
 * written as ONE logcat line under the tag TileShellQa:
 *
 *     qa-tunes: action=<action> data=<uri|null> query=<SearchManager.QUERY|null> focus=<EXTRA_MEDIA_FOCUS|null> categories=<…> extras=<keys>
 *
 * and shown in the TextView `qa_tunes_intent`. `data` is the whole URI as received (the search form's q is still
 * encoded there); `q` is added, decoded, when the URI has one. It plays nothing.
 */
class MainActivity : Activity() {
    private lateinit var text: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            id = R.id.qa_tunes_intent
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(48, 240, 48, 48)
        }
        setContentView(text)
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent?) {
        val data = intent?.data
        val q = runCatching { data?.takeIf { it.isHierarchical }?.getQueryParameter("q") }.getOrNull()
        val line = "action=${intent?.action} data=${intent?.dataString}" + (if (q != null) " q=$q" else "") +
            " query=${intent?.getStringExtra(SearchManager.QUERY)}" +
            " focus=${intent?.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)}" +
            " categories=${intent?.categories?.sorted()?.joinToString(",") ?: "none"}" +
            " extras=${intent?.extras?.keySet()?.sorted()?.joinToString(",") ?: "none"}"
        Log.i(TAG, "qa-tunes: $line")
        text.text = line
    }

    private companion object {
        const val TAG = "TileShellQa"
    }
}
