package app.tileshell.qa.imefixture

import android.text.InputFilter
import android.widget.EditText

/** The launcher screen: mirrors on top, a ScrollView of one field per input type / IME action. */
class MainActivity : FixtureActivity(R.layout.activity_main) {

    override fun onFieldsReady() {
        findViewById<EditText>(R.id.field_filter).filters =
            arrayOf(InputFilter.LengthFilter(5), LOWERCASE_ONLY)
    }
}
