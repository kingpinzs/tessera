package app.tileshell.applist

import android.os.UserHandle
import app.tileshell.apps.AppEntry
import app.tileshell.apps.ProfileKind
import app.tileshell.brand.Glyph
import java.util.Locale

/** One LazyColumn item of the A-Z list. Keys are stable across refreshes. */
sealed interface ListItem {
    val key: String
    val contentType: Int
}

data class LetterHeaderItem(val letter: String) : ListItem {
    override val key: String get() = "hdr:$letter"
    override val contentType: Int get() = 0
}

data class ProfileHeaderItem(val profile: ShellProfile) : ListItem {
    override val key: String get() = "phdr:${profile.serial}"
    override val contentType: Int get() = 1
}

data class UnlockItem(val profile: ShellProfile) : ListItem {
    override val key: String get() = "unlock:${profile.serial}"
    override val contentType: Int get() = 2
}

data class AppRowItem(val entry: AppEntry, val newKey: String, val normalizedLabel: String) : ListItem {
    override val key: String get() = "row:${entry.key}"
    override val contentType: Int get() = 3
}

/** A jump grid cell: "#", "A".."Z" or a profile glyph; [targetIndex] null = no apps (dimmed). */
data class JumpCell(val id: String, val label: String, val isGlyph: Boolean, val targetIndex: Int?)

class AppListModel(
    val items: List<ListItem>,
    /** Every visible, unlocked app row in collation order (search source). */
    val searchable: List<AppRowItem>,
    val cells: List<JumpCell>,
) {
    fun describe(): String {
        val groups = items.mapNotNull {
            when (it) {
                is LetterHeaderItem -> it.letter
                is ProfileHeaderItem -> it.profile.tagName + if (it.profile.quiet) "(locked)" else ""
                else -> null
            }
        }
        return "${searchable.size} rows, groups=${groups.joinToString(",")}"
    }
}

fun profileGlyph(kind: ProfileKind): String = if (kind == ProfileKind.PRIVATE) Glyph.LOCK else Glyph.BRIEFCASE

/**
 * Builds the A-Z list: "#" and letter groups of the main profile, then (when [showProfiles]) one group per work
 * profile / private space headed by its glyph (P4 design). A locked (quiet-mode) profile collapses to "Tap to unlock".
 */
fun buildAppListModel(
    apps: List<AppEntry>,
    profiles: List<ShellProfile>,
    showProfiles: Boolean,
    locale: Locale,
    keyOf: (AppEntry) -> String,
    serialOf: (UserHandle) -> Long,
): AppListModel {
    val collator = AppIndex.collator(locale)
    val items = ArrayList<ListItem>(apps.size + 40)
    val searchable = ArrayList<AppRowItem>(apps.size)
    val headerIndex = HashMap<String, Int>()
    fun row(entry: AppEntry) = AppRowItem(entry, keyOf(entry), AppIndex.normalize(entry.label)).also { items += it; searchable += it }

    val main = apps.filter { it.profile == ProfileKind.MAIN }
    for ((letter, group) in AppIndex.group(main, { it.label }, { it.key }, collator)) {
        headerIndex[letter] = items.size
        items += LetterHeaderItem(letter)
        group.forEach { row(it) }
    }
    val cells = AppIndex.JUMP_LETTERS.mapTo(ArrayList()) { JumpCell(it, it, false, headerIndex[it]) }

    if (showProfiles) {
        val byUser = apps.filter { it.profile != ProfileKind.MAIN }.groupBy { it.user }
        val known = profiles.map { it.user }.toSet()
        // A profile whose apps arrived before its broadcast still gets a group.
        val extra = byUser.keys.filter { it !in known }.map { ShellProfile(it, byUser.getValue(it).first().profile, false, serialOf(it)) }
        for (profile in profiles + extra) {
            cells += JumpCell(profile.tagName, profileGlyph(profile.kind), true, items.size)
            items += ProfileHeaderItem(profile)
            if (profile.quiet) {
                items += UnlockItem(profile)
            } else {
                AppIndex.sort(byUser[profile.user].orEmpty(), { it.label }, { it.key }, collator).forEach { row(it) }
            }
        }
    }
    val sortedSearch = AppIndex.sort(searchable, { it.entry.label }, { it.entry.key }, collator)
    return AppListModel(items, sortedSearch, cells)
}
