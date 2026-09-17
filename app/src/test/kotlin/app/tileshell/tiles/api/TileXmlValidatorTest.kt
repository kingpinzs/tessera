package app.tileshell.tiles.api

import app.tileshell.tiles.api.TileXmlValidator.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class TileXmlValidatorTest {

    private fun valid(xml: String): TilePayload = when (val r = TileXmlValidator.validate(xml)) {
        is TileXmlResult.Valid -> r.payload
        is TileXmlResult.Invalid -> { fail("expected valid, got ${r.reason}: ${r.detail}"); error("unreachable") }
    }

    private fun invalid(xml: String): TileXmlResult.Invalid = when (val r = TileXmlValidator.validate(xml)) {
        is TileXmlResult.Invalid -> r
        is TileXmlResult.Valid -> { fail("expected rejection, got valid payload ${r.payload}"); error("unreachable") }
    }

    private fun parserOnly(xml: String): TileXmlResult.Invalid = when (val r = TileXmlValidator.parse(xml)) {
        is TileXmlResult.Invalid -> r
        is TileXmlResult.Valid -> { fail("parser layer accepted: ${r.payload}"); error("unreachable") }
    }

    private fun tile(binding: String) = "<tile><visual>$binding</visual></tile>"

    // ---------- accepted payloads ----------

    @Test fun minimalMediumText() {
        val p = valid(tile("""<binding template="TileMedium"><text>Hello</text></binding>"""))
        val b = p.binding(TileTemplate.MEDIUM)!!
        assertEquals(listOf(TileElement.Text("Hello", "caption", false, null, null, null)), b.children)
    }

    @Test fun fullPhonePayloadAllThreeSizes() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <!-- a comment is fine -->
            <tile>
              <visual branding="nameAndLogo" displayName="Mail" arguments="inbox" lang="en-US">
                <binding template="TileSmall"><text hint-style="titleNumeral">3</text></binding>
                <binding template="TileMedium" hint-textStacking="center" branding="logo">
                  <image src="content://com.example.mail.images/peek/1.png" placement="peek" hint-crop="circle"/>
                  <text hint-style="captionSubtle" hint-wrap="true" hint-maxLines="2" hint-align="center">Jen &amp; Sam &#169;</text>
                </binding>
                <binding template="TileWide" hint-presentation="people">
                  <image src="content://com.example.mail.images/bg.png" placement="background" hint-overlay="40"/>
                  <group>
                    <subgroup hint-weight="33"><image src="android.resource://com.example.mail/drawable/avatar" hint-removeMargin="true" alt="Jen"/></subgroup>
                    <subgroup hint-textStacking="bottom"><text hint-style="base">Subject</text><text hint-style="bodySubtle">Body</text></subgroup>
                  </group>
                </binding>
              </visual>
            </tile>"""
        val p = valid(xml)
        assertEquals("name", p.branding)
        assertEquals(3, p.bindings.size)
        assertEquals("name", p.binding(TileTemplate.MEDIUM)!!.branding)
        val medium = p.binding(TileTemplate.MEDIUM)!!.flatten()
        assertEquals("Jen & Sam ©", (medium[1] as TileElement.Text).content)
        assertEquals(ImagePlacement.PEEK, (medium[0] as TileElement.Image).placement)
        val images = p.images()
        assertEquals(3, images.size)
        assertEquals("com.example.mail.images", images[0].authority)
        assertEquals("android.resource", images[2].scheme)
        assertEquals("com.example.mail", images[2].authority)
    }

    @Test fun twelveDistinctImagesIsTheCap() {
        val imgs = (1..12).joinToString("") { """<image src="content://com.example.img/$it.png"/>""" }
        valid(tile("""<binding template="TileWide">$imgs</binding>"""))
        val thirteen = imgs + """<image src="content://com.example.img/13.png"/>"""
        assertEquals(Reason.IMAGE_COUNT, invalid(tile("""<binding template="TileWide">$thirteen</binding>""")).reason)
    }

    @Test fun photosPresentationCapsAtNineOnPhone() {
        val nine = (1..9).joinToString("") { """<image src="content://com.example.img/$it.png"/>""" }
        valid(tile("""<binding template="TileMedium" hint-presentation="photos">$nine</binding>"""))
        val ten = nine + """<image src="content://com.example.img/10.png"/>"""
        assertEquals(Reason.IMAGE_COUNT, invalid(tile("""<binding template="TileMedium" hint-presentation="photos">$ten</binding>""")).reason)
    }

    @Test fun exactlyEightKilobytesIsAccepted() {
        val shell = tile("""<binding template="TileMedium"><text></text></binding>""")
        val filler = "a".repeat(TileXmlValidator.MAX_XML_BYTES - shell.length)
        val xml = tile("""<binding template="TileMedium"><text>$filler</text></binding>""")
        assertEquals(TileXmlValidator.MAX_XML_BYTES, xml.toByteArray().size)
        valid(xml)
    }

    // ---------- adversarial: DTDs and entities ----------

    @Test fun internalDtdIsRejected() {
        val xml = """<?xml version="1.0"?><!DOCTYPE tile [<!ENTITY x "boom">]>""" + tile("""<binding template="TileMedium"><text>&x;</text></binding>""")
        assertEquals(Reason.DTD, invalid(xml).reason)
        parserOnly(xml)
    }

    @Test fun doctypeIsRejectedCaseInsensitivelyAndWithoutSubset() {
        assertEquals(Reason.DTD, invalid("""<!doctype tile>""" + tile("""<binding template="TileMedium"/>""")).reason)
        assertEquals(Reason.DTD, invalid("""<!DOCTYPE tile SYSTEM "http://attacker.invalid/tile.dtd">""" + tile("""<binding template="TileMedium"/>""")).reason)
    }

    @Test fun externalEntityIsRejectedAndNeverRead() {
        val secret = File.createTempFile("livetile-xxe", ".txt").apply { writeText("SECRET-MARKER-42"); deleteOnExit() }
        val xml = """<?xml version="1.0"?><!DOCTYPE tile [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>""" +
            tile("""<binding template="TileMedium"><text>&xxe;</text></binding>""")
        val r = invalid(xml)
        assertEquals(Reason.DTD, r.reason)
        assertFalse(r.detail.contains("SECRET-MARKER-42"))
        // Defence in depth: the parser layer alone (no lexical prescan) must also refuse and never expand it.
        val p = parserOnly(xml)
        assertFalse(p.detail.contains("SECRET-MARKER-42"))
    }

    @Test fun externalParameterEntityIsRejectedByBothLayers() {
        val xml = """<!DOCTYPE tile [<!ENTITY % ext SYSTEM "http://attacker.invalid/x.dtd"> %ext;]>""" + tile("""<binding template="TileMedium"/>""")
        assertEquals(Reason.DTD, invalid(xml).reason)
        parserOnly(xml)
    }

    @Test(timeout = 5_000)
    fun billionLaughsIsRejectedQuicklyByBothLayers() {
        val sb = StringBuilder("""<?xml version="1.0"?><!DOCTYPE tile [<!ENTITY lol "lol">""")
        sb.append("""<!ENTITY lol1 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">""")
        for (i in 2..9) sb.append("<!ENTITY lol$i \"" + "&lol${i - 1};".repeat(10) + "\">")
        sb.append("]>").append(tile("""<binding template="TileMedium"><text>&lol9;</text></binding>"""))
        val xml = sb.toString()
        assertEquals(Reason.DTD, invalid(xml).reason)
        val p = parserOnly(xml)
        assertTrue(p.detail.length < 10_000)
    }

    @Test fun undeclaredEntityReferenceIsRejected() {
        val xml = tile("""<binding template="TileMedium"><text>&xxe;</text></binding>""")
        assertEquals(Reason.ENTITY, invalid(xml).reason)
        parserOnly(xml)
    }

    @Test fun processingInstructionIsRejected() {
        val xml = """<?xml version="1.0"?><?xml-stylesheet href="http://attacker.invalid/x.xsl"?>""" + tile("""<binding template="TileMedium"/>""")
        assertEquals(Reason.PI, invalid(xml).reason)
        assertEquals(Reason.PI, invalid(tile("""<binding template="TileMedium"><?php boom ?></binding>""")).reason)
    }

    // ---------- adversarial: size ----------

    @Test fun oversizedPayloadIsRejected() {
        val big = tile("""<binding template="TileMedium"><text>${"a".repeat(9_000)}</text></binding>""")
        assertEquals(Reason.SIZE, invalid(big).reason)
    }

    @Test fun multibyteOversizeIsCountedInBytes() {
        val text = "€".repeat(3_000) // 3,000 chars, 9,000 UTF-8 bytes
        val xml = tile("""<binding template="TileMedium"><text>$text</text></binding>""")
        assertTrue(xml.length < TileXmlValidator.MAX_XML_BYTES)
        assertEquals(Reason.SIZE, invalid(xml).reason)
    }

    // ---------- W10M phone subset ----------

    @Test fun tileLargeIsRejected() {
        assertEquals(Reason.TILE_LARGE, invalid(tile("""<binding template="TileLarge"><text>x</text></binding>""")).reason)
    }

    @Test fun iconicAndLegacyTemplatesAreRejected() {
        assertEquals(Reason.VALUE, invalid(tile("""<binding template="TileSquare150x150IconWithBadge"/>""")).reason)
        assertEquals(Reason.VALUE, invalid(tile("""<binding template="TileWide310x150Text01"/>""")).reason)
    }

    @Test fun httpImagesAreRejected() {
        assertEquals(Reason.IMAGE_HTTP, invalid(tile("""<binding template="TileMedium"><image src="http://example.com/a.png"/></binding>""")).reason)
        assertEquals(Reason.IMAGE_HTTP, invalid(tile("""<binding template="TileMedium"><image src="HTTPS://example.com/a.png"/></binding>""")).reason)
    }

    @Test fun otherImageSchemesAndAuthorityTricksAreRejected() {
        fun img(src: String) = invalid(tile("""<binding template="TileMedium"><image src="$src"/></binding>""")).reason
        assertEquals(Reason.IMAGE_SCHEME, img("ms-appx:///Assets/a.png"))
        assertEquals(Reason.IMAGE_SCHEME, img("file:///data/data/app.tileshell/files/a.png"))
        assertEquals(Reason.IMAGE_SCHEME, img("data:image/png;base64,AAAA"))
        assertEquals(Reason.IMAGE_URI, img("content://10@com.example.img/a.png"))
        assertEquals(Reason.IMAGE_URI, img("content://com.example.img:99/a.png"))
        assertEquals(Reason.IMAGE_URI, img("content://com.example.img/a.png?x=1"))
        assertEquals(Reason.IMAGE_URI, img("android.resource://not a package/x"))
    }

    @Test fun unsupportedAttributesAreRejectedNotDropped() {
        assertEquals(Reason.ATTRIBUTE, invalid("""<tile><visual baseUri="http://x/"><binding template="TileMedium"/></visual></tile>""").reason)
        assertEquals(Reason.ATTRIBUTE, invalid("""<tile><visual hint-lockDetailedStatus1="x"><binding template="TileWide"/></visual></tile>""").reason)
        assertEquals(Reason.ATTRIBUTE, invalid(tile("""<binding template="TileMedium"><image src="content://a.b/c" addImageQuery="true"/></binding>""")).reason)
        assertEquals(Reason.ATTRIBUTE, invalid(tile("""<binding template="TileMedium"><text onload="x">a</text></binding>""")).reason)
    }

    @Test fun unknownElementsAreRejected() {
        assertEquals(Reason.ELEMENT, invalid(tile("""<binding template="TileMedium"><script>x</script></binding>""")).reason)
        assertEquals(Reason.ELEMENT, invalid("""<toast><visual/></toast>""").reason)
    }

    @Test fun badValuesAreRejected() {
        assertEquals(Reason.VALUE, invalid(tile("""<binding template="TileMedium"><text hint-style="huge">a</text></binding>""")).reason)
        assertEquals(Reason.VALUE, invalid(tile("""<binding template="TileMedium"><image src="content://a.b/c" hint-overlay="101"/></binding>""")).reason)
        assertEquals(Reason.VALUE, invalid(tile("""<binding template="TileMedium"><image src="content://a.b/c" placement="hero"/></binding>""")).reason)
        assertEquals(Reason.VALUE, invalid(tile("""<binding template="TileMedium" hint-presentation="slideshow"/>""")).reason)
        assertEquals(Reason.VALUE, invalid(tile("""<binding/>""")).reason)
    }

    @Test fun structureIsEnforced() {
        assertEquals(Reason.STRUCTURE, invalid("""<tile><visual>loose text<binding template="TileMedium"/></visual></tile>""").reason)
        assertEquals(Reason.STRUCTURE, invalid(tile("""<binding template="TileMedium"/><binding template="TileMedium"/>""")).reason)
        assertEquals(Reason.STRUCTURE, invalid(tile("""<binding template="TileMedium"><group><text>a</text></group></binding>""")).reason)
        assertEquals(Reason.STRUCTURE, invalid("""<tile><visual></visual></tile>""").reason)
        assertEquals(Reason.STRUCTURE, invalid("""<visual><binding template="TileMedium"/></visual>""").reason)
        assertEquals(Reason.MALFORMED, invalid("""<tile><visual><binding template="TileMedium">""").reason)
        assertEquals(Reason.MALFORMED, invalid("").reason)
    }
}
