package app.tileshell.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.music.server.ServerLibrary
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import app.tileshell.video.AddServerForm
import app.tileshell.video.VideoCalls
import app.tileshell.video.server.MediaServer
import app.tileshell.video.server.ServerConfig
import app.tileshell.video.server.ServerRules
import app.tileshell.video.server.ServerState
import app.tileshell.video.server.ServerTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The diagnostics tag the Music app's server lines are written under: the MAIN ring's `[music] server <host>: …`. */
private const val TAG = "music"

/** The home server's three groupings, in the collection's own order and lowercase header form. */
private val SERVER_PIVOTS = listOf("albums", "artists", "songs")

/**
 * The home server's music (phase 20 build task 8; Y7, a P4 design): the user's Jellyfin library, listed as albums /
 * artists / songs in the four built pivots' rows under the server's name, and played in the shell's own player.
 *
 * The server is phase 17's — the same connection and the same token store — so one signed in for Movies & TV is
 * signed in here, and with none set up this page is phase 17's own sign-in form ([AddServerForm], its rules and its
 * off-home-network ask), on the black it was drawn for. TRUST-TOUCHING: nothing here holds, shows or logs a credential;
 * a song's address carries no token (`ServerTrackItem`), and a call that throws is named by its class alone.
 *
 * Every read of the server blocks and runs off the main thread. The listing last answered is kept for this run of the
 * activity, so a server that stops answering leaves its songs on the page under "Can't reach your media server".
 *
 * Tags: `server_page`, `server_name`, `server_notice`, `server_pivot_header:albums|artists|songs`, `server_pivot`,
 * `server_album:<id>`, `server_artist:<name>`, `server_song:<id>` (each with `music_pri:` / `music_sub:`),
 * `server_page:album`, `server_page:artist`, and the sign-in form's own (`server_host`, `server_user`,
 * `server_password`, `server_connect`, `server_error`, `server_insecure…`).
 */
