package app.tileshell.video

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OutlinedField
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import app.tileshell.video.server.MediaServer
import app.tileshell.video.server.PromptAnswer
import app.tileshell.video.server.ServerAddress
import app.tileshell.video.server.ServerConfig
import app.tileshell.video.server.ServerItem
import app.tileshell.video.server.ServerRules
import app.tileshell.video.server.ServerState
import app.tileshell.video.server.SignInAction
import kotlinx.coroutines.launch

/** Movies & TV's media server setting: "Add a server", or the server that is set up and its removal. */
data object ServerSub : HubSub {
    override val title = "Media server"
}

/** Y10: R6 §3.4.2's sign-in geometry — outlined fields 43 epx tall at a 53.6-epx pitch. */
private const val FIELD_HEIGHT = 43f
private const val FIELD_GAP = 10.6f

/**
 * "Add a server" (build task 13; Y10): host, user, password, Connect. TRUST-TOUCHING.
 *
 * The password field is a secret one; its value is handed to the one sign-in call and dropped — the field is emptied
 * as the request starts and on Cancel, and nothing here stores or logs it. A plain-http address that is not on the
 * home network is ASKED about first (C-16 (5)): the prompt is shown, the line `insecure, asked` is written, and no
 * request of any kind leaves the phone until Continue.
 *
 * Tags: `server_host`, `server_user`, `server_password`, `server_connect`, `server_error`, `server_insecure`,
 * `server_insecure_continue`, `server_insecure_cancel`.
 */
