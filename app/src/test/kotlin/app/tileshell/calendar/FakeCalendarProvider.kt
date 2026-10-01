package app.tileshell.calendar

/**
 * A recording stand-in for the calendar provider (fix round F15): in-memory calendars, events, reminders, alerts and
 * instances that answer the queries the shell's calendar code makes, and a list of every write that reached it —
 * the op, the URI, the columns and the selection — which is what the write-layer tests assert on.
 *
 * It knows the selections the product uses and no others: a selection it cannot read is an error, so a query that
 * changes shape fails its test instead of silently matching nothing.
 */
class FakeCalendarProvider : CalendarProvider {
    data class Write(val op: String, val uri: ProviderUri, val values: Map<String, Any?> = emptyMap(), val where: String? = null, val args: List<String> = emptyList()) {
        val columns: Set<String> get() = values.keys
    }

    /** Every insert, update and delete that reached the provider, in order. */
    val writes = ArrayList<Write>()

    val calendars = ArrayList<MutableMap<String, Any?>>()
    val events = ArrayList<MutableMap<String, Any?>>()
    val reminders = ArrayList<MutableMap<String, Any?>>()
    val alerts = ArrayList<MutableMap<String, Any?>>()
    val instances = ArrayList<MutableMap<String, Any?>>()

    /** A query of a table for which this answers true gets no answer (null), as from a provider that is off. */
    var noAnswer: (ProviderUri) -> Boolean = { false }

    /** A query of a table for which this answers true throws, as with READ_CALENDAR revoked. */
    var throwOnQuery: (ProviderUri) -> Boolean = { false }

    /** Called after each write was applied: a test's way to change the provider between two writes of one call. */
    var afterWrite: (Write) -> Unit = {}

    private var nextId = 1000L

    // ------------------------------------------------------------------------------------------ fixtures

    fun calendar(id: Long, account: String, type: String, access: Int, name: String, displayName: String = name): CalendarInfo {
        calendars += mutableMapOf(
            "_id" to id, "account_name" to account, "account_type" to type, "calendar_displayName" to displayName,
            "calendar_color" to null, "calendar_access_level" to access, "name" to name,
        )
        return CalendarInfo(id, account, type, displayName, null, access, name)
    }

    fun event(
        id: Long, calendarId: Long, title: String = "Event $id", dtstart: Long = 1_790_000_000_000L, dtend: Long? = 1_790_003_600_000L,
        rrule: String? = null, duration: String? = null, originalId: Long? = null, originalInstanceTime: Long? = null, syncId: String? = null, status: Int? = null,
    ) {
        events += mutableMapOf(
            "_id" to id, "calendar_id" to calendarId, "title" to title, "eventLocation" to null, "description" to null,
            "dtstart" to dtstart, "dtend" to dtend, "duration" to duration, "allDay" to 0, "eventTimezone" to "UTC",
            "rrule" to rrule, "rdate" to null, "exrule" to null, "exdate" to null, "original_id" to originalId,
            "originalInstanceTime" to originalInstanceTime, "availability" to 0, "eventStatus" to status, "displayColor" to null,
            "_sync_id" to syncId, "deleted" to 0,
        )
    }

    fun reminder(eventId: Long, minutes: Int, method: Int = 1) {
        reminders += mutableMapOf("_id" to nextId++, "event_id" to eventId, "minutes" to minutes, "method" to method)
    }

    fun instance(eventId: Long, title: String?, begin: Long, calendarId: Long) {
        instances += mutableMapOf("event_id" to eventId, "title" to title, "begin" to begin, "end" to begin + 3_600_000L, "calendar_id" to calendarId)
    }

    fun eventRow(id: Long): MutableMap<String, Any?>? = events.firstOrNull { it["_id"] == id }

    // ------------------------------------------------------------------------------------------ the four calls

    override fun query(uri: ProviderUri, columns: List<String>, where: String?, args: List<String>, sort: String?): List<ProviderRow>? {
        if (throwOnQuery(uri)) throw SecurityException("Permission Denial: reading ${uri.table}")
        if (noAnswer(uri)) return null
        var rows = matching(uri, where, args)
        if (sort != null && sort.startsWith("minutes")) rows = rows.sortedBy { (it["minutes"] as Number).toLong() }
        if (sort != null && sort.startsWith("begin")) rows = rows.sortedBy { (it["begin"] as Number).toLong() }
        if (sort != null && sort.startsWith("account_name")) rows = rows.sortedWith(compareBy({ it["account_name"].toString().lowercase() }, { it["calendar_displayName"].toString().lowercase() }))
        return rows.map { row -> ProviderRow(columns.map { row[it] }) }
    }

