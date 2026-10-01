package app.tileshell.people

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The two pivots (r11/people.md P1.1, less What's New — out: offline preferred). */
enum class PeoplePivot(val id: String, val title: String) { CONTACTS("contacts", "CONTACTS"), GROUPS("groups", "GROUPS") }

/** What the list is open for. */
sealed interface ListMode {
    data object Browse : ListMode

    /** `ACTION_PICK`: a tap hands back the one contact or phone URI tapped; Back cancels. */
    data class Pick(val kind: PickKind) : ListMode

    /** `ACTION_INSERT_OR_EDIT`: choose "new contact", or an existing editable contact the fields are added to. */
    data class InsertOrEdit(val prefill: ContactPrefill) : ListMode
}

/** A page of People. One activity hosts every one (r3 D11); Back unwinds them. */
sealed interface PeoplePage {
    /** An intent named a contact: shown while the provider is asked which contact that is. */
    data class Opening(val route: PeopleRoute) : PeoplePage
    data class Contacts(val mode: ListMode) : PeoplePage
    data class Card(val contactId: Long, val lookup: String, val enterprise: ContactRow? = null) : PeoplePage
    data class Editor(val draft: EditorDraft) : PeoplePage
    data class Link(val contactId: Long, val lookup: String) : PeoplePage

    /** "Select a contact to link": the list as it was when the page opened (see LinkPickerPage). */
    data class LinkPicker(val contactId: Long, val lookup: String, val snapshot: List<ContactRow>) : PeoplePage
    data object Settings : PeoplePage
    data object CanEdit : PeoplePage
    data object Filter : PeoplePage
    data object Sim : PeoplePage
    data class Group(val groupId: Long) : PeoplePage
    data class GroupEditor(val groupId: Long?, val title: String) : PeoplePage
    data class MemberPicker(val groupId: Long) : PeoplePage
    data class PickNumber(val contactId: Long, val name: String) : PeoplePage
}

/**
 * People's navigation, held above the composition so an intent can route while the activity is already running
 * (onNewIntent). The stack is the pages Back unwinds; the things drawn over a page — the "…" menu, the jump grid, a
 * flyout, a confirmation — close first.
 */
class PeopleNav {
    /** What the last intent asked for; [routeToken] changes on every intent, so the same route twice is two opens. */
    var route by mutableStateOf<PeopleRoute>(PeopleRoute.Open(null))
        private set
    var routeToken by mutableIntStateOf(0)
        private set

    private val stack = mutableStateListOf<PeoplePage>(PeoplePage.Contacts(ListMode.Browse))
    val page: PeoplePage get() = stack.last()
    val depth: Int get() = stack.size

    var pivot by mutableStateOf(PeoplePivot.CONTACTS)

    /** A tap on the other pivot's header: the pager slides to it, then it is [pivot]. */
    var pivotRequest by mutableStateOf<PeoplePivot?>(null)

    /** The page's notice line (`people_notice`): it stays until the page changes. */
    var notice by mutableStateOf<PeopleNotice?>(null)

    /** The app bar's "…" menu, and what is open over the page: the jump grid, or one named overlay (a flyout, a prompt). */
    var barExpanded by mutableStateOf(false)
    var gridOpen by mutableStateOf(false)
    var overlay by mutableStateOf<String?>(null)

    fun open(route: PeopleRoute) {
        this.route = route
        routeToken++
        closeOverlays()
        notice = null
        stack.clear()
        when (route) {
            is PeopleRoute.Open -> {
                pivot = if (route.page == PeopleShortcut.GROUPS) PeoplePivot.GROUPS else PeoplePivot.CONTACTS
                stack += PeoplePage.Contacts(ListMode.Browse)
                // The "New contact" shortcut: the editor over the list, on the phone, nothing saved until Save.
                if (route.page == PeopleShortcut.NEW_CONTACT) stack += PeoplePage.Opening(route)
            }
            is PeopleRoute.Card, is PeopleRoute.Edit, is PeopleRoute.Insert -> stack += PeoplePage.Opening(route)
            is PeopleRoute.InsertOrEdit -> { pivot = PeoplePivot.CONTACTS; stack += PeoplePage.Contacts(ListMode.InsertOrEdit(route.prefill)) }
            is PeopleRoute.Pick -> { pivot = PeoplePivot.CONTACTS; stack += PeoplePage.Contacts(ListMode.Pick(route.kind)) }
        }
    }

    fun push(page: PeoplePage) {
        closeOverlays()
        notice = null
        stack += page
    }

    /** Replaces the page on show (an Opening page with what it resolved to; the editor with the card it saved). */
    fun replace(page: PeoplePage, notice: PeopleNotice? = null) {
        closeOverlays()
        stack[stack.lastIndex] = page
        this.notice = notice
    }

    /** Leaves the page on show; [notice] is what the page under it then says. False when it was the last page. */
    fun pop(notice: PeopleNotice? = null): Boolean {
        closeOverlays()
        if (stack.size <= 1) {
            this.notice = notice
            return false
        }
        stack.removeAt(stack.lastIndex)
        this.notice = notice
        return true
    }

    private fun closeOverlays() {
        barExpanded = false
        gridOpen = false
        overlay = null
    }

    /** Back: the innermost thing first. False when there was nothing left to unwind (the activity finishes). */
    fun back(): Boolean = when {
        overlay != null -> { overlay = null; true }
        barExpanded -> { barExpanded = false; true }
        gridOpen -> { gridOpen = false; true }
        else -> pop()
    }
}

/** What a page needs from the activity. */
class PeopleHost(
    val onBack: () -> Unit,
    val onWindows: () -> Unit,
    /** Ends a PICK: the one URI picked goes back to the caller with a read grant for it alone; null cancels. */
    val finishPick: (Uri?) -> Unit,
    /** Leaves People (the last page closed itself). */
    val finish: () -> Unit,
)

/**
 * What the pages share: the list, the accounts, the groups and what the app may do, re-read whenever the Contacts
 * provider changes (a `ContentObserver`) or the app comes back to the front (a grant may have changed).
 */
class PeopleRepo(private val context: Context, private val scope: CoroutineScope) {
    var loaded by mutableStateOf(false)
        private set
    var canRead by mutableStateOf(PeopleData.canRead(context))
        private set
    var canWrite by mutableStateOf(PeopleData.canWrite(context))
        private set
    /** Every contact, before the filter. */
    var all by mutableStateOf<List<ContactRow>>(emptyList())
        private set
    /** The contacts the list shows: [all] less what "filter contact list" hides. */
    var rows by mutableStateOf<List<ContactRow>>(emptyList())
        private set
    var accounts by mutableStateOf<List<ContactAccount>>(emptyList())
        private set
    var memberships by mutableStateOf<Map<Long, ContactMembership>>(emptyMap())
        private set
    var groups by mutableStateOf<List<ContactGroup>>(emptyList())
        private set
    /** Counts the reads: a page that holds its own data re-reads when this changes. */
    var version by mutableIntStateOf(0)
        private set

    val local: ContactAccount = PeopleEditStore.localAccount(context)
    private val filters = PeopleFilterStore.get(context)

    private var running = false
    private var again = false

    /**
     * One read at a time; a change that lands while one runs is read once it ends, however many landed — and a burst
     * of changes (a sync, an import) is let settle first, so it costs a few reads and not one per row.
     */
    fun refresh(reason: String) {
        if (running) { again = true; return }
        running = true
        Diagnostics.add("people", "reload ($reason)")
        scope.launch {
            var first = true
            do {
                if (!first) delay(BURST_SETTLE_MS)
                first = false
                again = false
                val read = PeopleData.canRead(context)
                val write = PeopleData.canWrite(context)
                val filter = filters.filter.value
                val snapshot = withContext(Dispatchers.IO) {
                    if (!read) return@withContext null
                    val contacts = PeopleData.contacts(context) ?: return@withContext null
                    val named = PeopleData.syncAccounts(context).orEmpty()
                    Read(contacts, named, PeopleData.memberships(context), PeopleData.groups(context))
                }
                clearAvatarCache()
                canRead = read
                canWrite = write
                all = snapshot?.contacts.orEmpty()
                accounts = snapshot?.accounts.orEmpty()
                memberships = snapshot?.memberships.orEmpty()
                groups = snapshot?.groups.orEmpty()
                rows = all.filter { FilterRules.shows(it, memberships[it.id], filter) }
                loaded = true
                version++
                PeopleData.logList(context, rows.size)
            } while (again)
            running = false
        }
    }

    private companion object {
        const val BURST_SETTLE_MS = 300L
    }

    private class Read(val contacts: List<ContactRow>, val accounts: List<ContactAccount>, val memberships: Map<Long, ContactMembership>, val groups: List<ContactGroup>)
}

/** The whole app: the frame every page shares, and the page the navigation holds. */
@Composable
fun PeopleApp(nav: PeopleNav, host: PeopleHost) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { PeopleRepo(context, scope) }
    val allowed by PeopleEditStore.get(context).allowed.collectAsState()
    val filter by PeopleFilterStore.get(context).filter.collectAsState()
    val policy = remember(allowed, repo.local) { EditPolicy(repo.local, allowed) }

    // The pages follow the provider (a ContentObserver, no timer) and the grants (re-read when the app comes back).
    DisposableEffect(context) {
        val observer = PeopleData.observe(context) { repo.refresh("provider change") }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) repo.refresh("resume") }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(filter) { if (repo.loaded) repo.refresh("filter") }

    // Grants are offered in place (phase 10 E18's form): the notice's action asks Android; a grant Android will no
    // longer ask for opens the app's own settings page instead.
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        Diagnostics.add("people", "permission request: ${result.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" }}")
        val refused = result.filterValues { !it }.keys
        if (refused.isNotEmpty() && refused.none { (context as Activity).shouldShowRequestPermissionRationale(it) }) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
        if (refused.isEmpty()) nav.notice = null
        repo.refresh("grant")
    }
    val onNoticeAction: (NoticeAction) -> Unit = { action ->
        when (action) {
            NoticeAction.GRANT_CONTACTS -> grant.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
            NoticeAction.GRANT_CALLS -> grant.launch(arrayOf(Manifest.permission.CALL_PHONE))
            NoticeAction.CAN_EDIT -> nav.push(PeoplePage.CanEdit)
        }
    }
    val env = PeopleEnv(nav, host, repo, policy, filter, onNoticeAction)

    when (val page = nav.page) {
        is PeoplePage.Opening -> OpeningPage(env, page)
        is PeoplePage.Contacts -> ContactsPage(env, page.mode)
        is PeoplePage.Card -> CardPage(env, page)
        is PeoplePage.Editor -> EditorPage(env, page.draft)
        is PeoplePage.Link -> LinkPage(env, page)
        is PeoplePage.LinkPicker -> LinkPickerPage(env, page)
        PeoplePage.Settings -> SettingsPage(env)
        PeoplePage.CanEdit -> CanEditPage(env)
        PeoplePage.Filter -> FilterPage(env)
        PeoplePage.Sim -> SimPage(env)
        is PeoplePage.Group -> GroupPage(env, page.groupId)
        is PeoplePage.GroupEditor -> GroupEditorPage(env, page)
        is PeoplePage.MemberPicker -> MemberPickerPage(env, page.groupId)
        is PeoplePage.PickNumber -> PickNumberPage(env, page)
    }
}

