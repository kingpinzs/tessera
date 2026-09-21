package app.tileshell.tiles.api

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.XMLReader
import org.xml.sax.ext.DefaultHandler2
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/*
 * Adaptive tile XML restricted to what Windows 10 Mobile rendered on phones (R5 §1.7, §1.12, §4b):
 * <tile><visual><binding template="TileSmall|TileMedium|TileWide"> text / image / group>subgroup </binding></visual></tile>.
 * Pure JVM (no Android classes) so the validator is unit-tested on the JVM with the same code the provider runs.
 */

enum class TileTemplate(val xmlName: String) { SMALL("TileSmall"), MEDIUM("TileMedium"), WIDE("TileWide") }

enum class ImagePlacement { INLINE, BACKGROUND, PEEK }

sealed interface TileElement {
    data class Text(
        val content: String,
        val style: String,
        val wrap: Boolean,
        val maxLines: Int?,
        val minLines: Int?,
        val align: String?,
    ) : TileElement

    data class Image(
        val src: String,
        /** content:// authority or android.resource:// package; the provider checks it belongs to the caller. */
        val authority: String,
        val scheme: String,
        val placement: ImagePlacement,
        val crop: String?,
        val overlay: Int?,
        val removeMargin: Boolean,
        val align: String?,
        val alt: String?,
    ) : TileElement

    data class Group(val subgroups: List<Subgroup>) : TileElement
}

data class Subgroup(val weight: Int?, val textStacking: String?, val children: List<TileElement>)

data class TileBinding(
    val template: TileTemplate,
    val branding: String?,
    val displayName: String?,
    val arguments: String?,
    val textStacking: String?,
    val presentation: String?,
    val children: List<TileElement>,
) {
    /** Text and images in document order, groups flattened. */
    fun flatten(): List<TileElement> = children.flatMap { el ->
        if (el is TileElement.Group) el.subgroups.flatMap { it.children } else listOf(el)
    }
}

data class TilePayload(
    val branding: String?,
    val displayName: String?,
    val arguments: String?,
    val lang: String?,
    val bindings: List<TileBinding>,
) {
    fun images(): List<TileElement.Image> = bindings.flatMap { b -> b.flatten().filterIsInstance<TileElement.Image>() }
    fun binding(template: TileTemplate): TileBinding? = bindings.firstOrNull { it.template == template }
}

sealed interface TileXmlResult {
    data class Valid(val payload: TilePayload) : TileXmlResult
    data class Invalid(val reason: String, val detail: String) : TileXmlResult
}

/** One image URI checked on its own (a secondary tile's logo, R5 §4b), with the same rules as an `<image src>`. */
sealed interface ImageSourceResult {
    data class Ok(val image: TileElement.Image) : ImageSourceResult
    data class Invalid(val reason: String, val detail: String) : ImageSourceResult
}

object TileXmlValidator {
    const val MAX_XML_BYTES = 8 * 1024
    const val MAX_IMAGES = 12
    const val MAX_PHOTOS = 9

    object Reason {
        const val SIZE = "xml-size"
        const val DTD = "xml-dtd"
        const val ENTITY = "xml-entity"
        const val PI = "xml-pi"
        const val MALFORMED = "xml-malformed"
        const val ELEMENT = "xml-element"
        const val ATTRIBUTE = "xml-attribute"
        const val VALUE = "xml-value"
        const val STRUCTURE = "xml-structure"
        const val TILE_LARGE = "tile-large"
        const val IMAGE_HTTP = "image-http"
        const val IMAGE_SCHEME = "image-scheme"
        const val IMAGE_URI = "image-uri"
        const val IMAGE_COUNT = "image-count"
    }

    private val PREDEFINED_NAMES = setOf("amp", "lt", "gt", "quot", "apos")
    private val PREDEFINED_REF = Regex("^&(amp|lt|gt|quot|apos|#[0-9]{1,7}|#x[0-9A-Fa-f]{1,6});")
    private val LANG = Regex("^[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*$")
    private val CONTENT_URI = Regex("^content://([A-Za-z0-9._-]+)(/[^?#\\s]*)?$")
    private val RESOURCE_URI = Regex("^android\\.resource://([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+)(/[^?#\\s]*)?$")
    private val STYLE_BASES = listOf("caption", "body", "base", "subtitle", "title", "subheader", "header")
    private val STYLES = STYLE_BASES.flatMap { b -> listOf(b, "${b}Subtle", "${b}Numeral", "${b}NumeralSubtle") }.toSet()
    private val BRANDING = setOf("none", "name", "logo", "nameAndLogo")
    private val STACKING = setOf("top", "center", "bottom")
    private val PRESENTATION = setOf("photos", "people", "contact")
    private val TEXT_ALIGN = setOf("left", "center", "right")
    private val IMAGE_ALIGN = setOf("stretch", "left", "center", "right")
    private val CROP = setOf("none", "circle")
    private val BOOL = setOf("true", "false")

