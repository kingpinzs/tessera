package app.tileshell.recorder

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.bars.hideSystemBars
import app.tileshell.cortana.ui.CortanaUi
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Voice Recorder (phase 15 build task 7): an app in the shell APK with its own task, in the app list like
 * Music (Scope). One page with a record state and a list state, and a playback page (r11/voice-recorder.md
 * 1.1); every page hides Samsung's bars and draws the W10M bars (phase 01's bar rule), the drawn Back is Back for
 * the page and Windows goes Home.
 *
 * The take itself is not here: it runs in `:recorder` ([RecorderService]), which this page binds while it shows
 * and starts with an intent when the record button is tapped. The App Shortcuts' `page` extra ("record" /
 * "list", build task 9) only chooses the state shown; nothing an intent says can start a take (T15-33 (i)).
 *
 * `testTagsAsResourceId` on the root is the QA contract every shell window signs (MusicActivity's lesson).
 */
class RecorderActivity : ComponentActivity() {

    enum class Page { RECORD, LIST }

    /** The page an intent asked for; null leaves the choice to 1.3 (the list when recordings exist). */
    private val requested = mutableStateOf<Page?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        requested.value = pageOf(intent)
        // Read before the first frame, so the first dump already holds the rows (and 1.3's choice has its answer).
        RecorderLibrary.refresh(this, "open")
        Diagnostics.add("recorder", "RecorderActivity created page=${intent?.getStringExtra(EXTRA_PAGE) ?: "auto"}")
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    RecorderScreen(
                        requestedPage = requested.value,
                        onPageShown = { requested.value = null },
                        onFinish = { finish() },
                        onHome = { goHome() },
                        onShare = { share(it) },
                        onOpenAppSettings = { openAppSettings() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pageOf(intent)?.let { requested.value = it }
    }

    override fun onStart() {
        super.onStart()
        RecorderClient.bind(this)
        RecorderLibrary.start(this)
    }

    override fun onStop() {
        RecorderLibrary.stop(this)
        RecorderClient.unbind(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onDestroy() {
        // The player and its `recorder` session live as long as this window; a take in `:recorder` is untouched.
        RecorderPlayer.release()
        super.onDestroy()
    }

    private fun pageOf(intent: Intent?): Page? = when (intent?.getStringExtra(EXTRA_PAGE)) {
        PAGE_RECORD -> Page.RECORD
        PAGE_LIST -> Page.LIST
        else -> null
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", packageName, null)))
    }

    /**
     * Share (1.9, E16): Android's share sheet — W10M's "system share page" — with `ACTION_SEND audio/mp4`, the
     * recording's MediaStore content URI and a read grant for the app chosen. Other apps' recordings share the
     * same way (T15-3); the grant is only ever to read, and only that one URI (T15-33 (f)).
     */
    private fun share(recording: Recording) {
        val uri = RecordingStore.uriOf(recording.id)
        val send = Intent(Intent.ACTION_SEND)
            .setType(RecorderAudio.FILE_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(recording.name, uri)
        val own = recording.isOwn(packageName)
        Diagnostics.add("recorder", "share ${recording.id} type=${RecorderAudio.FILE_MIME} uri=$uri (${if (own) "own" else "another app's"})")
        runCatching { startActivity(Intent.createChooser(send, null)) }
            .onFailure { Diagnostics.add("recorder", "share ${recording.id} failed: $it") }
    }

    companion object {
        /** The shortcuts' extra — the key SettingsActivity.EXTRA_PAGE already uses (build task 9). */
        const val EXTRA_PAGE = "page"
        const val PAGE_RECORD = "record"
        const val PAGE_LIST = "list"
    }
}

/** A dialog open over the page (R7 §1.3.9's form). */
private sealed interface RecDialog {
    data class Delete(val recording: Recording) : RecDialog
    data class Rename(val recording: Recording) : RecDialog
}

@Composable
private fun RecorderScreen(
    requestedPage: RecorderActivity.Page?,
    onPageShown: () -> Unit,
    onFinish: () -> Unit,
    onHome: () -> Unit,
    onShare: (Recording) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val scope = rememberCoroutineScope()
    val shell = context.packageName
    val take by RecorderClient.take.collectAsState()
    val snapshot by RecorderLibrary.snapshot.collectAsState()

    var page by remember { mutableStateOf(if (snapshot.recordings.isEmpty()) RecorderActivity.Page.RECORD else RecorderActivity.Page.LIST) }
    LaunchedEffect(requestedPage) {
        if (requestedPage != null) {
            page = requestedPage
            onPageShown()
        }
    }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var trimming by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ShowingKind.ALL) }
    var flyout by remember { mutableStateOf<FlyoutState?>(null) }
    var dialog by remember { mutableStateOf<RecDialog?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var cutT0 by remember { mutableStateOf<Long?>(null) }
    var micGranted by remember { mutableStateOf(granted(context, Manifest.permission.RECORD_AUDIO)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        micGranted = granted(context, Manifest.permission.RECORD_AUDIO)
        if (RecordingStore.canSeeOthers(context) != snapshot.othersVisible) {
            scope.launch(Dispatchers.IO) { RecorderLibrary.refresh(context, "access changed while away") }
        }
    }
    LaunchedEffect(Unit) { RecorderClient.notices.collect { notice = it } }
    LaunchedEffect(Unit) {
        RecorderClient.saved.collect { (id, name) ->
            // 1.2: after Stop the page becomes the list, with the new row at the top.
            Diagnostics.add("recorder", "saved $id ($name): showing the list")
            page = RecorderActivity.Page.LIST
            withContext(Dispatchers.IO) { RecorderLibrary.refresh(context, "take saved") }
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MS)
            notice = null
        }
    }

    val micGrant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        micGranted = ok
        Diagnostics.add("recorder", "microphone permission request: ${if (ok) "granted" else "denied"}")
        if (!ok && !(context as android.app.Activity).shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) onOpenAppSettings()
    }
    val audioGrant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Diagnostics.add("recorder", "audio permission request: ${if (ok) "granted" else "denied"}")
        if (!ok && !(context as android.app.Activity).shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO)) onOpenAppSettings()
        scope.launch(Dispatchers.IO) { RecorderLibrary.refresh(context, "permission ${if (ok) "granted" else "denied"}") }
    }

    val playing = playingId?.let { id -> snapshot.recordings.firstOrNull { it.id == id } }
    // A recording deleted outside the app while its page shows (the ContentObserver's edge case) closes the page.
    LaunchedEffect(playingId, playing) { if (playingId != null && playing == null && snapshot.recordings.isNotEmpty()) { playingId = null; trimming = false } }

    fun io(block: suspend () -> Unit) = scope.launch(Dispatchers.IO) { block() }

    fun back() {
        when {
            dialog != null -> dialog = null
            flyout != null -> flyout = null
            about -> about = false
            trimming -> trimming = false
            playingId != null -> playingId = null
            take.active -> onFinish() // the take goes on in the background (E15)
            page == RecorderActivity.Page.RECORD && snapshot.recordings.isNotEmpty() -> page = RecorderActivity.Page.LIST
            else -> onFinish()
        }
    }
    BackHandler(enabled = true) { back() }

    fun settingsFlyout(bottomEpx: Float) = FlyoutState(
        tag = "rec_more_menu",
        items = listOf(FlyoutItem("Settings", "rec_more_menu:settings") { flyout = null; about = true }),
        centreXEpx = 0f, topEpx = null, bottomEpx = bottomEpx, rightAlign = true,
    )

    Box(Modifier.fillMaxSize().background(colors.background).testTag("rec_root")) {
        Column(Modifier.fillMaxSize()) {
            W10mStatusBar()
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val contentH = maxHeight.value
                val open = playing
                when {
                    about -> RecorderAboutPage()
                    open != null && trimming -> TrimPage(
                        recording = open,
                        player = rememberPlayer(open, autoplay = false),
                        onSave = { start, end ->
                            io {
                                val result = RecorderEdits.trim(context, open, start, end)
                                // The list holds the new row before the page turns to it (the observer's refresh comes later).
                                if (result is RecorderEdits.Outcome.Done) RecorderLibrary.refresh(context, "trimmed")
                                withContext(Dispatchers.Main) {
                                    when (result) {
                                        is RecorderEdits.Outcome.Done -> { trimming = false; playingId = result.id }
                                        is RecorderEdits.Outcome.Refused -> notice = result.notice
                                        RecorderEdits.Outcome.Unchanged -> trimming = false
                                    }
                                }
                            }
                        },
                        onCancel = { trimming = false },
                    )
                    open != null -> {
                        val caps = RecordingCaps.of(open.ownerPackage, shell)
                        var markers by remember(open.id) { mutableStateOf(if (caps.markers) RecordingMetaStore.get(context, open.id)?.markers.orEmpty() else emptyList()) }
                        PlaybackPage(
                            recording = open,
                            caps = caps,
                            markers = markers,
                            player = rememberPlayer(open, autoplay = true),
                            onFlag = { at ->
                                io {
                                    val now = RecordingMetaStore.addMarker(
                                        context, open.id, at,
                                        RecordingMetaStore.Meta(open.recordedAtMs, open.durationMs, emptyList()),
                                    )
                                    Diagnostics.add("recorder", "marker ${open.name} at=$at")
                                    withContext(Dispatchers.Main) { markers = now }
                                }
                            },
                            onShare = { onShare(open) },
                            onTrim = { trimming = true },
                            onDelete = { dialog = RecDialog.Delete(open) },
                            onRename = { dialog = RecDialog.Rename(open) },
                            onMore = { flyout = settingsFlyout(contentH - CortanaUi.APPBAR_EPX) },
                        )
                    }
                    take.active || page == RecorderActivity.Page.RECORD -> {
                        RecordState(
                            take = take,
                            hasRecordings = snapshot.recordings.isNotEmpty(),
                            micGranted = micGranted,
                            onRecordTap = {
                                if (take.active) {
                                    RecorderClient.stop()
                                } else if (micGranted) {
                                    cutT0 = SystemClock.uptimeMillis()
                                    RecorderClient.start(context)
                                }
                            },
                            onPauseTap = { if (take.phase == RecorderState.PAUSED) RecorderClient.resume() else RecorderClient.pause() },
                            onFlagTap = { RecorderClient.flag() },
                            onMore = { flyout = settingsFlyout(contentH - RecorderMetrics.MINIMAL_BAR) },
                            onGrantMic = { micGrant.launch(Manifest.permission.RECORD_AUDIO) },
                        )
                    }
                    else -> {
                        val shown = remember(snapshot, query, kind) { RecordingFilter.apply(snapshot.recordings, kind, query, shell) }
                        ListState(
                            snapshot = snapshot,
                            shown = shown,
                            query = query,
                            kind = kind,
                            onQuery = { query = it },
                            onShowing = { x, bottom ->
                                flyout = FlyoutState(
                                    tag = "rec_filter_menu",
                                    items = ShowingKind.entries.map { k ->
                                        FlyoutItem(k.label, "rec_filter_choice:${k.id}") {
                                            kind = k
                                            flyout = null
                                            Diagnostics.add("recorder", "showing ${k.id}")
                                        }
                                    },
                                    centreXEpx = x, topEpx = bottom, bottomEpx = null,
                                )
                            },
                            onOpen = { r ->
                                trimming = false
                                playingId = r.id
                            },
                            onHold = { r, x, _, bottom ->
                                val caps = RecordingCaps.of(r.ownerPackage, shell)
                                // 1.6: Share / Delete / Rename (W10M's, less "Open file location"); another app's: Share (T15-3).
                                val items = buildList {
                                    add(FlyoutItem("Share", "rec_menu:share") { flyout = null; onShare(r) })
                                    if (caps.delete) add(FlyoutItem("Delete", "rec_menu:delete") { flyout = null; dialog = RecDialog.Delete(r) })
                                    if (caps.rename) add(FlyoutItem("Rename", "rec_menu:rename") { flyout = null; dialog = RecDialog.Rename(r) })
                                }
                                flyout = FlyoutState("rec_menu", items, x, bottom, null)
                            },
                            onRecord = {
                                cutT0 = SystemClock.uptimeMillis()
                                RecorderClient.start(context)
                            },
                            micGranted = micGranted,
                            onMore = { flyout = settingsFlyout(contentH - RecorderMetrics.MINIMAL_BAR) },
                            onGrantAudio = { audioGrant.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                        )
                    }
                }
                flyout?.let { f ->
                    RecorderFlyout(
                        tag = f.tag,
                        items = f.items,
                        centreXEpx = f.centreXEpx,
                        onDismiss = { flyout = null },
                        topEpx = f.topEpx,
                        bottomEpx = f.bottomEpx,
                        rightAlign = f.rightAlign,
                    )
                }
                notice?.let { NoticeBand(it) }
            }
            W10mNavBar(onBack = { back() }, onWindows = onHome)
        }
        RecordCutClock(cutT0, take.phase) { cutT0 = null }
        when (val d = dialog) {
            is RecDialog.Delete -> RecorderDialog(
                tag = "rec_delete_dialog",
                title = "Delete this recording?",
                confirmLabel = "Delete",
                confirmTag = "rec_delete_confirm",
                cancelTag = "rec_delete_cancel",
                onConfirm = {
                    dialog = null
                    io {
                        val ok = RecorderEdits.delete(context, d.recording)
                        if (ok) RecorderLibrary.refresh(context, "deleted")
                        withContext(Dispatchers.Main) {
                            if (ok) { if (playingId == d.recording.id) { playingId = null; trimming = false } }
                            else notice = "The recording couldn't be deleted."
                        }
                    }
                },
                onCancel = { dialog = null },
            ) {
                BasicText(d.recording.name, style = ShellType.body.copy(color = RecorderMetrics.SECONDARY))
            }
            is RecDialog.Rename -> RenameDialog(
                recording = d.recording,
                onDone = { dialog = null },
                onRename = { name, fail ->
                    io {
                        val result = RecorderEdits.rename(context, d.recording, name)
                        if (result != null) RecorderLibrary.refresh(context, "renamed")
                        withContext(Dispatchers.Main) {
                            if (result == null) fail("The recording couldn't be renamed.") else dialog = null
                        }
                    }
                },
            )
            null -> Unit
        }
    }
}

/**
 * The process's one player, loaded with the recording this page shows for as long as the page shows. The load
 * happens in the effect, not the composition: a page leaving disposes before the page arriving loads, so the
 * playback and trim pages hand the same player over cleanly.
 */
@Composable
private fun rememberPlayer(recording: Recording, autoplay: Boolean): RecorderPlayer {
    val context = LocalContext.current
    val player = remember { RecorderPlayer.get(context) }
    DisposableEffect(recording.id, autoplay) {
        player.load(recording)
        // 1.5 / E16: a row tap opens the playback page playing; the trim page opens still.
        if (autoplay) player.play() else player.pause()
        onDispose { player.stop() }
    }
    return player
}

/** U4: the rename dialog — a 32-epx text box (3.1's) holding the name, Rename / Cancel; a refused name says why. */
@Composable
private fun RenameDialog(recording: Recording, onDone: () -> Unit, onRename: (String, (String) -> Unit) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(recording.name, TextRange(0, recording.name.length))) }
    var refusal by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun submit() {
        when (val check = RenameRule.check(value.text)) {
            is RenameRule.Result.Refused -> {
                refusal = check.notice
                Diagnostics.add("recorder", "rename ${recording.id} refused: ${check.notice}")
            }
            is RenameRule.Result.Ok -> onRename(check.name) { refusal = it }
        }
    }
    RecorderDialog(
        tag = "rec_rename_dialog",
        title = "Rename",
        confirmLabel = "Rename",
        confirmTag = "rec_rename_ok",
        cancelTag = "rec_rename_cancel",
        onConfirm = { submit() },
        onCancel = onDone,
    ) {
        Column {
            BasicTextField(
                value = value,
                onValueChange = { value = it; refusal = null },
                singleLine = true,
                textStyle = ShellType.body.copy(color = androidx.compose.ui.graphics.Color.White),
                cursorBrush = SolidColor(LocalShellColors.current.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RecorderMetrics.SEARCH_EPX.dp)
                    .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .focusRequester(focus)
                    .testTag("rec_rename_field"),
            )
            refusal?.let {
                BasicText(it, style = ShellType.caption.copy(color = androidx.compose.ui.graphics.Color.White), modifier = Modifier.padding(top = 4.dp).testTag("rec_rename_notice"))
            }
        }
    }
}

private fun granted(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
