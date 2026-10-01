package app.tileshell.people

import android.content.Context
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "Filter contact list" (r11/people.md P5.1): which accounts and groups People's list hides, and W10M's "Hide contacts
 * without phone numbers". It is People's own state in its own `people_filter.json` (temp file, then rename) — the
 * provider's `Groups.GROUP_VISIBLE` and `Settings.UNGROUPED_VISIBLE` are never written, as the Calendar's ≡ pane never
 * writes `Calendars.VISIBLE`: a filter is not a write to an account.
 */
class PeopleFilterStore private constructor(private val file: File) {
    private val state = MutableStateFlow(read())
    val filter: StateFlow<ContactFilter> = state.asStateFlow()

    @Synchronized
    fun set(next: ContactFilter) {
        if (next == state.value) return
        val json = JSONObject()
            .put("hideWithoutPhones", next.hideWithoutPhones)
            .put("hiddenAccounts", JSONArray().apply {
                next.hiddenAccounts.forEach { put(JSONObject().put("name", it.name ?: JSONObject.NULL).put("type", it.type ?: JSONObject.NULL)) }
            })
            .put("hiddenGroups", JSONArray().apply { next.hiddenGroups.forEach { put(it) } })
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) error("rename failed")
        }.onFailure {
            Diagnostics.add("people", "filter: people_filter.json could not be written: $it")
            return
        }
        state.value = next
        Diagnostics.add("people", "filter: ${next.hiddenAccounts.size} accounts and ${next.hiddenGroups.size} groups hidden, hideWithoutPhones=${next.hideWithoutPhones}")
    }

    private fun read(): ContactFilter = runCatching {
        if (!file.exists()) return@runCatching ContactFilter()
        val json = JSONObject(file.readText())
        val accounts = LinkedHashSet<ContactAccount>()
        json.optJSONArray("hiddenAccounts")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                accounts += ContactAccount(if (o.isNull("name")) null else o.optString("name"), if (o.isNull("type")) null else o.optString("type"))
            }
        }
        val groups = LinkedHashSet<Long>()
        json.optJSONArray("hiddenGroups")?.let { a -> for (i in 0 until a.length()) groups += a.optLong(i) }
        ContactFilter(accounts, groups, json.optBoolean("hideWithoutPhones", false))
    }.getOrElse {
        Diagnostics.add("people", "filter: people_filter.json unreadable, every contact is listed: $it")
        ContactFilter()
    }

    companion object {
        const val FILE = "people_filter.json"

        @Volatile private var instance: PeopleFilterStore? = null

        fun get(context: Context): PeopleFilterStore =
            instance ?: synchronized(this) {
                instance ?: PeopleFilterStore(File(context.applicationContext.filesDir, FILE)).also { instance = it }
            }
    }
}
