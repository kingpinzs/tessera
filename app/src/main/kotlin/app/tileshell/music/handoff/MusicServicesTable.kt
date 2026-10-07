package app.tileshell.music.handoff

import app.tileshell.video.VideoLines
import app.tileshell.video.handoff.HandoffPlan
import java.net.URLEncoder
import java.util.Locale

/**
 * One entry of a title's "Listen on" list: a service of the table that is on the phone, or any other installed music app.
 *
 * @param id the table's id (`pandora`), or the package name of an app the table does not know
 * @param label what the entry is called on the page ("Listen on <label>") and what Tess matches a spoken app against
 * @param searchLinks the service's recorded search links, in the order they are tried, `%s` for the query; empty for
 *  Tidal and for an app the table does not know
 */
data class MusicHandoffEntry(val id: String, val label: String, val packageName: String, val searchLinks: List<String>) {
    /** How a `[music] handoff:` line names it: the table's id, or an unknown app's own label. */
    val lineName: String get() = if (searchLinks.isEmpty() && id == packageName) VideoLines.text(label) else id
}

/** An installed app that answers `MAIN` + `CATEGORY_APP_MUSIC`, as the package manager lists it. */
data class MusicLauncher(val packageName: String, val label: String)

/**
 * The music hand-off's table and its logic (phase 20 build task 8; r3 D3), free of Android types so the unit tests
 * cover every row. MusicBrainz says nothing about where a title streams, so every entry opens the service's OWN search
 * for the title: its recorded link, then Android's play-from-search, then the app itself.
 *
 * The links are the services' published web-search addresses (r11/music-radio-addendum-2026-10-07.md §3), handed to
 * that service's app with its package named. Whether each installed app lands on its results is the phone's to prove
 * (P3) — none is verified here.
 */
object MusicServicesTable {
    /** The QA Tunes fixture's row (`testapps/qa-tunes`): in the table of DEBUG builds only. */
    const val QA_TUNES_ID = "qa-tunes"
    const val QA_TUNES_PACKAGE = "app.tileshell.testclient.qatunes"

    /** What a tap says when the app has gone since the list was drawn (`StreamingHandoff.Opened.NOT_INSTALLED`). */
    const val TEXT_NOT_INSTALLED = "That app isn't installed any more"

    private val production: List<MusicHandoffEntry> = listOf(
        // Jeremy's own (Q5 follow-up): the app's deep link first, as Pandora's search page advertises it, then the web link.
        MusicHandoffEntry("pandora", "Pandora", "com.pandora.android", listOf("pandorav8://search/%s/all", "https://www.pandora.com/search/%s/all")),
        MusicHandoffEntry("youtube-music", "YouTube Music", "com.google.android.apps.youtube.music", listOf("https://music.youtube.com/search?q=%s")),
        MusicHandoffEntry("amazon-music", "Amazon Music", "com.amazon.mp3", listOf("https://music.amazon.com/search/%s")),
        MusicHandoffEntry("apple-music", "Apple Music", "com.apple.android.music", listOf("https://music.apple.com/us/search?term=%s")),
        MusicHandoffEntry("deezer", "Deezer", "deezer.android.app", listOf("https://www.deezer.com/search/%s")),
        MusicHandoffEntry("soundcloud", "SoundCloud", "com.soundcloud.android", listOf("https://soundcloud.com/search?q=%s")),
        // No link: Tidal's search address answered 403, so nothing is recorded for it.
        MusicHandoffEntry("tidal", "Tidal", "com.aspiro.tidal", emptyList()),
    )

    private val qaTunes = MusicHandoffEntry(QA_TUNES_ID, "QA Tunes", QA_TUNES_PACKAGE, listOf("https://qa-tunes.test/search?q=%s"))

    /** The table: the seven recorded services, plus the QA Tunes fixture row in a DEBUG build only. */
    fun services(debug: Boolean): List<MusicHandoffEntry> = if (debug) production + qaTunes else production

