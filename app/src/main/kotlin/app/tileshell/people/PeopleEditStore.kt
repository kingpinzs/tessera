package app.tileshell.people

import android.content.Context
import android.provider.ContactsContract
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * People's "Can edit" list (Q-16-3; Decisions "the People write guard and the Can edit list" point (2)): the accounts
 * the user allowed People to write, keyed on account name + type, in People's own `people_edit.json` in the files dir
 * (temp file, then rename — `LayoutStore`'s form). Nothing is allowed until the user ticks it; the phone's own contacts
 * need no entry, they are always editable.
 *
 * A file that is missing or cannot be read is an empty list: nothing allowed, phone-only contacts still editable.
 */
class PeopleEditStore private constructor(private val file: File) {
    private val state = MutableStateFlow(read())

    /** The allowed accounts, as last written or pruned. */
    val allowed: StateFlow<Set<ContactAccount>> = state.asStateFlow()

    @Synchronized
    fun set(account: ContactAccount, on: Boolean) {
        val next = CanEditRules.toggle(state.value, account, on)
        if (next == state.value) return
        write(next)
        Diagnostics.add("people", "can edit ${account.id}: ${if (on) "allowed" else "not allowed"} (${next.size} allowed)")
    }

    /**
     * Drops every allowed account the provider no longer names ([named] is the result of a SUCCESSFUL read of the
     * provider's accounts — a failed read must never reach here, or losing READ_CONTACTS would clear the list).
     */
    @Synchronized
    fun prune(named: Collection<ContactAccount>) {
        val next = CanEditRules.prune(state.value, named)
        if (next == state.value) return
        val dropped = state.value - next
        write(next)
        Diagnostics.add("people", "can edit: dropped ${dropped.joinToString { it.id }} (no longer on this phone)")
    }

    private fun write(next: Set<ContactAccount>) {
        val json = JSONObject().put("allowed", JSONArray().apply {
            next.forEach { put(JSONObject().put("name", it.name).put("type", it.type)) }
        })
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) error("rename failed")
        }.onFailure {
            // What is on disk is what the next process start reads: the list in memory must not claim more than that.
            Diagnostics.add("people", "can edit: people_edit.json could not be written: $it")
            state.value = read()
            return
        }
        state.value = next
    }

    private fun read(): Set<ContactAccount> = runCatching {
        if (!file.exists()) return@runCatching emptySet()
        val array = JSONObject(file.readText()).optJSONArray("allowed") ?: return@runCatching emptySet()
        val out = LinkedHashSet<ContactAccount>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            // An entry without a name or a type names no account the provider could hold; it is never an allowance.
            if (o.isNull("name") || o.isNull("type")) continue
            val name = o.optString("name")
            val type = o.optString("type")
            if (name.isNotEmpty() && type.isNotEmpty()) out += ContactAccount(name, type)
        }
        out
    }.getOrElse {
        Diagnostics.add("people", "can edit: people_edit.json unreadable, nothing is allowed: $it")
        emptySet()
    }

    companion object {
        const val FILE = "people_edit.json"

        @Volatile private var instance: PeopleEditStore? = null

        fun get(context: Context): PeopleEditStore =
            instance ?: synchronized(this) {
                instance ?: PeopleEditStore(File(context.applicationContext.filesDir, FILE)).also { instance = it }
            }

        /**
         * The policy every write is checked against: the device's local account as the provider reports it (never a
         * literal null — Verify at build start 4) and the allowed list as it stands now.
         */
        fun policy(context: Context): EditPolicy = EditPolicy(localAccount(context), get(context).allowed.value)

        fun localAccount(context: Context): ContactAccount = ContactAccount(
            ContactsContract.RawContacts.getLocalAccountName(context),
            ContactsContract.RawContacts.getLocalAccountType(context),
        ).also { CardRules.phoneAccount = it }
    }
}