/** What every page is handed. */
class PeopleEnv(
    val nav: PeopleNav,
    val host: PeopleHost,
    val repo: PeopleRepo,
    val policy: EditPolicy,
    val filter: ContactFilter,
    val onNoticeAction: (NoticeAction) -> Unit,
)

/** The notices a write's outcome puts on a page (approximations, H20). */
object PeopleNotices {
    val CANNOT_READ = PeopleNotice("People can't read your contacts. Allow Contacts to see them here.", NoticeAction.GRANT_CONTACTS)
    val CANNOT_SAVE = PeopleNotice("People can't save this: it isn't allowed to change contacts.", NoticeAction.GRANT_CONTACTS)
    val NOT_ALLOWED = PeopleNotice("That account isn't on Can edit, so People can't change it.", NoticeAction.CAN_EDIT)
    val CONTACT_GONE = PeopleNotice("That contact is no longer on this phone.")
    val GROUP_GONE = PeopleNotice("That group is no longer on this phone.")

    fun failed(what: String) = PeopleNotice("People couldn't $what. Nothing was changed.")

    /** The notice for a write that did not happen; null when it did. */
    fun of(result: WriteResult, what: String): PeopleNotice? = when (result) {
        is WriteResult.Ok -> null
        WriteResult.Refused -> NOT_ALLOWED
        is WriteResult.Failed -> if (result.needsGrant) CANNOT_SAVE else failed(what)
    }
}