    /** What a hand-off searches for: the title and its artist ("Bloom Beach House"); the title alone when there is none. */
    fun query(title: String, artist: String): String = listOf(title.trim(), artist.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * A search link with the query in it. After a `?` the query is a form value (a space is `+`); before one it is a
     * path segment, where `+` would be a literal plus, so a space is `%20`. Either way a `/`, `?`, `#` or `&` of the
     * title is percent-encoded and cannot change the link's shape.
     */
    fun link(form: String, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val inPath = '?' !in form.substringBefore("%s")
        return form.replace("%s", if (inPath) encoded.replace("+", "%20") else encoded)
    }

    /**
     * What a tap on "Listen on <service>" tries, in order: each recorded search link → play-from-search → the app
     * itself. Tidal and an app the table does not know have no link, so theirs starts at play-from-search.
     */
    fun plan(service: MusicHandoffEntry, title: String, artist: String): List<HandoffPlan> {
        val query = query(title, artist)
        return buildList {
            for (form in service.searchLinks) add(HandoffPlan.Search(link(form, query)))
            add(HandoffPlan.PlayFromSearch(query))
            add(HandoffPlan.Launch)
        }
    }

    /**
     * The "Listen on" list (r3 D3 (d)): the table's rows that are installed, in the table's order, then every other
     * app that declares `CATEGORY_APP_MUSIC`, by label — never the shell itself ([self]; it declares the category
     * too), and never a table package a second time. A local player is a plain-open entry like any other.
     *
     * @param installed the packages on the phone, or at least those of the table's that are
     */
    fun entries(installed: Set<String>, musicLaunchers: List<MusicLauncher>, self: String, debug: Boolean): List<MusicHandoffEntry> {
        val table = services(debug)
        val known = table.mapTo(HashSet()) { it.packageName }
        val others = musicLaunchers.filter { it.packageName != self && it.packageName !in known }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ it.label.lowercase(Locale.ROOT) }, { it.packageName }))
            .map { MusicHandoffEntry(it.packageName, it.label.ifBlank { it.packageName }, it.packageName, emptyList()) }
        return table.filter { it.packageName in installed && it.packageName != self } + others
    }

    /**
     * The entry a spoken app name means ("listen to <x> on <app>"; r3 D7), by code: the label that IS the phrase, else
     * the first label that starts with it ("youtube" → YouTube Music) — both compared as letters and digits only, in
     * lower case, so "you tube music" and "qa-tunes" match. Null when nothing does.
     */
    fun byLabel(phrase: String, entries: List<MusicHandoffEntry>): MusicHandoffEntry? {
        val wanted = normalise(phrase)
        if (wanted.isEmpty()) return null
        return entries.firstOrNull { normalise(it.label) == wanted } ?: entries.firstOrNull { normalise(it.label).startsWith(wanted) }
    }

    private fun normalise(text: String): String = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    // ---- the lines (`[music] handoff: …`)

    /**
     * The line for one plan that was tried (`StreamingHandoff.openPlans`' `line`):
     *  - a search link or play-from-search that opened: `handoff: <service> "<title>" -> <uri|intent>`;
     *  - one that nothing answered: `handoff: <service> did not open at the title` (the next plan is tried);
     *  - the plain open, for an entry with no recorded link: `handoff: <app> "<title>" -> open (no search link)`;
     *  - the plain open as the LAST fallback of a service that has links: `handoff: <service>: search not passed`.
     * A plain open that fails writes nothing: the app is gone, and the tap says so.
     */
    fun line(service: MusicHandoffEntry, title: String, plan: HandoffPlan, opened: Boolean): String? {
        val name = service.lineName
        val quoted = "\"${VideoLines.text(title)}\""
        return when {
            plan == HandoffPlan.Launch -> when {
                !opened -> null
                service.searchLinks.isEmpty() -> "handoff: $name $quoted -> open (no search link)"
                else -> "handoff: $name: search not passed"
            }
            !opened -> "handoff: $name did not open at the title"
            plan is HandoffPlan.Search -> "handoff: $name $quoted -> ${VideoLines.text(plan.url, VideoLines.ADDRESS_MAX)}"
            plan is HandoffPlan.Title -> "handoff: $name $quoted -> ${VideoLines.text(plan.url, VideoLines.ADDRESS_MAX)}"
            else -> "handoff: $name $quoted -> ${HandoffPlan.PlayFromSearch.ACTION}"
        }
    }
}
