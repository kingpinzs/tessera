package app.tileshell.files

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.onboarding.Checklist
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.tokens.ShellType

/**
 * What an intent asked Files to show (r3 D12): the extras of [FilesIntents], as they came. They only ever choose what
 * is shown; [FilesState.open] decides whether a path is one it may open ([FilesNav.resolve]).
 */
class FilesRequest(val page: String?, val path: String?, val name: String? = null, val from: String? = null)

/**
 * Files (phase 18 build task 1): W10M's File Explorer as an app in the shell APK with its own task, in the app list
 * like Music and Voice Recorder. ONE activity for every page (r3 D12), singleTask, so a second launch re-routes the
 * running app through [onNewIntent]. Every page hides Android's bars and draws the W10M bars (phase 01's bar rule);
 * the drawn Back is Back for the page and Windows goes Home.
 *
 * Files browses by path under All-files access (Q1 A). The grant is read at every start and again on every resume,
 * because it is turned on and off in Android's Settings while Files is away; without it the page says so, names the
 * Setup checklist and offers the same Settings page from here.
 *
 * `testTagsAsResourceId` on the root is the QA contract every shell window signs (MusicActivity's lesson).
 */
class FilesActivity : ComponentActivity() {

    /** Where Files is and what it shows: made once, kept for the activity's life (a warm return shows the page left). */
    private lateinit var state: FilesState

    /**
     * A launch not yet acted on, and whether it is the cold one. Without All-files access no folder can be checked, so
     * the request waits for the grant; with it, it is taken at once.
     */
    private var pending: Pair<FilesRequest, Boolean>? = null

    /** All-files access as last read; null before the first read. */
    private val access = mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        // ShellApp started the tracker with the process; a second call is a no-op, and covers a process whose launcher
        // start-up is still waiting for the first unlock.
        FileVolumes.start(this)
        // Build tasks 3, 8 and 9 are FilesBehaviour; tasks 4 and 10 (open-with, Recent's menu) join it as FilesOpener.
        state = FilesState(this, FilesBehaviour(this, FilesOpener(this)))
        val request = requestOf(intent)
        pending = request to true
        Diagnostics.add("files", "FilesActivity created page=${request.page ?: "none"}")
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    FilesScreen(
                        granted = access.value == true,
                        state = state,
                        onGrant = { openAllFilesAccess() },
                        // Back is the page's first (Y3); with nothing left it leaves the app.
                        onBack = { if (access.value != true || !state.back()) leave() },
                        onHome = { goHome() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pending = requestOf(intent) to false
        takePending()
    }

    override fun onStart() {
        super.onStart()
        readAccess(atStart = true)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        readAccess(atStart = false)
    }

    /** Reads the grant; the line is written at each start and whenever a read finds it changed. */
    private fun readAccess(atStart: Boolean) {
        val now = Checklist.allFilesAccess()
        if (atStart || now != access.value) Diagnostics.add("files", "access=${if (now) "granted" else "denied"}")
        access.value = now
        takePending()
    }

    /** Acts on the waiting launch once the grant is held. */
    private fun takePending() {
        if (access.value != true) return
        val (request, cold) = pending ?: return
        pending = null
        state.open(request, cold)
    }

    private fun requestOf(intent: Intent?) = FilesRequest(
        page = intent?.getStringExtra(FilesIntents.EXTRA_PAGE),
        path = intent?.getStringExtra(FilesIntents.EXTRA_PATH),
        name = intent?.getStringExtra(FilesIntents.EXTRA_NAME),
        from = intent?.getStringExtra(FilesIntents.EXTRA_FROM),
    )

    /**
     * Back with nothing left in Files' history: the task goes behind the one that was there before it (Voice Recorder
     * after "Open file location", Start otherwise) and Files stays as it is, so a return to it is a warm one — W10M
     * resumed File Explorer where it was left (pass 2 §3 #4).
     */
    private fun leave() {
        moveTaskToBack(true)
    }

    private fun openAllFilesAccess() {
        runCatching { startActivity(Checklist.allFilesAccessIntent(this)) }
            .onFailure { Diagnostics.add("files", "grant link failed: $it") }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        /** The extras' keys live in [FilesIntents]; these two names are kept for the callers that already use them. */
        const val EXTRA_PAGE = FilesIntents.EXTRA_PAGE
        const val EXTRA_PATH = FilesIntents.EXTRA_PATH
    }
}

@Composable
private fun FilesScreen(granted: Boolean, state: FilesState, onGrant: () -> Unit, onBack: () -> Unit, onHome: () -> Unit) {
    BackHandler(enabled = true) { onBack() }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("files_root")) {
        Column(Modifier.fillMaxSize()) {
            W10mStatusBar()
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (granted) FilesPage(state) else UngrantedPage(onGrant)
            }
            W10mNavBar(onBack = onBack, onWindows = onHome)
        }
        if (granted) state.actions.ScreenOverlays(state)
    }
}

/** Without All-files access: what is wrong, where the grant lives, and the grant itself (E1). */
@Composable
private fun UngrantedPage(onGrant: () -> Unit) {
    val colors = LocalShellColors.current
    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        BasicText("Files can't see this phone's storage", Modifier.testTag("files_ungranted"), style = ShellType.subtitle.copy(color = Color.White))
        BasicText(
            "Turn on Files in Start settings > Setup checklist, or allow it here.",
            Modifier.padding(top = 12.dp),
            style = ShellType.body.copy(color = Color.White),
        )
        BasicText(
            "Allow all files access",
            Modifier.padding(top = 16.dp).testTag("files_grant_link").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onGrant),
            style = ShellType.subtitle.copy(color = colors.accent),
        )
    }
}
