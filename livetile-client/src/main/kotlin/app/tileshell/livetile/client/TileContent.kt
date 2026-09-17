package app.tileshell.livetile.client

import android.net.Uri

/**
 * Builder for the adaptive tile XML subset the shell accepts (what Windows 10 Mobile rendered on phones):
 *
 * ```
 * val content = tileContent {
 *     medium { text("Jen", TextStyle.BASE); text("Lunch?", TextStyle.CAPTION_SUBTLE); image(uri, ImagePlacement.PEEK) }
 *     wide { group { subgroup(weight = 33) { image(avatar, crop = ImageCrop.CIRCLE) }; subgroup { text("Jen"); text("Lunch?") } } }
 * }
 * TileUpdater.forApplication(context).update(content)
 * ```
 * Images must be content:// URIs from the app's own provider (declared with grantUriPermissions="true") or
 * android.resource://<own package>/...; each at most 200 KB, at most 12 per notification.
 */
class TileContent internal constructor(
    private val branding: Branding?,
    private val displayName: String?,
    private val arguments: String?,
    private val lang: String?,
    private val bindings: List<Binding>,
) {
    internal class Binding(val template: String, val attrs: Map<String, String>, val children: List<Node>)

    internal sealed interface Node {
        fun write(sb: StringBuilder)
        fun uris(): List<Uri>
    }

    internal class TextNode(val text: String, val attrs: Map<String, String>) : Node {
        override fun write(sb: StringBuilder) {
            sb.append("<text").appendAttrs(attrs).append('>').append(escape(text)).append("</text>")
        }
        override fun uris() = emptyList<Uri>()
    }

    internal class ImageNode(val uri: Uri, val attrs: Map<String, String>) : Node {
        override fun write(sb: StringBuilder) {
            sb.append("<image src=\"").append(escape(uri.toString())).append('"').appendAttrs(attrs).append("/>")
        }
        override fun uris() = listOf(uri)
    }

    internal class GroupNode(val subgroups: List<Pair<Map<String, String>, List<Node>>>) : Node {
        override fun write(sb: StringBuilder) {
            sb.append("<group>")
            for ((attrs, children) in subgroups) {
                sb.append("<subgroup").appendAttrs(attrs).append('>')
                children.forEach { it.write(sb) }
                sb.append("</subgroup>")
            }
            sb.append("</group>")
        }
        override fun uris() = subgroups.flatMap { (_, children) -> children.flatMap { it.uris() } }
    }

    fun toXml(): String {
        val sb = StringBuilder("<tile><visual")
        sb.appendAttrs(
            buildMap {
                branding?.let { put("branding", it.value) }
                displayName?.let { put("displayName", it) }
                arguments?.let { put("arguments", it) }
                lang?.let { put("lang", it) }
            },
        ).append('>')
        for (b in bindings) {
            sb.append("<binding template=\"").append(b.template).append('"').appendAttrs(b.attrs).append('>')
            b.children.forEach { it.write(sb) }
            sb.append("</binding>")
        }
        return sb.append("</visual></tile>").toString()
    }

    fun imageUris(): Set<Uri> = bindings.flatMap { b -> b.children.flatMap { it.uris() } }.toSet()

    internal companion object {
        fun StringBuilder.appendAttrs(attrs: Map<String, String>): StringBuilder {
            attrs.forEach { (k, v) -> append(' ').append(k).append("=\"").append(escape(v)).append('"') }
            return this
        }

        /** XML 1.0 escaping; characters XML cannot carry at all are dropped rather than sent and refused. */
        fun escape(s: String): String = buildString(s.length) {
            for (c in s) {
                when {
                    c == '&' -> append("&amp;")
                    c == '<' -> append("&lt;")
                    c == '>' -> append("&gt;")
                    c == '"' -> append("&quot;")
                    c == '\'' -> append("&apos;")
                    c == '\t' || c == '\n' || c == '\r' || c >= ' ' && c != '￾' && c != '￿' -> append(c)
                }
            }
        }
    }
}

enum class Branding(val value: String) { NONE("none"), NAME("name") }
enum class TextStacking(val value: String) { TOP("top"), CENTER("center"), BOTTOM("bottom") }
enum class Presentation(val value: String) { PHOTOS("photos"), PEOPLE("people"), CONTACT("contact") }
enum class TextAlign(val value: String) { LEFT("left"), CENTER("center"), RIGHT("right") }
enum class ImageAlign(val value: String) { STRETCH("stretch"), LEFT("left"), CENTER("center"), RIGHT("right") }
enum class ImagePlacement(val value: String) { INLINE("inline"), BACKGROUND("background"), PEEK("peek") }
enum class ImageCrop(val value: String) { NONE("none"), CIRCLE("circle") }

