package app.tileshell.net

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Phase 17 build task 17 (C-16): the fixed endpoints are https and the network security config covers exactly them. */
class FixedEndpointsTest {
    private data class DomainConfig(val cleartext: Boolean, val domains: List<Pair<String, Boolean>>)
    private data class Config(val baseCleartext: Boolean?, val domainConfigs: List<DomainConfig>)

    private fun read(path: String): String = listOf(File(path), File("app/$path")).first { it.exists() }.readText()

    private val mainPath = "src/main/res/xml/network_security_config.xml"
    private val debugPath = "src/debug/res/xml/network_security_config.xml"

    private fun children(of: Element, tag: String): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = of.childNodes
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.takeIf { it.tagName == tag }?.let(out::add)
        return out
    }

    private fun descendants(of: Element): List<Element> {
        val all = of.getElementsByTagName("*")
        return (0 until all.length).map { all.item(it) as Element }
    }

    private fun document(xml: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream()).documentElement

    private fun parse(path: String): Config = parseText(read(path))

    /**
     * A network security config as the tests below compare it. Every structural rule of C-M3 is asserted here, so no
     * copy of the config is read without them: nothing that changes whom the app trusts, and no padded host.
     */
    private fun parseText(xml: String): Config {
        val root = document(xml)
        assertEquals("network-security-config", root.tagName)
        // User-added CAs (trust-anchors / certificates), a debug-overrides block and a pin-set each change whom the
        // shell trusts; this config holds none, and nothing but the four elements it is made of.
        for (e in descendants(root)) {
            for (banned in listOf("trust-anchors", "certificates", "debug-overrides", "pin-set", "pin")) assertFalse("no <$banned> element", e.tagName == banned)
            assertTrue("unexpected element <${e.tagName}>", e.tagName in setOf("base-config", "domain-config", "domain"))
        }
        val base = children(root, "base-config").singleOrNull()?.getAttribute("cleartextTrafficPermitted")?.toBooleanStrict()
        val configs = children(root, "domain-config").map { dc ->
            // A nested domain-config would inherit and override; this config has none, and the test keeps it so.
            assertTrue("no nested domain-config", children(dc, "domain-config").isEmpty())
            DomainConfig(
                cleartext = dc.getAttribute("cleartextTrafficPermitted").toBooleanStrict(),
                domains = children(dc, "domain").map {
                    // Compared UNTRIMMED: the platform does not trim a <domain>'s text, so a padded host matches nothing
                    // and its cleartext rule silently never applies.
                    val host = it.textContent
                    assertEquals("a <domain>'s text is the host alone, with no whitespace around it", host.trim(), host)
                    assertTrue("a <domain> names a host: '$host'", host.isNotEmpty() && host.none(Char::isWhitespace))
                    host to it.getAttribute("includeSubdomains").toBooleanStrict()
                },
            )
        }
        return Config(base, configs)
    }

    /** The manifest's `<application>` must name the config, or the platform's default policy applies and the file is dead. */
    private fun assertManifestNamesTheConfig(manifestXml: String) {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(manifestXml.byteInputStream()).documentElement
        val apps = root.getElementsByTagName("application")
        assertEquals("one <application>", 1, apps.length)
        val app = apps.item(0) as Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("android:networkSecurityConfig", "@xml/network_security_config", app.getAttributeNS(android, "networkSecurityConfig"))
        // With the config named, this attribute would be ignored by the platform; here it could only mislead a reader.
        assertFalse("no android:usesCleartextTraffic beside the config", app.hasAttributeNS(android, "usesCleartextTraffic"))
    }

    private fun assertRejected(what: String, check: () -> Unit) {
        val failure = runCatching(check).exceptionOrNull()
        assertTrue("$what must fail the checks, and did not", failure is AssertionError)
    }

    @Test
    fun `C-M3 neither config holds a trust-anchors, debug-overrides or pin-set element, and a copy that does is rejected`() {
        for (path in listOf(mainPath, debugPath)) {
            val xml = read(path)
            parseText(xml)
            assertFalse(path, Regex("<\\s*(trust-anchors|debug-overrides|pin-set|certificates)").containsMatchIn(xml.replace(Regex("(?s)<!--.*?-->"), "")))
            val base = "<base-config cleartextTrafficPermitted=\"true\" />"
            assertTrue(path, base in xml)
            assertRejected("$path with user CAs trusted in the base config") {
                parseText(xml.replace(base, "<base-config cleartextTrafficPermitted=\"true\"><trust-anchors><certificates src=\"user\" /></trust-anchors></base-config>"))
            }
            assertRejected("$path with user CAs trusted for the fixed hosts") {
                parseText(xml.replace("<domain-config cleartextTrafficPermitted=\"false\">", "<domain-config cleartextTrafficPermitted=\"false\"><trust-anchors><certificates src=\"user\" /></trust-anchors>"))
            }
            assertRejected("$path with a debug-overrides block") {
                parseText(xml.replace("</network-security-config>", "<debug-overrides><trust-anchors><certificates src=\"user\" /></trust-anchors></debug-overrides></network-security-config>"))
            }
            assertRejected("$path with a pin-set") {
                parseText(xml.replace("<domain-config cleartextTrafficPermitted=\"false\">", "<domain-config cleartextTrafficPermitted=\"false\"><pin-set><pin digest=\"SHA-256\">AAAA</pin></pin-set>"))
            }
        }
    }

    @Test
    fun `C-M3 every domain's text is the bare host, and a padded copy is rejected`() {
        for (path in listOf(mainPath, debugPath)) {
            val xml = read(path)
            val hosts = parseText(xml).domainConfigs.flatMap { it.domains }.map { it.first }
            assertTrue(path, hosts.containsAll(FixedEndpoints.HOSTS))
            for (padded in listOf(">api.themoviedb.org </domain>", "> api.themoviedb.org</domain>", ">\n            api.themoviedb.org\n        </domain>", ">api.themoviedb.org\t</domain>")) {
                assertTrue(path, ">api.themoviedb.org</domain>" in xml)
                assertRejected("$path with a padded <domain>") { parseText(xml.replace(">api.themoviedb.org</domain>", padded)) }
            }
            assertRejected("$path with an empty <domain>") { parseText(xml.replace(">api.themoviedb.org</domain>", "></domain>")) }
        }
    }

    @Test
    fun `C-M3 the manifest's application names the network security config, and a copy without it is rejected`() {
        val manifest = read("src/main/AndroidManifest.xml")
        assertManifestNamesTheConfig(manifest)
        val attribute = "android:networkSecurityConfig=\"@xml/network_security_config\""
        assertEquals("the attribute is written once", 1, Regex(Regex.escape(attribute)).findAll(manifest).count())
        assertRejected("a manifest without the attribute") { assertManifestNamesTheConfig(manifest.replace(attribute, "")) }
        assertRejected("a manifest that names another config") { assertManifestNamesTheConfig(manifest.replace(attribute, "android:networkSecurityConfig=\"@xml/other\"")) }
        assertRejected("a manifest that also sets usesCleartextTraffic") { assertManifestNamesTheConfig(manifest.replace(attribute, "$attribute android:usesCleartextTraffic=\"true\"")) }
        // No other source set brings a manifest that could replace the attribute.
        val overlays = listOf("src/debug/AndroidManifest.xml", "src/release/AndroidManifest.xml").filter { File(it).exists() || File("app/$it").exists() }
        assertEquals("no build-type manifest overlays the application element", emptyList<String>(), overlays)
    }

    /** Every file named like the config, in any source set's any `res/xml*` folder: path under `app/src`. */
    private fun configCopies(): List<String> {
        val src = listOf(File("src"), File("app/src")).first { File(it, "main/res/xml/network_security_config.xml").isFile }
        return src.walkTopDown()
            .filter { it.isFile && it.name == "network_security_config.xml" && it.parentFile.name.startsWith("xml") && it.parentFile.parentFile.name == "res" }
            .map { it.relativeTo(src).invariantSeparatorsPath }.sorted().toList()
    }

    @Test
    fun `C2-M2 the only network security configs are main's and debug's - no other source set or qualified folder shadows them`() {
        // A copy in src/release/res/xml/ would replace main's in the release build, and one in res/xml-v34/ would win
        // on every phone the shell runs on (minSdk 34) — and none of the checks above would read either.
        assertEquals(listOf("debug/res/xml/network_security_config.xml", "main/res/xml/network_security_config.xml"), configCopies())
        // The manifest names the config by that one resource name, so a copy under another name is not the config.
        assertEquals(1, Regex("android:networkSecurityConfig=\"@xml/network_security_config\"").findAll(read("src/main/AndroidManifest.xml")).count())
        assertEquals(1, Regex("networkSecurityConfig").findAll(read("src/main/AndroidManifest.xml")).count())
    }

    @Test
    fun `every fixed endpoint is an https URL with a host`() {
        assertTrue(FixedEndpoints.URLS.isNotEmpty())
        for (url in FixedEndpoints.URLS) {
            assertTrue("$url is https", url.startsWith("https://"))
            assertTrue("$url has a host", FixedEndpoints.hostOf(url).contains('.'))
        }
        assertEquals("no host twice", FixedEndpoints.HOSTS.size, FixedEndpoints.HOSTS.toSet().size)
    }

    @Test
    fun `hostOf reads the host of a URL`() {
        assertEquals("api.themoviedb.org", FixedEndpoints.hostOf("https://api.themoviedb.org/3/configuration"))
        assertEquals("10.0.2.2", FixedEndpoints.hostOf("http://10.0.2.2:8090/img/x.png"))
        assertEquals("archive.org", FixedEndpoints.hostOf("https://archive.org/"))
    }

    @Test
    fun `the release config denies cleartext to exactly the fixed hosts and their subdomains`() {
        val config = parse("src/main/res/xml/network_security_config.xml")
        assertEquals("the base permits cleartext (Q-D A)", true, config.baseCleartext)
        val denied = config.domainConfigs.single()
        assertFalse(denied.cleartext)
        assertEquals(FixedEndpoints.HOSTS, denied.domains.map { it.first })
        assertTrue("every fixed host covers its subdomains", denied.domains.all { it.second })
    }

    @Test
    fun `the release config holds no emulator host`() {
        assertFalse(read(mainPath).contains("10.0.2.2"))
    }

    @Test
    fun `the debug config is the release config plus the one emulator host`() {
        val release = parse("src/main/res/xml/network_security_config.xml")
        val debug = parse("src/debug/res/xml/network_security_config.xml")
        assertEquals(release.baseCleartext, debug.baseCleartext)
        assertEquals(release.domainConfigs.single(), debug.domainConfigs.first { !it.cleartext })
        val extra = debug.domainConfigs.filter { it.cleartext }
        assertEquals(listOf(DomainConfig(true, listOf("10.0.2.2" to false))), extra)
        assertEquals(2, debug.domainConfigs.size)
    }
}