@Composable
fun AddServerForm(prefillHost: String, prefillUser: String, firstError: String?, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var host by remember { mutableStateOf(prefillHost) }
    var user by remember { mutableStateOf(prefillUser) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(firstError) }
    var asking by remember { mutableStateOf<ServerAddress?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun signIn(address: ServerAddress) {
        val secret = password
        password = ""
        busy = true
        error = null
        scope.launch {
            val state = VideoCalls.io("server sign-in", ServerState.UNREACHABLE) { MediaServer(context).connect(address, user.trim(), secret) }
            busy = false
            when (state) {
                ServerState.CONNECTED -> onConnected()
                ServerState.UNAUTHORISED -> error = ServerRules.TEXT_BAD_PASSWORD
                ServerState.UNREACHABLE -> error = ServerRules.TEXT_UNREACHABLE
            }
        }
    }

    fun submit() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        val address = ServerRules.parse(host)
        if (address == null) { error = "That isn't a server address"; return }
        if (user.isBlank()) { error = "Enter the user name"; return }
        when (ServerRules.signInAction(address, answer = null)) {
            SignInAction.ASK -> {
                // Asked first: nothing is sent — not the sign-in, not a probe — until Continue.
                Diagnostics.add("video", ServerRules.line(address.label, "insecure, asked"))
                asking = address
            }
            SignInAction.SEND -> signIn(address)
            SignInAction.NOTHING -> Unit
        }
    }

    /** The prompt's two buttons: only an answer the rule turns into SEND makes the request. */
    fun answer(address: ServerAddress, answer: PromptAnswer) {
        asking = null
        if (ServerRules.signInAction(address, answer) == SignInAction.SEND) signIn(address) else password = ""
    }

    Column(Modifier.fillMaxWidth()) {
        val waiting = asking
        if (waiting != null) {
            BasicText(ServerRules.TEXT_INSECURE, Modifier.testTag("server_insecure"), style = ShellType.subtitle.copy(color = Color.White))
            BasicText(waiting.label, Modifier.padding(top = 8.dp), style = ShellType.body.copy(color = LocalShellColors.current.subtleText))
            Row(Modifier.padding(top = 16.dp)) {
                HubButton("Continue", "server_insecure_continue") { answer(waiting, PromptAnswer.CONTINUE) }
                Spacer(Modifier.width(12.dp))
                HubButton("Cancel", "server_insecure_cancel") { answer(waiting, PromptAnswer.CANCEL) }
            }
            return@Column
        }
        BasicText("Server address", Modifier.padding(bottom = 6.dp), style = ShellType.body.copy(color = Color.White))
        OutlinedField(host, { host = it }, "server_host", Modifier.fillMaxWidth(), height = FIELD_HEIGHT.dp, maxLength = 300, placeholder = "192.168.1.10:8096", keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
        BasicText("User name", Modifier.padding(top = FIELD_GAP.dp, bottom = 6.dp), style = ShellType.body.copy(color = Color.White))
        OutlinedField(user, { user = it }, "server_user", Modifier.fillMaxWidth(), height = FIELD_HEIGHT.dp, maxLength = 200, imeAction = ImeAction.Next)
        BasicText("Password", Modifier.padding(top = FIELD_GAP.dp, bottom = 6.dp), style = ShellType.body.copy(color = Color.White))
        OutlinedField(password, { password = it }, "server_password", Modifier.fillMaxWidth(), height = FIELD_HEIGHT.dp, maxLength = 500, onImeAction = { submit() }, secret = true)
        error?.let { BasicText(it, Modifier.padding(top = 12.dp).testTag("server_error"), style = ShellType.body.copy(color = Color.White)) }
        Row(Modifier.padding(top = 16.dp)) {
            HubButton(if (busy) "Connecting…" else "Connect", "server_connect", enabled = !busy && host.isNotBlank()) { submit() }
        }
    }
}

/** The setting's page: the form while no server is set up, else the server and "Remove this server". Tags: `server_current`, `server_remove`. */
@Composable
fun ServerSettingsPage(nav: VideoNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<ServerConfig?>(null) }
    var loaded by remember { mutableStateOf(false) }
    suspend fun reload() {
        val (setUp, cfg) = VideoCalls.io("server read", false to null) { MediaServer(context).let { it.isSetUp() to it.config() } }
        nav.serverSetUp = setUp
        config = if (setUp) cfg else null
        loaded = true
    }
    LaunchedEffect(nav.resumes) { reload() }
    Column(Modifier.fillMaxSize().focusable().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 16.dp).testTag("hub_page:server")) {
        if (!loaded) return@Column
        val cfg = config
        if (cfg == null) {
            BasicText("Add a server", Modifier.padding(bottom = 4.dp), style = ShellType.subtitle.copy(color = Color.White))
            BasicText("Your own Jellyfin server. The password is used once to sign in and is not kept.", Modifier.padding(bottom = 16.dp), style = ShellType.body.copy(color = LocalShellColors.current.subtleText))
            AddServerForm("", "", null) { nav.serverSetUp = true; nav.go(HubPage.MEDIA_SERVER) }
        } else {
            BasicText("${cfg.label} — signed in as ${cfg.userName}", Modifier.testTag("server_current"), style = ShellType.body.copy(color = Color.White))
            Row(Modifier.padding(top = 16.dp)) {
                HubButton("Remove this server", "server_remove") {
                    scope.launch {
                        VideoCalls.io("server remove", Unit) { MediaServer(context).remove() }
                        reload()
                    }
                }
            }
        }
    }
}

/**
 * The Media server page (Y10): the server's videos in My videos' tile-grid form. Unreachable → "Can't reach your media
 * server" (My videos and the player are untouched); a token the server no longer accepts → the sign-in form again,
 * the host prefilled.
 *
 * Tags: `server_item:<id>`, `server_caption:<id>`, `server_notice`, and the form's.
 */