enum class TextStyle(val value: String) {
    CAPTION("caption"), CAPTION_SUBTLE("captionSubtle"),
    BODY("body"), BODY_SUBTLE("bodySubtle"),
    BASE("base"), BASE_SUBTLE("baseSubtle"),
    SUBTITLE("subtitle"), SUBTITLE_SUBTLE("subtitleSubtle"),
    TITLE("title"), TITLE_SUBTLE("titleSubtle"), TITLE_NUMERAL("titleNumeral"),
    SUBHEADER("subheader"), SUBHEADER_SUBTLE("subheaderSubtle"), SUBHEADER_NUMERAL("subheaderNumeral"),
    HEADER("header"), HEADER_SUBTLE("headerSubtle"), HEADER_NUMERAL("headerNumeral"),
}

@DslMarker
annotation class TileDsl

fun tileContent(block: TileContentBuilder.() -> Unit): TileContent = TileContentBuilder().apply(block).build()

@TileDsl
class TileContentBuilder {
    var branding: Branding? = null
    var displayName: String? = null
    var arguments: String? = null
    var lang: String? = null
    private val bindings = LinkedHashMap<String, TileContent.Binding>()

    fun small(block: BindingBuilder.() -> Unit) = binding("TileSmall", block)
    fun medium(block: BindingBuilder.() -> Unit) = binding("TileMedium", block)
    fun wide(block: BindingBuilder.() -> Unit) = binding("TileWide", block)

    private fun binding(template: String, block: BindingBuilder.() -> Unit) {
        bindings[template] = BindingBuilder().apply(block).build(template)
    }

    fun build() = TileContent(branding, displayName, arguments, lang, bindings.values.toList())
}

@TileDsl
open class ContainerBuilder {
    internal val children = mutableListOf<TileContent.Node>()

    fun text(
        text: String,
        style: TextStyle = TextStyle.CAPTION,
        wrap: Boolean = false,
        maxLines: Int? = null,
        minLines: Int? = null,
        align: TextAlign? = null,
    ) {
        children += TileContent.TextNode(text, buildMap {
            if (style != TextStyle.CAPTION) put("hint-style", style.value)
            if (wrap) put("hint-wrap", "true")
            maxLines?.let { put("hint-maxLines", it.toString()) }
            minLines?.let { put("hint-minLines", it.toString()) }
            align?.let { put("hint-align", it.value) }
        })
    }

    fun image(
        uri: Uri,
        placement: ImagePlacement = ImagePlacement.INLINE,
        crop: ImageCrop? = null,
        overlay: Int? = null,
        removeMargin: Boolean = false,
        align: ImageAlign? = null,
        alt: String? = null,
    ) {
        children += TileContent.ImageNode(uri, buildMap {
            if (placement != ImagePlacement.INLINE) put("placement", placement.value)
            crop?.let { put("hint-crop", it.value) }
            overlay?.let { put("hint-overlay", it.toString()) }
            if (removeMargin) put("hint-removeMargin", "true")
            align?.let { put("hint-align", it.value) }
            alt?.let { put("alt", it) }
        })
    }
}

@TileDsl
class BindingBuilder : ContainerBuilder() {
    var branding: Branding? = null
    var displayName: String? = null
    var arguments: String? = null
    var textStacking: TextStacking? = null
    var presentation: Presentation? = null

    fun group(block: GroupBuilder.() -> Unit) {
        children += TileContent.GroupNode(GroupBuilder().apply(block).subgroups)
    }

    internal fun build(template: String) = TileContent.Binding(template, buildMap {
        branding?.let { put("branding", it.value) }
        displayName?.let { put("displayName", it) }
        arguments?.let { put("arguments", it) }
        textStacking?.let { put("hint-textStacking", it.value) }
        presentation?.let { put("hint-presentation", it.value) }
    }, children.toList())
}

@TileDsl
class GroupBuilder {
    internal val subgroups = mutableListOf<Pair<Map<String, String>, List<TileContent.Node>>>()

    fun subgroup(weight: Int? = null, textStacking: TextStacking? = null, block: ContainerBuilder.() -> Unit) {
        val attrs = buildMap {
            weight?.let { put("hint-weight", it.toString()) }
            textStacking?.let { put("hint-textStacking", it.value) }
        }
        subgroups += attrs to ContainerBuilder().apply(block).children.toList()
    }
}
