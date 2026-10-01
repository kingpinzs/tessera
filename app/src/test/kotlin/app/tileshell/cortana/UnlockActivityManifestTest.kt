package app.tileshell.cortana

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.w3c.dom.Element
import org.junit.Test

/**
 * L14-1: the unlock page must not show over the lock screen. A page that does occludes the keyguard as it opens, and
 * SystemUI's occlusion handling cancels the very dismiss the page asked for (qa/phase-14/L14-1/rootcause-run1). This
 * pins the manifest entry, the vector the defect came in by (fix review r2: nothing below the device rows guarded it).
 */
class UnlockActivityManifestTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun unlockActivity(): Element {
        // Unit tests run with the module directory as the working directory.
        val manifest = File("src/main/AndroidManifest.xml")
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest)
        val activities = doc.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val a = activities.item(i) as Element
            if (a.getAttributeNS(androidNs, "name") == ".cortana.CortanaUnlockActivity") return a
        }
        throw AssertionError("no .cortana.CortanaUnlockActivity in ${manifest.absolutePath}")
    }

    @Test
    fun `the unlock page does not show over the lock screen`() {
        val a = unlockActivity()
        assertNotNull(a)
        assertEquals("", a.getAttributeNS(androidNs, "showWhenLocked"))
        assertEquals("", a.getAttributeNS(androidNs, "turnScreenOn"))
    }

    @Test
    fun `the unlock page stays private to the shell`() {
        assertEquals("false", unlockActivity().getAttributeNS(androidNs, "exported"))
    }
}
