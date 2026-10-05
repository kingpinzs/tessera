package app.tileshell.testclient.qaflix

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** Shows the URI this fixture "streaming app" was opened with, whole, in the TextView `qa_flix_uri`. */
class MainActivity : Activity() {
    private lateinit var text: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            id = R.id.qa_flix_uri
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
        text.text = intent?.dataString ?: "(opened with no URI)"
    }
}
