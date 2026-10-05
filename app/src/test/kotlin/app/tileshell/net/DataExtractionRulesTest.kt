package app.tileshell.net

import app.tileshell.video.server.ServerStore
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Phase 17 trust review, B-7: the credential store's file and the media server's file leave the phone by no backup
 * and no device-to-device transfer. `allowBackup="false"` alone does not stop a transfer on Android 12 and later; the
 * manifest's `dataExtractionRules` does. This reads the two source files — that the platform honours them is a device
 * check.
 */
class DataExtractionRulesTest {
    private fun source(path: String): File = listOf(File(path), File("app/$path")).first { it.exists() }

    private fun root(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source(path)).documentElement

    private fun children(of: Element, tag: String): List<Element> {
        val nodes = of.childNodes
        return (0 until nodes.length).map(nodes::item).filterIsInstance<Element>().filter { it.tagName == tag }
    }

    private val secretFiles = setOf(CredentialStore.FILE_NAME, ServerStore.CONFIG_FILE)

    @Test fun `the two files are the ones the stores write`() {
        assertEquals(setOf("credentials_v1.json", "media_server.json"), secretFiles)
    }

    @Test fun `cloud backup and device transfer both leave out the credential file and the server file`() {
        val rules = root("src/main/res/xml/data_extraction_rules.xml")
        assertEquals("data-extraction-rules", rules.tagName)
        for (section in listOf("cloud-backup", "device-transfer")) {
            val parts = children(rules, section)
            assertEquals(section, 1, parts.size)
            val excluded = children(parts.single(), "exclude")
            // Each is a file of `files/`, named whole: no pattern that could miss one.
            assertTrue(section, excluded.all { it.getAttribute("domain") == "file" })
            assertEquals(section, secretFiles, excluded.map { it.getAttribute("path") }.toSet())
            // An <include> would turn the section into "only these": none may bring a secret file back in.
            assertTrue(section, children(parts.single(), "include").isEmpty())
        }
    }

    @Test fun `the manifest names the rules and still refuses backup`() {
        val application = children(root("src/main/AndroidManifest.xml"), "application").single()
        assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"))
        assertEquals("false", application.getAttribute("android:allowBackup"))
    }
}