    /** The longest image URI accepted anywhere (an `<image src>` or a secondary tile's logo). */
    const val MAX_IMAGE_URI = 1024

    /**
     * Checks one image URI on its own and returns the image element a bare `<image src="…">` would produce: no
     * network (http(s) refused), only content:// and android.resource://, and a well-formed authority. Whether the
     * authority belongs to the CALLER is not decided here — that needs the package manager and stays in [ImageIngest].
     * The XML parser and the secondary-tile logo both come through this one function.
     */
    fun imageSource(src: String): ImageSourceResult {
        if (src.length > MAX_IMAGE_URI) return ImageSourceResult.Invalid(Reason.IMAGE_URI, "src longer than $MAX_IMAGE_URI chars")
        val scheme = src.substringBefore(':', "").lowercase()
        val (authority, kind) = when (scheme) {
            "http", "https" -> return ImageSourceResult.Invalid(Reason.IMAGE_HTTP, "web images are not fetched (no network)")
            "content" -> (CONTENT_URI.matchEntire(src) ?: return ImageSourceResult.Invalid(Reason.IMAGE_URI, "malformed content URI")).groupValues[1] to "content"
            "android.resource" -> (RESOURCE_URI.matchEntire(src) ?: return ImageSourceResult.Invalid(Reason.IMAGE_URI, "malformed android.resource URI")).groupValues[1] to "android.resource"
            else -> return ImageSourceResult.Invalid(Reason.IMAGE_SCHEME, "scheme '$scheme' (only content:// and android.resource://)")
        }
        return ImageSourceResult.Ok(
            TileElement.Image(
                src = src,
                authority = authority,
                scheme = kind,
                placement = ImagePlacement.INLINE,
                crop = null,
                overlay = null,
                removeMargin = false,
                align = null,
                alt = null,
            ),
        )
    }

    /** Attributes W10M phones did not render (R5 §4b): rejected with an explicit reason, never dropped. */
    private val UNSUPPORTED_ATTRIBUTES = setOf("baseUri", "addImageQuery", "hint-lockDetailedStatus1", "hint-lockDetailedStatus2", "hint-lockDetailedStatus3")

    private val ALLOWED = mapOf(
        "tile" to emptySet(),
        "visual" to setOf("branding", "displayName", "arguments", "lang"),
        "binding" to setOf("template", "branding", "displayName", "arguments", "lang", "hint-textStacking", "hint-presentation"),
        "text" to setOf("hint-style", "hint-wrap", "hint-maxLines", "hint-minLines", "hint-align", "lang"),
        "image" to setOf("src", "placement", "hint-crop", "hint-overlay", "hint-removeMargin", "hint-align", "alt"),
        "group" to emptySet(),
        "subgroup" to setOf("hint-weight", "hint-textStacking"),
    )

    /** Allowed children per parent element ("#document" = the root). */
    private val CHILDREN = mapOf(
        "#document" to setOf("tile"),
        "tile" to setOf("visual"),
        "visual" to setOf("binding"),
        "binding" to setOf("text", "image", "group"),
        "group" to setOf("subgroup"),
        "subgroup" to setOf("text", "image"),
        "text" to emptySet(),
        "image" to emptySet(),
    )

    fun validate(xml: String): TileXmlResult {
        prescan(xml)?.let { return it }
        return parse(xml)
    }

