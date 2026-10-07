package app.tileshell.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import app.tileshell.music.catalogue.CatalogueTrack
import app.tileshell.music.catalogue.MusicCatalogueNotice
import app.tileshell.music.radio.RadioCountry
import app.tileshell.music.radio.RadioDirectory
import app.tileshell.music.radio.RadioTag
import app.tileshell.music.radio.Station
import app.tileshell.music.server.ServerAlbum
import app.tileshell.music.server.ServerArtist
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import app.tileshell.video.server.MediaServer
import app.tileshell.video.server.ServerConfig
import app.tileshell.video.server.ServerState
import app.tileshell.video.server.ServerTrack

/**
 * A page of the Music app's streaming side (phase 20 build tasks 6 and 8), opened from the radio pivot and drawn where
 * an album's page is drawn — over the pivot, with Back returning to it.
 */
sealed interface OnlinePage

data object RadioSearchPage : OnlinePage
data object RadioGenresPage : OnlinePage
data object RadioCountriesPage : OnlinePage
data class RadioGenrePage(val tag: RadioTag) : OnlinePage
data class RadioCountryPage(val country: RadioCountry) : OnlinePage
data object CatalogueSearchPage : OnlinePage
data class CatalogueTitlePage(val track: CatalogueTrack) : OnlinePage
data object ServerPage : OnlinePage
data class ServerAlbumPage(val album: ServerAlbum) : OnlinePage
data class ServerArtistPage(val artist: ServerArtist) : OnlinePage

/**
 * Where the streaming side is, and what its pages hold — kept by the ACTIVITY, above the collection, because a tap on
 * a station opens now-playing and the collection leaves the composition while it is up: Back has to return to the
 * genre, the search or the album the tap was made in, not to a rebuilt pivot.
 *
 * Nothing here is written anywhere: it is this run of the activity's and no more (the catalogue's rows are the
 * session's by rule; the directory and the favourites have their own stores).
 */
class OnlineNav {
    val stack = mutableStateListOf<OnlinePage>()
    val top: OnlinePage? get() = stack.lastOrNull()

    fun open(page: OnlinePage) { stack += page }

    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    /** The pivot request this was last drawn under: a NEW one (an App Shortcut) closes whatever page is open. */
    var seenPivotToken: Any? = null

    /** How many of the directory's stations the pivot lists: 100, and 100 more each time its end is reached. */
    var radioShown by mutableIntStateOf(RadioDirectory.LIST)

    /** What a station's row says instead of its genres after a tap that did not play: "can't play this station", … */
    val refused = mutableStateMapOf<String, String>()

    var radioText by mutableStateOf("")
    var radioQuery by mutableStateOf("")

    var catalogueText by mutableStateOf("")
    var catalogueQuery by mutableStateOf("")
    var catalogueTracks by mutableStateOf<List<CatalogueTrack>>(emptyList())
    var catalogueNotice by mutableStateOf(MusicCatalogueNotice.NONE)
    var catalogueLoading by mutableStateOf(false)

    /** The home server as it last answered: null until it has been asked. */
    var server by mutableStateOf<MediaServer?>(null)
    var serverSetUp by mutableStateOf<Boolean?>(null)
    var serverState by mutableStateOf<ServerState?>(null)
    var serverConfig by mutableStateOf<ServerConfig?>(null)
    var serverTracks by mutableStateOf<List<ServerTrack>>(emptyList())
    var serverPivot by mutableIntStateOf(0)
    var serverNotice by mutableStateOf<String?>(null)
}

/** A station the pivot or a result list can play, hold, and say something about. */
internal class StationActions(
    val refusalOf: (Station) -> String?,
    val onTap: (Station) -> Unit,
    val onHold: (Station, Float) -> Unit,
)

/**
 * A streaming-side page's frame, which is the album page's ([MusicCollectionPage]'s detail): a caption line, the
 * page's name in the pivot's own header face, then the page.
 */
@Composable
internal fun OnlineFrame(caption: String, title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalShellColors.current
    Column(Modifier.fillMaxSize().testTag(tag)) {
        BasicText(
            caption,
            style = ShellType.caption.copy(color = colors.subtleText),
            maxLines = 1,
            modifier = Modifier.padding(start = MusicMetrics.SIDE).height(MusicMetrics.TITLE_BLOCK),
        )
        BasicText(
            title,
            style = ShellType.subheader.copy(color = colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = MusicMetrics.SIDE)
                .height(MusicMetrics.PIVOT_BLOCK)
                .testTag("online_title"),
        )
        content()
    }
}