@Composable
fun MediaServerPage(nav: VideoNav, activity: ComponentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ServerState?>(null) }
    var items by remember { mutableStateOf<List<ServerItem>>(emptyList()) }
    var config by remember { mutableStateOf<ServerConfig?>(null) }
    var reloads by remember { mutableStateOf(0) }
    LaunchedEffect(nav.resumes, reloads) {
        val (cfg, result) = VideoCalls.io("server library", null to (ServerState.UNREACHABLE to emptyList())) { MediaServer(context).let { it.config() to it.library() } }
        config = cfg
        items = result.second
        state = result.first
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("hub_page:mediaserver")) {
        when (state) {
            null -> Unit
            ServerState.UNREACHABLE -> BasicText(
                ServerRules.TEXT_UNREACHABLE, Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp).testTag("server_notice"),
                style = ShellType.subtitle.copy(color = Color.White),
            )
            ServerState.UNAUTHORISED -> Column(Modifier.fillMaxSize().focusable().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 16.dp)) {
                BasicText("Sign in again", Modifier.padding(bottom = 16.dp).testTag("server_notice"), style = ShellType.subtitle.copy(color = Color.White))
                AddServerForm(ServerRules.signInAgainPrefill(config), config?.userName.orEmpty(), null) { reloads++ }
            }
            ServerState.CONNECTED -> if (items.isEmpty()) {
                BasicText("There are no videos on this server.", Modifier.padding(start = 12.dp, top = 20.dp).testTag("server_notice"), style = ShellType.subtitle.copy(color = Color.White))
            } else {
                val columns = VideoGroups.columns(maxWidth.value)
                val rows = items.chunked(columns)
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "header") {
                        Box(Modifier.fillMaxWidth().height(MyVideosMetrics.HEADER_BLOCK.dp)) {
                            BasicText(
                                config?.label.orEmpty(),
                                Modifier.padding(start = VideoGroups.MARGIN.dp, top = CapMetrics.topPaddingForCapTop(MyVideosMetrics.HEADER_CAP_TOP, 15f).dp).testTag("server_group"),
                                style = ShellType.base.copy(color = LocalShellColors.current.accent), maxLines = 1,
                            )
                        }
                    }
                    items(rows.size, key = { rows[it].first().id }) { index ->
                        Row(Modifier.fillMaxWidth().height(MyVideosMetrics.ROW_PITCH.dp).padding(start = VideoGroups.MARGIN.dp)) {
                            for ((i, item) in rows[index].withIndex()) {
                                if (i > 0) Spacer(Modifier.width((VideoGroups.PITCH - VideoGroups.TILE).dp))
                                ServerTile(item) {
                                    // The address is built on the sealed server's (a store read): off the main thread.
                                    scope.launch {
                                        val url = VideoCalls.io<String?>("server stream address", null) { MediaServer(context).streamUrl(item) }
                                        if (url != null) playFromServer(activity, item, url)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerTile(item: ServerItem, onTap: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.width(VideoGroups.TILE.dp).pointerInput(item.id) { detectTapGestures { onTap() } }) {
        Box(Modifier.size(VideoGroups.TILE.dp).background(Color.Black).testTag("server_item:${item.id}"), contentAlignment = Alignment.Center) {
            val thumb by produceState<ImageBitmap?>(null, item.id) {
                value = VideoCalls.io("server picture", null) { MediaServer(context).thumbnail(item)?.asImageBitmap() }
            }
            val t = thumb
            if (t != null) Image(t, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else BasicText(Glyph.VIDEO, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 36.sp, lineHeight = 36.sp, color = Color.White.copy(alpha = 0.4f)))
        }
        BasicText(
            item.name,
            Modifier.padding(top = (MyVideosMetrics.CAPTION_BASELINE - MyVideosMetrics.CAPTION_FONT * CapMetrics.ASCENT_RATIO).dp)
                .width(MyVideosMetrics.CAPTION_WIDTH.dp).clipToBounds().testTag("server_caption:${item.id}"),
            style = ShellType.body.copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Clip,
        )
    }
}

/** Direct play in the shared player: the address carries no token (the player's data source adds it as it opens). */
private fun playFromServer(activity: ComponentActivity, item: ServerItem, url: String) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setClass(activity, PlayerActivity::class.java)
        .setDataAndType(Uri.parse(url), "video/*")
        .putExtra(PlayerActivity.EXTRA_TITLE, item.name)
    activity.startActivity(intent, ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle())
}