    /**
     * Lexical gate that runs before any parser sees the bytes, identical on the JVM and on Android:
     * size cap, no DOCTYPE / markup declarations, no processing instructions (except a leading XML
     * declaration), and no entity references other than the five predefined ones and character references.
     */
    internal fun prescan(xml: String): TileXmlResult.Invalid? {
        if (xml.length > MAX_XML_BYTES) return TileXmlResult.Invalid(Reason.SIZE, "${xml.length} chars > $MAX_XML_BYTES bytes")
        val bytes = xml.toByteArray(Charsets.UTF_8).size
        if (bytes > MAX_XML_BYTES) return TileXmlResult.Invalid(Reason.SIZE, "$bytes bytes > $MAX_XML_BYTES")
        var i = xml.indexOf("<!")
        while (i >= 0) {
            val rest = xml.regionMatches(i, "<!--", 0, 4) || xml.regionMatches(i, "<![CDATA[", 0, 9)
            if (!rest) return TileXmlResult.Invalid(Reason.DTD, "markup declaration at offset $i")
            i = xml.indexOf("<!", i + 2)
        }
        i = xml.indexOf("<?")
        while (i >= 0) {
            if (!(i == 0 && xml.startsWith("<?xml") && xml.length > 5 && xml[5].isWhitespace())) {
                return TileXmlResult.Invalid(Reason.PI, "processing instruction at offset $i")
            }
            i = xml.indexOf("<?", i + 2)
        }
        i = xml.indexOf('&')
        while (i >= 0) {
            if (PREDEFINED_REF.find(xml.subSequence(i, minOf(xml.length, i + 12))) == null) {
                return TileXmlResult.Invalid(Reason.ENTITY, "entity reference at offset $i")
            }
            i = xml.indexOf('&', i + 1)
        }
        return null
    }

    private class Coded(val reason: String, val detail: String) : SAXException("$reason: $detail")

