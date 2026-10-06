package app.tileshell.net

import app.tileshell.video.server.ServerStore
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Phase 17 trust review, B-7 and B2-L3: the credential store's file and the media server's file — with the files
 * they are written through and beside (the store's temp and lock files, the server file's temp file, the device id
 * the server knows this install by) and the catalogue's directory (saved answers, posters, the last search) — leave
 * the phone by no backup and no device-to-device transfer. `allowBackup="false"` alone does not stop a transfer on
 * Android 12 and later; the manifest's `dataExtractionRules` does. This reads the two source files — that the platform
 * honours them is a device check.
 */
class DataExtractionRulesTest {
    private fun source(path: String): File = listOf(File(path), File("app/$path")).first { it.exists() }

    private fun root(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source(path)).documentElement

    private fun children(of: Element, tag: String): List<Element> {
        val nodes = of.childNodes
        return (0 until nodes.length).map(nodes::item).filterIsInstance<Element>().filter { it.tagName == tag }
    }

    /** Every name, from the constant its writer uses: a renamed file fails here until the rules name it. */
    private val secretFiles = setOf(
        CredentialStore.FILE_NAME,
        CredentialStore.FILE_NAME + CredentialFile.TEMP_SUFFIX,
        CredentialStore.FILE_NAME + CredentialFile.LOCK_SUFFIX,
        ServerStore.CONFIG_FILE,
        ServerStore.TEMP_FILE,
        ServerStore.DEVICE_FILE,
        app.tileshell.video.catalogue.Catalogue.DIR,
    )

    @Test fun `the names are the ones the stores write`() {
        assertEquals(
            setOf(
                "credentials_v1.json", "credentials_v1.json.tmp", "credentials_v1.json.lock",
                "media_server.json", "media_server.json.tmp", "media_server_device.txt", "video_catalogue",
            ),
            secretFiles,
        )
    }

    @Test fun `the credential store writes through the temp and lock files the rules name`() {
        val dir = java.nio.file.Files.createTempDirectory("rules").toFile()
        val plain = object : CredentialCipher {
            override fun seal(name: String, plain: ByteArray) = CredentialCipher.Sealed(ByteArray(12), plain)
            override fun open(name: String, sealed: CredentialCipher.Sealed) = sealed.ciphertext
        }
        // The rename refused: what the write left on the disk at that moment is every file the store ever makes.
        val seen = mutableSetOf<String>()
        CredentialFile(File(dir, CredentialStore.FILE_NAME), plain, rename = { _, _ -> seen += dir.list()!!; false }) {}.set("tmdb", "x")
        assertEquals(setOf("credentials_v1.json.tmp", "credentials_v1.json.lock"), seen)
        assertTrue(secretFiles.containsAll(seen))
    }

    @Test fun `cloud backup and device transfer both leave out every file of the credential store, the server and the catalogue`() {
        val rules = root("src/main/res/xml/data_extraction_rules.xml")
        assertEquals("data-extraction-rules", rules.tagName)
        for (section in listOf("cloud-backup", "device-transfer")) {
            val parts = children(rules, section)
            assertEquals(section, 1, parts.size)
            val excluded = children(parts.single(), "exclude")
            // Each is a file (or the one directory) of `files/`, named whole: no pattern that could miss one.
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
