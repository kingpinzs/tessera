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

    private fun parse(path: String): Config {
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        assertEquals("network-security-config", root.tagName)
        fun children(of: Element, tag: String): List<Element> {
            val out = mutableListOf<Element>()
            val nodes = of.childNodes
            for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.takeIf { it.tagName == tag }?.let(out::add)
            return out
        }
        val base = children(root, "base-config").singleOrNull()?.getAttribute("cleartextTrafficPermitted")?.toBooleanStrict()
        val configs = children(root, "domain-config").map { dc ->
            // A nested domain-config would inherit and override; this config has none, and the test keeps it so.
            assertTrue("no nested domain-config", children(dc, "domain-config").isEmpty())
            DomainConfig(
                cleartext = dc.getAttribute("cleartextTrafficPermitted").toBooleanStrict(),
                domains = children(dc, "domain").map { it.textContent.trim() to it.getAttribute("includeSubdomains").toBooleanStrict() },
            )
        }
        return Config(base, configs)
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
        val text = listOf(File("src/main/res/xml/network_security_config.xml"), File("app/src/main/res/xml/network_security_config.xml"))
            .first { it.exists() }.readText()
        assertFalse(text.contains("10.0.2.2"))
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