    /**
     * SAX parse with DTDs, external entities and entity expansion disabled. Features the platform parser does not
     * recognise are skipped (Android's Expat reader supports a subset); the handler additionally throws on any DTD,
     * entity or declaration callback, and [prescan] has already refused the markup, so no configuration depends on
     * a single feature flag.
     */
    internal fun parse(xml: String): TileXmlResult {
        val handler = Builder()
        return try {
            val reader = newReader(handler)
            reader.parse(InputSource(StringReader(xml)))
            handler.result()
        } catch (e: Coded) {
            TileXmlResult.Invalid(e.reason, e.detail)
        } catch (e: SAXException) {
            TileXmlResult.Invalid(Reason.MALFORMED, e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            TileXmlResult.Invalid(Reason.MALFORMED, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun newReader(handler: Builder): XMLReader {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        val reader = factory.newSAXParser().xmlReader
        val features = listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            "http://xml.org/sax/features/validation" to false,
        )
        for ((name, value) in features) runCatching { reader.setFeature(name, value) }
        runCatching { reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler) }
        runCatching { reader.setProperty("http://xml.org/sax/properties/declaration-handler", handler) }
        reader.contentHandler = handler
        reader.errorHandler = handler
        reader.dtdHandler = handler
        reader.entityResolver = handler
        return reader
    }

    private class Node(val name: String, val attrs: Map<String, String>) {
        val children = mutableListOf<Any>() // TileElement, Subgroup, TileBinding, TilePayload
        val text = StringBuilder()
    }

    private class Builder : DefaultHandler2() {
        private val stack = ArrayDeque<Node>()
        private var payload: TilePayload? = null

        fun result(): TileXmlResult = payload?.let { TileXmlResult.Valid(it) }
            ?: TileXmlResult.Invalid(Reason.STRUCTURE, "no <tile> root")

        // --- anything DTD- or entity-shaped is refused outright ---
        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            throw Coded(Reason.DTD, "DOCTYPE $name")
        }

        override fun startEntity(name: String?) {
            if (name != null && name !in PREDEFINED_NAMES) throw Coded(Reason.ENTITY, "entity $name")
        }

        override fun internalEntityDecl(name: String?, value: String?) {
            throw Coded(Reason.ENTITY, "internal entity $name")
        }

        override fun externalEntityDecl(name: String?, publicId: String?, systemId: String?) {
            throw Coded(Reason.ENTITY, "external entity $name")
        }

        override fun elementDecl(name: String?, model: String?) {
            throw Coded(Reason.DTD, "element declaration $name")
        }

        override fun attributeDecl(eName: String?, aName: String?, type: String?, mode: String?, value: String?) {
            throw Coded(Reason.DTD, "attribute declaration $eName")
        }

        override fun notationDecl(name: String?, publicId: String?, systemId: String?) {
            throw Coded(Reason.DTD, "notation $name")
        }

        override fun unparsedEntityDecl(name: String?, publicId: String?, systemId: String?, notationName: String?) {
            throw Coded(Reason.ENTITY, "unparsed entity $name")
        }

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
            throw Coded(Reason.ENTITY, "external reference $systemId")
        }

        override fun resolveEntity(name: String?, publicId: String?, baseURI: String?, systemId: String?): InputSource {
            throw Coded(Reason.ENTITY, "external reference $systemId")
        }

        // getExternalSubset is intentionally not overridden: the default returns null (no subset), and parsers call
        // it for every document without a DOCTYPE, so throwing there would refuse valid payloads.

        override fun processingInstruction(target: String?, data: String?) {
            throw Coded(Reason.PI, "processing instruction $target")
        }

        override fun skippedEntity(name: String?) {
            throw Coded(Reason.ENTITY, "skipped entity $name")
        }

        override fun error(e: org.xml.sax.SAXParseException) {
            throw e
        }

        override fun fatalError(e: org.xml.sax.SAXParseException) {
            throw e
        }

        override fun startElement(uri: String?, localName: String?, rawQName: String?, attributes: Attributes) {
            // Parsers differ on which name field they fill with namespaces off; accept either.
            val qName = rawQName?.takeIf { it.isNotEmpty() } ?: localName.orEmpty()
            val parent = stack.lastOrNull()?.name ?: "#document"
            val allowedAttrs = ALLOWED[qName] ?: throw Coded(Reason.ELEMENT, "<$qName> is not a W10M phone tile element")
            if (qName !in CHILDREN.getValue(parent)) throw Coded(Reason.STRUCTURE, "<$qName> inside <$parent>")
            if (parent == "#document" && payload != null) throw Coded(Reason.STRUCTURE, "second root")
            val attrs = LinkedHashMap<String, String>()
            for (i in 0 until attributes.length) {
                val name = attributes.getQName(i)?.takeIf { it.isNotEmpty() } ?: attributes.getLocalName(i).orEmpty()
                if (name in UNSUPPORTED_ATTRIBUTES) throw Coded(Reason.ATTRIBUTE, "$name on <$qName> is not rendered on W10M phones")
                if (name !in allowedAttrs) throw Coded(Reason.ATTRIBUTE, "$name on <$qName>")
                attrs[name] = attributes.getValue(i)
            }
            stack.addLast(Node(qName, attrs))
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            val node = stack.lastOrNull() ?: return
            if (node.name == "text") node.text.appendRange(ch, start, start + length)
            else if (!String(ch, start, length).isBlank()) throw Coded(Reason.STRUCTURE, "text content inside <${node.name}>")
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val node = stack.removeLast()
            val built: Any = when (node.name) {
                "text" -> text(node)
                "image" -> image(node)
                "subgroup" -> Subgroup(
                    weight = node.attrs["hint-weight"]?.let { int(it, 1, 100, "hint-weight") },
                    textStacking = node.attrs["hint-textStacking"]?.let { oneOf(it, STACKING, "hint-textStacking") },
                    children = node.children.map { it as TileElement },
                )
                "group" -> {
                    if (node.children.isEmpty()) throw Coded(Reason.STRUCTURE, "<group> without <subgroup>")
                    TileElement.Group(node.children.map { it as Subgroup })
                }
                "binding" -> binding(node)
                "visual" -> visual(node)
                "tile" -> node.children.singleOrNull() as? TilePayload ?: throw Coded(Reason.STRUCTURE, "<tile> needs exactly one <visual>")
                else -> throw Coded(Reason.ELEMENT, node.name)
            }
            val parent = stack.lastOrNull()
            if (parent == null) payload = built as TilePayload else parent.children += built
        }

        private fun text(node: Node) = TileElement.Text(
            content = node.text.toString(),
            style = node.attrs["hint-style"]?.let { oneOf(it, STYLES, "hint-style") } ?: "caption",
            wrap = node.attrs["hint-wrap"]?.let { oneOf(it, BOOL, "hint-wrap") == "true" } ?: false,
            maxLines = node.attrs["hint-maxLines"]?.let { int(it, 1, 10, "hint-maxLines") },
            minLines = node.attrs["hint-minLines"]?.let { int(it, 1, 10, "hint-minLines") },
            align = node.attrs["hint-align"]?.let { oneOf(it, TEXT_ALIGN, "hint-align") },
        ).also { lang(node) }

        private fun image(node: Node): TileElement.Image {
            val src = node.attrs["src"] ?: throw Coded(Reason.VALUE, "<image> without src")
            val base = when (val r = imageSource(src)) {
                is ImageSourceResult.Ok -> r.image
                is ImageSourceResult.Invalid -> throw Coded(r.reason, r.detail)
            }
            return base.copy(
                placement = when (node.attrs["placement"] ?: "inline") {
                    "inline" -> ImagePlacement.INLINE
                    "background" -> ImagePlacement.BACKGROUND
                    "peek" -> ImagePlacement.PEEK
                    else -> throw Coded(Reason.VALUE, "placement=${node.attrs["placement"]}")
                },
                crop = node.attrs["hint-crop"]?.let { oneOf(it, CROP, "hint-crop") },
                overlay = node.attrs["hint-overlay"]?.let { int(it, 0, 100, "hint-overlay") },
                removeMargin = node.attrs["hint-removeMargin"]?.let { oneOf(it, BOOL, "hint-removeMargin") == "true" } ?: false,
                align = node.attrs["hint-align"]?.let { oneOf(it, IMAGE_ALIGN, "hint-align") },
                alt = node.attrs["alt"]?.also { if (it.length > 256) throw Coded(Reason.VALUE, "alt longer than 256") },
            )
        }

        private fun binding(node: Node): TileBinding {
            val templateName = node.attrs["template"] ?: throw Coded(Reason.VALUE, "<binding> without template")
            if (templateName == "TileLarge") throw Coded(Reason.TILE_LARGE, "W10M phones had no large tile")
            val template = TileTemplate.entries.firstOrNull { it.xmlName == templateName }
                ?: throw Coded(Reason.VALUE, "template=$templateName")
            lang(node)
            val binding = TileBinding(
                template = template,
                branding = node.attrs["branding"]?.let { branding(it) },
                displayName = node.attrs["displayName"]?.let { bounded(it, 256, "displayName") },
                arguments = node.attrs["arguments"]?.let { bounded(it, 2048, "arguments") },
                textStacking = node.attrs["hint-textStacking"]?.let { oneOf(it, STACKING, "hint-textStacking") },
                presentation = node.attrs["hint-presentation"]?.let { oneOf(it, PRESENTATION, "hint-presentation") },
                children = node.children.map { it as TileElement },
            )
            if (binding.presentation == "photos") {
                val photos = binding.flatten().count { it is TileElement.Image }
                if (photos > MAX_PHOTOS) throw Coded(Reason.IMAGE_COUNT, "$photos photos > $MAX_PHOTOS on phone")
            }
            return binding
        }

        private fun visual(node: Node): TilePayload {
            val bindings = node.children.map { it as TileBinding }
            if (bindings.isEmpty()) throw Coded(Reason.STRUCTURE, "<visual> without <binding>")
            val dup = bindings.groupBy { it.template }.filterValues { it.size > 1 }.keys
            if (dup.isNotEmpty()) throw Coded(Reason.STRUCTURE, "duplicate binding ${dup.first().xmlName}")
            val payload = TilePayload(
                branding = node.attrs["branding"]?.let { branding(it) },
                displayName = node.attrs["displayName"]?.let { bounded(it, 256, "displayName") },
                arguments = node.attrs["arguments"]?.let { bounded(it, 2048, "arguments") },
                lang = lang(node),
                bindings = bindings,
            )
            val distinct = payload.images().map { it.src }.toSet()
            if (distinct.size > MAX_IMAGES) throw Coded(Reason.IMAGE_COUNT, "${distinct.size} images > $MAX_IMAGES")
            return payload
        }

        /** W10M phones rendered only `name` or `none`; logo forms map to name (R5 §1.12). */
        private fun branding(value: String) = when (oneOf(value, BRANDING, "branding")) {
            "none" -> "none"
            else -> "name"
        }

        private fun lang(node: Node): String? = node.attrs["lang"]?.also {
            if (!LANG.matches(it) || it.length > 35) throw Coded(Reason.VALUE, "lang=$it")
        }

        private fun oneOf(value: String, allowed: Set<String>, attr: String): String =
            if (value in allowed) value else throw Coded(Reason.VALUE, "$attr=$value")

        private fun int(value: String, min: Int, max: Int, attr: String): Int =
            value.toIntOrNull()?.takeIf { it in min..max } ?: throw Coded(Reason.VALUE, "$attr=$value (expected $min..$max)")

        private fun bounded(value: String, max: Int, attr: String): String =
            if (value.length <= max) value else throw Coded(Reason.VALUE, "$attr longer than $max")
    }
}
