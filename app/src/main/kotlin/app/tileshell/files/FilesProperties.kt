package app.tileshell.files

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tileshell.ui.tokens.ShellType
import java.io.File
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One "label: value" line of the Properties page: `files_prop_label:<id>` / `files_prop_value:<id>`. */
class PropRow(val id: String, val label: String, val value: String)

/** A per-type section (r11/files.md 1.10.4): a SemiBold header (`files_prop_section:<id>`) with a rule under it. */
class PropSection(val id: String, val title: String, val rows: List<PropRow>)

/** Everything the page says about one item. */
class PropFacts(val rows: List<PropRow>, val sections: List<PropSection>)

/** The Properties page's values (r11/files.md 1.10, a camera photo: LOW, structure). */
object PropMetrics {
    /** F 1.10.2: the thumbnail, about 95 square, at the left; the name to its right, top-aligned. */
    const val THUMB = 95f
    const val SIDE = 12f
    const val ROW = 28f

    /** F 1.10.3: the label grey, the value white from about mid-width. */
    val LABEL = FilesMetrics.SORT_LABEL
    val RULE = Color(0xFF4A4A4A)
}

/** What Properties reads off a file. Everything here is a read: nothing is written and nothing is scanned. */
object FileFacts {
    /** r11/files.md 1.10.5: "MOV File"; a folder is a "File folder", a name with no extension a "File". */
    fun typeText(name: String, isDirectory: Boolean): String {
        if (isDirectory) return "File folder"
        val ext = FilePaths.splitExtension(name).second.removePrefix(".")
        return if (ext.isEmpty()) "File" else "${ext.uppercase()} File"
    }

    /** "00:07:52" (1.10.5). */
    fun lengthText(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }

    /** "20619kbps" (1.10.5). */
    fun rateText(bitsPerSecond: Long): String = "${bitsPerSecond / 1000}kbps"

    /** Off the main thread: the common rows, then the type's own section. */
    fun read(file: File, isDirectory: Boolean, kind: FileKind, zone: ZoneId): PropFacts {
        val rows = ArrayList<PropRow>()
        rows += PropRow("date", "Date modified:", FileListing.dateText(file.lastModified(), zone))
        rows += PropRow("type", "File type:", typeText(file.name, isDirectory))
        rows += PropRow("size", "File size:", FileListing.sizeText(if (isDirectory) FilePaths.treeBytes(file) else file.length()))
        val sections = when (kind) {
            FileKind.IMAGE -> listOfNotNull(image(file))
            FileKind.VIDEO -> video(file)
            FileKind.AUDIO -> listOfNotNull(audio(file))
            else -> emptyList()
        }
        return PropFacts(rows, sections)
    }