@Composable
internal fun ServerMusic(nav: OnlineNav, onPlay: (List<ServerTrack>, Int) -> Unit) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val scope = rememberCoroutineScope()
    var reloads by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloads) {
        class Read(val server: MediaServer, val setUp: Boolean, val config: ServerConfig?, val state: ServerState?, val tracks: List<ServerTrack>)
        val read = withContext(Dispatchers.IO) {
            VideoCalls.guarded<Read?>("server music", null, { Diagnostics.add(TAG, it) }) {
                val server = nav.server ?: MediaServer(context, TAG)
                if (!server.isSetUp()) Read(server, false, null, null, emptyList())
                else {
                    val config = server.config()
                    val (state, tracks) = server.music()
                    Read(server, true, config, state, tracks)
                }
            }
        }
        if (read == null) {
            nav.serverState = ServerState.UNREACHABLE
            nav.serverSetUp = nav.serverSetUp ?: true
            return@LaunchedEffect
        }
        nav.server = read.server
        nav.serverSetUp = read.setUp
        nav.serverConfig = read.config
        nav.serverState = read.state
        // A listing that did not arrive keeps the last one that did.
        if (read.state == ServerState.CONNECTED || !read.setUp) nav.serverTracks = read.tracks
    }

    val setUp = nav.serverSetUp ?: return
    val state = nav.serverState
    if (!setUp || state == ServerState.UNAUTHORISED) {
        // Phase 17's form is white on black (R6 §3.4.2's sign-in geometry), whatever the theme: it keeps its ground.
        Column(
            Modifier.fillMaxSize().background(Color.Black).focusable().verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 16.dp).testTag("server_page"),
        ) {
            if (setUp) {
                BasicText("Sign in again", Modifier.padding(bottom = 16.dp).testTag("server_notice"), style = ShellType.subtitle.copy(color = Color.White))
                AddServerForm(ServerRules.signInAgainPrefill(nav.serverConfig), nav.serverConfig?.userName.orEmpty(), null, tag = TAG) { reloads++ }
            } else {
                BasicText("Add a server", Modifier.padding(bottom = 4.dp), style = ShellType.subtitle.copy(color = Color.White))
                BasicText(
                    "Your own Jellyfin server. The password is used once to sign in and is not kept. A server added in Movies & TV is this one.",
                    Modifier.padding(bottom = 16.dp), style = ShellType.body.copy(color = Color(0x99FFFFFF)),
                )
                AddServerForm("", "", null, tag = TAG) { reloads++ }
            }
        }
        return
    }

    val tracks = nav.serverTracks
    val albums = remember(tracks) { ServerLibrary.albums(tracks) }
    val artists = remember(tracks) { ServerLibrary.artists(tracks) }
    val songs = remember(tracks) { ServerLibrary.songs(tracks) }
    val pager = rememberPagerState(initialPage = nav.serverPivot) { SERVER_PIVOTS.size }
    LaunchedEffect(pager.settledPage) { nav.serverPivot = pager.settledPage }
    Column(Modifier.fillMaxSize().testTag("server_page")) {
        // The server-name line, where the collection has its app title.
        BasicText(
            nav.serverConfig?.label.orEmpty(),
            style = ShellType.caption.copy(color = colors.subtleText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = MusicMetrics.SIDE).height(MusicMetrics.TITLE_BLOCK).testTag("server_name"),
        )
        PivotHeaders(SERVER_PIVOTS, SERVER_PIVOTS.map { "server_pivot_header:$it" }, pager.currentPage + pager.currentPageOffsetFraction) { page ->
            scope.launch { pager.animateScrollToPage(page, animationSpec = androidx.compose.animation.core.tween(app.tileshell.ui.motion.Motion.PIVOT_SETTLE_MS)) }
        }
        val notice = when {
            state == ServerState.UNREACHABLE -> ServerRules.TEXT_UNREACHABLE
            nav.serverNotice != null -> nav.serverNotice
            tracks.isEmpty() -> "There is no music on this server."
            else -> null
        }
        notice?.let {
            BasicText(
                it,
                style = ShellType.body.copy(color = colors.subtleText),
                modifier = Modifier.padding(start = MusicMetrics.SIDE, end = MusicMetrics.SIDE, bottom = 8.dp).testTag("server_notice"),
            )
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().testTag("server_pivot")) { index ->
            when (index) {
                0 -> LazyColumn(Modifier.fillMaxSize().testTag("server_list:albums")) {
                    items(albums.size, key = { albums[it].id }) { i ->
                        val album = albums[i]
                        TwoLineRow(tag = "server_album:${album.id}", primary = album.name, secondary = album.artist, onTap = { nav.open(ServerAlbumPage(album)) }) {
                            // The server's own pictures are not fetched: an album draws the collection's placeholder.
                            AlbumArt(null)
                        }
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().testTag("server_list:artists")) {
                    items(artists.size, key = { artists[it].name }) { i ->
                        val artist = artists[i]
                        TwoLineRow(tag = "server_artist:${artist.name}", primary = artist.name, secondary = ServerLibrary.countText(artist.tracks.size), onTap = { nav.open(ServerArtistPage(artist)) }) {
                            GlyphSquare(Glyph.PEOPLE)
                        }
                    }
                }
                else -> ServerSongs(songs, Modifier.testTag("server_list:songs"), onPlay)
            }
        }
    }
}

/** An album or an artist of the server, opened from its pivot: its songs in the order they play ([OnlineFrame], the album page's form). */
@Composable
internal fun ServerSongsPage(caption: String, title: String, tag: String, tracks: List<ServerTrack>, notice: String?, onPlay: (List<ServerTrack>, Int) -> Unit) {
    OnlineFrame(caption = caption, title = title, tag = tag) {
        notice?.let {
            BasicText(
                it,
                style = ShellType.body.copy(color = LocalShellColors.current.subtleText),
                modifier = Modifier.padding(start = MusicMetrics.SIDE, end = MusicMetrics.SIDE, bottom = 8.dp).testTag("server_notice"),
            )
        }
        ServerSongs(tracks, Modifier, onPlay)
    }
}

/** Song rows: a tap plays the LISTED songs from the one tapped, as a tap on the songs pivot plays that pivot. */
@Composable
private fun ServerSongs(tracks: List<ServerTrack>, modifier: Modifier, onPlay: (List<ServerTrack>, Int) -> Unit) {
    val colors = LocalShellColors.current
    LazyColumn(modifier.fillMaxSize()) {
        items(tracks.size, key = { tracks[it].id }) { i ->
            val track = tracks[i]
            val playing = MusicPlayer.nowPlayingId == MusicLive.serverId(track.id)
            TwoLineRow(
                tag = "server_song:${track.id}",
                primary = track.title,
                secondary = ServerLibrary.songLine(track),
                onTap = { onPlay(tracks, i) },
                primaryColor = if (playing) colors.accent else null,
            ) {
                val ink = if (playing) colors.accent else colors.subtleText
                Canvas(Modifier.size(MusicMetrics.ART)) { drawTransportMark(playing && MusicPlayer.isPlaying, ink) }
            }
        }
    }
}