    override fun insert(uri: ProviderUri, values: Map<String, Any?>): String? {
        val write = Write("insert", uri, LinkedHashMap(values))
        writes += write
        val id = nextId++
        when (uri.table) {
            ProviderTable.CALENDARS -> calendars += HashMap(values).apply { put("_id", id) }
            ProviderTable.EVENTS -> events += blankEvent(id).apply { putAll(values) }
            ProviderTable.EXCEPTIONS -> {
                val master = events.first { it["_id"] == uri.ids.single() }
                // The provider clones the master, points the new row at it and applies the values; an exception does not repeat.
                events += HashMap(master).apply {
                    put("_id", id)
                    put("original_id", uri.ids.single())
                    put("rrule", null)
                    put("_sync_id", null)
                    putAll(values)
                }
            }
            ProviderTable.REMINDERS -> reminders += HashMap(values).apply { put("_id", id) }
            else -> error("the fake takes no insert into ${uri.table}")
        }
        afterWrite(write)
        val path = when (uri.table) {
            ProviderTable.CALENDARS -> "calendars"
            ProviderTable.REMINDERS -> "reminders"
            else -> "events"
        }
        val query = uri.syncAdapterAccount?.let { "?caller_is_syncadapter=true&account_name=$it&account_type=LOCAL" }.orEmpty()
        return "content://com.android.calendar/$path/$id$query"
    }

    override fun update(uri: ProviderUri, values: Map<String, Any?>, where: String?, args: List<String>): Int {
        val write = Write("update", uri, LinkedHashMap(values), where, args)
        writes += write
        val rows = matching(uri, where, args)
        rows.forEach { it.putAll(values) }
        afterWrite(write)
        return rows.size
    }

    override fun delete(uri: ProviderUri, where: String?, args: List<String>): Int {
        val write = Write("delete", uri, where = where, args = args)
        writes += write
        val rows = matching(uri, where, args)
        table(uri).removeAll { row -> rows.any { it === row } }
        if (uri.table == ProviderTable.EVENTS) reminders.removeAll { r -> rows.any { it["_id"] == r["event_id"] } }
        afterWrite(write)
        return rows.size
    }

    // ------------------------------------------------------------------------------------------ how it matches

    private fun blankEvent(id: Long): MutableMap<String, Any?> = mutableMapOf("_id" to id, "deleted" to 0, "availability" to 0, "allDay" to 0)

    private fun table(uri: ProviderUri): MutableList<MutableMap<String, Any?>> = when (uri.table) {
        ProviderTable.CALENDARS -> calendars
        ProviderTable.EVENTS -> events
        ProviderTable.REMINDERS -> reminders
        ProviderTable.ALERTS -> alerts
        ProviderTable.INSTANCES -> instances
        ProviderTable.EXCEPTIONS -> error("the exception URI takes inserts only")
    }

    private fun matching(uri: ProviderUri, where: String?, args: List<String>): List<MutableMap<String, Any?>> {
        var rows: List<MutableMap<String, Any?>> = table(uri)
        if (uri.table == ProviderTable.INSTANCES) {
            val (from, to) = uri.ids
            rows = rows.filter { (it["begin"] as Number).toLong() <= to && (it["end"] as Number).toLong() >= from }
        } else if (uri.ids.isNotEmpty()) {
            rows = rows.filter { it["_id"] == uri.ids.single() }
        }
        if (where == null) return rows
        val left = ArrayDeque(args)
        for (clause in where.split(" AND ")) {
            val equals = Regex("""^(\w+) = \?$""").matchEntire(clause)
            val notNumber = Regex("""^(\w+) != (\d+)$""").matchEntire(clause)
            val among = Regex("""^(\w+) IN \(([\d, ]*)\)$""").matchEntire(clause)
            val atMost = Regex("""^(\w+) <= \?$""").matchEntire(clause)
            rows = when {
                equals != null -> left.removeFirst().let { arg -> rows.filter { it[equals.groupValues[1]]?.toString() == arg } }
                notNumber != null -> rows.filter { ((it[notNumber.groupValues[1]] as Number?)?.toLong() ?: 0L) != notNumber.groupValues[2].toLong() }
                among != null -> among.groupValues[2].split(",").map { it.trim().toLong() }.let { ids -> rows.filter { (it[among.groupValues[1]] as Number?)?.toLong() in ids } }
                atMost != null -> left.removeFirst().toLong().let { max -> rows.filter { (it[atMost.groupValues[1]] as Number).toLong() <= max } }
                else -> error("the fake does not know the selection: $clause")
            }
        }
        check(left.isEmpty()) { "selection arguments left over: $left" }
        return rows
    }
}
