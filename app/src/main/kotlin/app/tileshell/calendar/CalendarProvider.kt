package app.tileshell.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract

/** The calendar provider's tables the shell reads or writes, each one `CalendarContract` content URI. */
enum class ProviderTable {
    /** `Calendars.CONTENT_URI`. */
    CALENDARS,
    /** `Events.CONTENT_URI`. */
    EVENTS,
    /** `Events.CONTENT_EXCEPTION_URI`: an insert under a master's id makes an exception event of it. */
    EXCEPTIONS,
    /** `Reminders.CONTENT_URI`. */
    REMINDERS,
    /** `CalendarAlerts.CONTENT_URI`. */
    ALERTS,
    /** `Instances.CONTENT_URI`, with the two ends of the window as its ids. */
    INSTANCES,
}

/**
 * A `CalendarContract` URI without Android's types: the table, the ids appended to its path (a row's id; a window's two
 * ends), and — for a write made as a LOCAL account's own sync adapter — that account's name.
 */
data class ProviderUri(val table: ProviderTable, val ids: List<Long> = emptyList(), val syncAdapterAccount: String? = null) {
    constructor(table: ProviderTable, id: Long) : this(table, listOf(id))

    /** The same URI through `caller_is_syncadapter`, as the LOCAL account [accountName]'s own sync adapter. */
    fun asSyncAdapter(accountName: String): ProviderUri = copy(syncAdapterAccount = accountName)
}

/**
 * One row of a query, read the way a `Cursor` reads it: a number asked of an empty cell is 0, and text asked of a number
 * is its digits.
 */
class ProviderRow(private val cells: List<Any?>) {
    fun isNull(i: Int): Boolean = cells[i] == null

    fun long(i: Int): Long = when (val v = cells[i]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }

    fun int(i: Int): Int = long(i).toInt()

    fun string(i: Int): String? = when (val v = cells[i]) {
        null, is ByteArray -> null
        else -> v.toString()
    }
}

/**
 * The four calls the shell's calendar code makes of the provider (fix round F15, trust review A-F1): every read and
 * every write of [CalendarWrites], [CalendarReads], `CalendarSync`, `LocalCalendar` and Tess's calendar code goes
 * through this, so a JVM test can stand a recording fake in the provider's place and see exactly which writes a
 * refused request reaches — none — and which an allowed one makes. The phone's is [ResolverCalendarProvider].
 *
 * Each call throws what the provider throws.
 */
interface CalendarProvider {
    /** The rows, each with one cell per column of [columns]; null when the provider gave no answer. */
    fun query(uri: ProviderUri, columns: List<String>, where: String? = null, args: List<String> = emptyList(), sort: String? = null): List<ProviderRow>?

    /** The new row's URI as text (see [rowIdOf]); null when the provider made none. */
    fun insert(uri: ProviderUri, values: Map<String, Any?>): String?

    /** The number of rows updated. */
    fun update(uri: ProviderUri, values: Map<String, Any?>, where: String? = null, args: List<String> = emptyList()): Int

    /** The number of rows deleted. */
    fun delete(uri: ProviderUri, where: String? = null, args: List<String> = emptyList()): Int
}

/** The id at the end of a row URI's path — `…/events/42`, `…/calendars/3?caller_is_syncadapter=true&…`; null when it has none. */
fun rowIdOf(uri: String?): Long? = uri?.substringBefore('#')?.substringBefore('?')?.substringAfterLast('/')?.toLongOrNull()

/** [CalendarProvider] on the phone: the content resolver, with `CalendarContract`'s own URIs. */
class ResolverCalendarProvider(private val resolver: ContentResolver) : CalendarProvider {
    override fun query(uri: ProviderUri, columns: List<String>, where: String?, args: List<String>, sort: String?): List<ProviderRow>? =
        resolver.query(uri(uri), columns.toTypedArray(), where, argsOf(args), sort)?.use { c -> buildList { while (c.moveToNext()) add(row(c)) } }

    override fun insert(uri: ProviderUri, values: Map<String, Any?>): String? = resolver.insert(uri(uri), values(values))?.toString()

    override fun update(uri: ProviderUri, values: Map<String, Any?>, where: String?, args: List<String>): Int =
        resolver.update(uri(uri), values(values), where, argsOf(args))

    override fun delete(uri: ProviderUri, where: String?, args: List<String>): Int = resolver.delete(uri(uri), where, argsOf(args))

    private fun argsOf(args: List<String>): Array<String>? = if (args.isEmpty()) null else args.toTypedArray()

    private fun uri(u: ProviderUri): Uri {
        val base = when (u.table) {
            ProviderTable.CALENDARS -> CalendarContract.Calendars.CONTENT_URI
            ProviderTable.EVENTS -> CalendarContract.Events.CONTENT_URI
            ProviderTable.EXCEPTIONS -> CalendarContract.Events.CONTENT_EXCEPTION_URI
            ProviderTable.REMINDERS -> CalendarContract.Reminders.CONTENT_URI
            ProviderTable.ALERTS -> CalendarContract.CalendarAlerts.CONTENT_URI
            ProviderTable.INSTANCES -> CalendarContract.Instances.CONTENT_URI
        }
        val builder = base.buildUpon()
        u.ids.forEach { ContentUris.appendId(builder, it) }
        if (u.syncAdapterAccount != null) {
            builder.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, u.syncAdapterAccount)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        }
        return builder.build()
    }

    private fun values(map: Map<String, Any?>): ContentValues = ContentValues().apply {
        for ((k, v) in map) when (v) {
            null -> putNull(k)
            is String -> put(k, v)
            is Long -> put(k, v)
            is Int -> put(k, v)
            else -> error("a ${v.javaClass.simpleName} for $k")
        }
    }

    private fun row(c: Cursor): ProviderRow = ProviderRow(
        List(c.columnCount) { i ->
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                else -> c.getString(i)
            }
        },
    )
}

/**
 * What the calendar code is handed in place of a `Context`: the provider and the shell's own store. On the phone,
 * [of]; in a JVM test, a recording fake and a store over a temp file.
 */
class CalendarAccess(val provider: CalendarProvider, val store: CalendarSyncStore) {
    companion object {
        @Volatile private var instance: CalendarAccess? = null

        fun of(context: Context): CalendarAccess = instance ?: synchronized(this) {
            instance ?: CalendarAccess(ResolverCalendarProvider(context.applicationContext.contentResolver), CalendarSyncStore.get(context)).also { instance = it }
        }
    }
}