/**
 * An intent named a contact, or asked for the editor: the provider is asked which contact that is, and the write guard
 * whether an EDIT may open the editor (Trust (c)). Nothing here writes.
 */
@Composable
private fun OpeningPage(env: PeopleEnv, page: PeoplePage.Opening) {
    val context = LocalContext.current
    val nav = env.nav
    Box(Modifier.fillMaxSize().background(LocalShellColors.current.background))
    LaunchedEffect(page) {
        when (val route = page.route) {
            is PeopleRoute.Open, is PeopleRoute.Insert -> {
                // A prefill starts on the phone whatever account its caller named, and saves nothing until Save is tapped.
                val prefill = (route as? PeopleRoute.Insert)?.prefill ?: ContactPrefill()
                val source = if (route is PeopleRoute.Insert) NewContactSource.INSERT_PREFILL else NewContactSource.EDITOR
                nav.replace(PeoplePage.Editor(EditorDraft.new(env.repo.local, source, prefill)))
            }
            is PeopleRoute.Card, is PeopleRoute.Edit -> {
                val ref = if (route is PeopleRoute.Card) route.contact else (route as PeopleRoute.Edit).contact
                val card = withContext(Dispatchers.IO) {
                    if (!PeopleData.canRead(context)) null else PeopleData.resolve(context, ref)?.let { PeopleData.card(context, it) }
                }
                when {
                    card == null -> {
                        // Nothing to show for it: the list, saying why.
                        nav.replace(PeoplePage.Contacts(ListMode.Browse), if (PeopleData.canRead(context)) PeopleNotices.CONTACT_GONE else null)
                    }
                    route is PeopleRoute.Edit -> {
                        val actions = PeopleWriteGuard.cardActions(card.raws.map { it.ref() }, env.policy)
                        if (actions.edit) {
                            nav.replace(PeoplePage.Editor(EditorDraft.edit(card, env.policy, ContactPrefill())))
                        } else {
                            // The exported EDIT on a contact the guard keeps read-only opens its card, never the editor.
                            Diagnostics.add("people", "edit ${card.lookup}: refused (account not allowed)")
                            nav.replace(PeoplePage.Card(card.id, card.lookup))
                        }
                    }
                    else -> nav.replace(PeoplePage.Card(card.id, card.lookup))
                }
            }
            else -> nav.replace(PeoplePage.Contacts(ListMode.Browse))
        }
    }
}
