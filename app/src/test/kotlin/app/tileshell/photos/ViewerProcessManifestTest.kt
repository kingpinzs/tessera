package app.tileshell.photos

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The owner's ruling D3 (a), 2026-10-31: the viewer other apps open (`.photos.ViewerActivity`, VIEW on a content image)
 * runs in the editor's process, `:photosedit`, not in the launcher's — a hostile picture another app hands it is decoded
 * where a crash takes down only that process, never the one that draws Start. This pins the manifest: the viewer's
 * process, its unchanged way in, every other exported component's process (none moved by accident), what shares
 * `:photosedit`, and that the calls the other processes make to the launcher's are answered IN the launcher's process.
 */
class ViewerProcessManifestTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }

    private val components: List<Element> by lazy {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val app = doc.getElementsByTagName("application").item(0) as Element
        (0 until app.childNodes.length).map { app.childNodes.item(it) }.filterIsInstance<Element>()
            .filter { it.tagName in setOf("activity", "activity-alias", "service", "receiver", "provider") }
    }

    private fun Element.attr(name: String): String = getAttributeNS(androidNs, name)

    private fun component(name: String): Element =
        components.firstOrNull { it.attr("name") == name } ?: throw AssertionError("no $name in ${manifest.absolutePath}")

    /** The launcher's process is the default one: no `android:process`, read here as "". */
    private fun Element.process(): String = attr("process")

    @Test fun `the viewer other apps open runs in the editor's process`() {
        assertEquals(":photosedit", component(".photos.ViewerActivity").process())
    }

    @Test fun `the viewer keeps its one way in - exported, VIEW on a content image, a helper in its caller's task`() {
        val viewer = component(".photos.ViewerActivity")
        assertEquals("true", viewer.attr("exported"))
        // Standard launch mode and no task affinity of its own: it runs in its caller's task and is not a catalog app (L14-2).
        assertEquals("", viewer.attr("launchMode"))
        assertEquals("", viewer.attr("taskAffinity"))
        val filters = (0 until viewer.childNodes.length).map { viewer.childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == "intent-filter" }
        assertEquals(1, filters.size)
        fun Element.children(tag: String) = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }
        val filter = filters.single()
        assertEquals(listOf("android.intent.action.VIEW"), filter.children("action").map { it.attr("name") })
        assertEquals(listOf("android.intent.category.DEFAULT"), filter.children("category").map { it.attr("name") })
        assertEquals(listOf("content" to "image/*"), filter.children("data").map { it.attr("scheme") to it.attr("mimeType") })
    }

    @Test fun `no other exported component moved process`() {
        val exported = components.filter { it.attr("exported") == "true" }.associate { it.attr("name") to it.process() }
        val expected = mapOf(
            ".StartActivity" to "",
            ".music.MusicService" to "",
            ".music.MusicActivity" to "",
            ".clock.ClockActivity" to "",
            ".clock.AlarmApiActivity" to "",
            ".weather.WeatherActivity" to "",
            ".settings.SettingsActivity" to "",
            ".feeds.TileNotificationListener" to "",
            ".tiles.api.LiveTileProvider" to "",
            ".tiles.api.LegacyBadgeReceiver" to "",
            ".cortana.CortanaService" to "",
            ".cortana.CortanaSessionService" to "",
            ".cortana.CortanaRecognitionService" to "",
            ".ime.KeyboardService" to ":ime",
            ".recorder.RecorderActivity" to "",
            ".calculator.CalculatorActivity" to "",
            ".calendar.CalendarActivity" to "",
            ".calendar.CalendarReminderReceiver" to "",
            ".people.PeopleActivity" to "",
            ".photos.PhotosActivity" to "",
            ".photos.ViewerActivity" to ":photosedit",
            ".camera.CameraActivity" to ":camera",
            ".camera.CaptureActivity" to ":camera",
            ".video.VideoActivity" to ":video",
            ".video.PlayerActivity" to ":video",
            ".files.FilesActivity" to "",
        )
        assertEquals(expected, exported)
    }

    @Test fun `the editor's process holds the viewer, the editor and its ring, and nothing else`() {
        val photosedit = components.filter { it.process() == ":photosedit" }.map { it.attr("name") }.toSet()
        assertEquals(setOf(".photos.ViewerActivity", ".photos.EditActivity", ".photos.PhotosEditDumpService"), photosedit)
        assertEquals("false", component(".photos.EditActivity").attr("exported"))
        assertEquals("false", component(".photos.PhotosEditDumpService").attr("exported"))
    }

    @Test fun `the calls the other processes make to the launcher's are answered in the launcher's process, by the shell alone`() {
        val calls = component(".process.LauncherCallsProvider")
        assertEquals("", calls.process())
        assertEquals("false", calls.attr("exported"))
        assertEquals("app.tileshell.launchercalls", calls.attr("authorities"))
        assertTrue("no permission opens it to another app", calls.attr("permission").isEmpty() && calls.attr("readPermission").isEmpty() && calls.attr("writePermission").isEmpty())
        // D3 fix round (L1): one instance, the launcher's — `multiprocess` would build one in each caller's process, which
        // would answer from that process's own copy — and no URI grant through it.
        assertEquals("", calls.attr("multiprocess"))
        assertEquals("", calls.attr("grantUriPermissions"))
        // Nothing else on it: these three attributes and no child (no grant-uri-permission, no path-permission).
        val attributes = (0 until calls.attributes.length).map { calls.attributes.item(it) }
            .map { (if (it.namespaceURI == androidNs) "android:" else "${it.namespaceURI}:") + it.localName }.toSet()
        assertEquals(setOf("android:name", "android:authorities", "android:exported"), attributes)
        assertEquals(emptyList<String>(), (0 until calls.childNodes.length).map { calls.childNodes.item(it) }.filterIsInstance<Element>().map { it.tagName })
    }
}