    private fun image(file: File): PropSection? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.path, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return PropSection("image", "Image", listOf(PropRow("dimensions", "Dimensions:", "${bounds.outWidth} x ${bounds.outHeight}")))
    }

    private fun <T> retrieve(file: File, body: (MediaMetadataRetriever) -> T): T? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.path)
            body(r)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun MediaMetadataRetriever.long(key: Int): Long? = extractMetadata(key)?.toLongOrNull()

    /** The frame rate and the audio track's bit rate come from the container's track formats. */
    private fun tracks(file: File): Pair<Int?, Long?> {
        val x = MediaExtractor()
        return try {
            x.setDataSource(file.path)
            var fps: Int? = null
            var audio: Long? = null
            for (i in 0 until x.trackCount) {
                val f = x.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && f.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = f.getInteger(MediaFormat.KEY_FRAME_RATE)
                if (mime.startsWith("audio/") && f.containsKey(MediaFormat.KEY_BIT_RATE)) audio = f.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
            }
            fps to audio
        } catch (e: Exception) {
            null to null
        } finally {
            runCatching { x.release() }
        }
    }

    private fun video(file: File): List<PropSection> {
        val rows = retrieve(file) { r ->
            listOfNotNull(
                r.long(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let { PropRow("length", "Length:", lengthText(it)) },
                r.long(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let { PropRow("width", "Frame width:", it.toString()) },
                r.long(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let { PropRow("height", "Frame height:", it.toString()) },
                r.long(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { PropRow("bitrate", "Total bitrate:", rateText(it)) },
            )
        } ?: return emptyList()
        val (fps, audioRate) = tracks(file)
        val video = rows + listOfNotNull(fps?.let { PropRow("fps", "Frame rate:", "$it frames/second") })
        if (video.isEmpty()) return emptyList()
        return listOfNotNull(
            PropSection("video", "Video", video),
            audioRate?.let { PropSection("audio", "Audio", listOf(PropRow("audio_bitrate", "Bit rate:", rateText(it)))) },
        )
    }

    private fun audio(file: File): PropSection? {
        val rows = retrieve(file) { r ->
            listOfNotNull(
                r.long(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let { PropRow("length", "Length:", lengthText(it)) },
                r.long(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { PropRow("audio_bitrate", "Bit rate:", rateText(it)) },
            )
        }.orEmpty()
        return if (rows.isEmpty()) null else PropSection("audio", "Audio", rows)
    }
}

/**
 * The Properties PAGE (build task 3; r11/files.md 1.10): a page of Files, not a dialog — the location bar's breadcrumb
 * ends in the item's name and there is no app bar. The thumbnail (or type icon) with the name beside it, the grey-label
 * / white-value rows "Date modified:", "File type:", "File size:", then the type's own section — "Video" with its
 * facts for a video, "Image" with its dimensions, "Audio".
 */
@Composable
fun FilesPropertiesPage(state: FilesState, at: FilesLocation.Properties) {
    val file = remember(at) { File(at.path) }
    val kind = remember(at) { FileKind.of(at.name, at.isDirectory) }
    var facts by remember(at) { mutableStateOf<PropFacts?>(null) }
    var gone by remember(at) { mutableStateOf(false) }
    LaunchedEffect(at) {
        val zone = ZoneId.systemDefault()
        val read = withContext(Dispatchers.IO) { if (FilePaths.existsNoFollow(file)) FileFacts.read(file, at.isDirectory, kind, zone) else null }
        facts = read
        gone = read == null
        state.say("properties ${FilePaths.lineText(at.path)}${if (read == null) ": gone" else ""}")
    }
    val density = LocalDensity.current.density
    val entry = remember(at) {
        FileEntry(FileRow(at.name, at.isDirectory, file.length(), file.lastModified()), at.path, at.volumeUuid, "", kind)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PropMetrics.SIDE.dp).testTag("files_properties")) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)) {
            Box(Modifier.size(PropMetrics.THUMB.dp).testTag("files_prop_icon"), contentAlignment = Alignment.Center) {
                FileIconOrThumb(
                    entry, thumbPx = (PropMetrics.THUMB * density).roundToInt(),
                    icon = { FileTypeIcon(kind, Modifier.size(67.dp, 84.dp)) },
                    thumb = { ThumbImage(it, Modifier.fillMaxSize()) },
                )
            }
            BasicText(at.name, Modifier.padding(start = 12.dp).testTag("files_prop_name"), style = ShellType.body.copy(color = Color.White))
        }
        if (gone) {
            BasicText("This item is no longer here", Modifier.testTag("files_error"), style = ShellType.body.copy(color = Color.White))
        }
        facts?.let { f ->
            for (row in f.rows) PropLine(row)
            for (section in f.sections) {
                BasicText(
                    section.title, Modifier.padding(top = 20.dp).testTag("files_prop_section:${section.id}"),
                    style = ShellType.body.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                )
                Box(Modifier.padding(top = 6.dp, bottom = 6.dp).fillMaxWidth().height(1.dp).background(PropMetrics.RULE))
                for (row in section.rows) PropLine(row)
            }
        }
    }
}

@Composable
private fun PropLine(row: PropRow) {
    Row(Modifier.fillMaxWidth().height(PropMetrics.ROW.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(row.label, Modifier.weight(1f).testTag("files_prop_label:${row.id}"), style = ShellType.body.copy(color = PropMetrics.LABEL), maxLines = 1)
        BasicText(row.value, Modifier.weight(1f).testTag("files_prop_value:${row.id}"), style = ShellType.body.copy(color = Color.White), maxLines = 1)
    }
}
