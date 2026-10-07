package app.tileshell.clock

import app.tileshell.clock.AlarmApiRules.Request
import app.tileshell.tiles.api.LiveTileProtocol
import app.tileshell.tiles.engine.TileRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * Ledger L18-3 (the adversarial review's N1 and N4): no intent can throw in the clock's two exported activities, and
 * the editor's hand-off bundle is honoured only from the shell and only when every field passes the API's own checks.
 */
class ClockIntentsTest {
    private val shell = 10077
    private val stranger = 10123

    private class Bad : RuntimeException("android.os.BadParcelableException stand-in")

    private fun port(vararg pairs: Pair<String, Any?>) = ExtrasPort { key -> mapOf(*pairs)[key] }
    private val throwing = ExtrasPort { throw Bad() }
    private fun alarmBundle(vararg more: Pair<String, Any?>) = port(
        ClockIntents.API_KIND to "alarm", ClockIntents.API_HOUR to 6, ClockIntents.API_MINUTE to 45, ClockIntents.API_DAYS to intArrayOf(1, 3),
        ClockIntents.API_MESSAGE to "Gym", ClockIntents.API_SOUND_KIND to "DEFAULT", *more,
    )
    private fun open(bundle: Any?, caller: Int = shell, vararg more: Pair<String, Any?>) =
        ClockIntents.open(port(ClockIntents.EXTRA_API_EDIT to bundle, *more), caller, shell)

    // ------------------------------------------------------------------------------------------------ the reader

    @Test fun `a port that throws for every read never throws through the reader - each value is null and noted`() {
        val safe = SafeExtras(throwing)
        assertNull(safe.string("a", 10)); assertNull(safe.int("b")); assertNull(safe.long("c")); assertNull(safe.boolean("d"))
        assertNull(safe.ints("e", 7)); assertNull(safe.nested("f"))
        assertEquals(6, safe.refused.size)
        assertEquals("extra a refused (threw Bad)", safe.refused.first())
        // An Error is caught too, and a port that is not there reads as nothing.
        assertNull(SafeExtras(ExtrasPort { throw StackOverflowError() }).int("x"))
        assertNull(SafeExtras(null).string("x", 5))
    }

    @Test fun `a value of the wrong type, an oversized one or a list holding anything but whole numbers is not taken`() {
        val safe = SafeExtras(port("s" to 7, "i" to "7", "l" to 7, "b" to "true", "big" to "x".repeat(11), "list" to arrayListOf<Any?>(1, Bad()), "nulls" to arrayListOf<Any?>(1, null),
            "long" to List(40) { 1 }, "arr" to IntArray(40), "bundle" to "text", "ok" to arrayListOf(1, 2), "okArr" to intArrayOf(3)))
        assertNull(safe.string("s", 10)); assertNull(safe.int("i")); assertNull(safe.long("l")); assertNull(safe.boolean("b")); assertNull(safe.string("big", 10))
        assertNull(safe.ints("list", 7)); assertNull(safe.ints("nulls", 7)); assertNull(safe.ints("long", 32)); assertNull(safe.ints("arr", 32)); assertNull(safe.ints("s", 7)); assertNull(safe.nested("bundle"))
        assertEquals(11, safe.refused.size)
        assertEquals(listOf(1, 2), safe.ints("ok", 7)); assertEquals(listOf(3), safe.ints("okArr", 7))
        // Absent is not refused; and a line never carries the value, and the key only as a token.
        assertNull(safe.string("absent", 10)); assertEquals(11, safe.refused.size)
        assertEquals("extra abx refused (not text)", SafeExtras(port("a\nb [x]" to 1)).apply { string("a\nb [x]", 5) }.refused.single())
        assertFalse(safe.refused.any { it.contains("xxxxxxxxxxx") })
    }

    // ------------------------------------------------------------------------------------------------ ClockActivity

    @Test fun `N1 the review's intent - api_edit with days 99 - throws nothing and opens no editor, from the shell or a stranger`() {
        for (caller in listOf(shell, stranger, -1)) {
            val o = open(alarmBundle(ClockIntents.API_DAYS to intArrayOf(99)), caller)
            assertNull(o.alarm); assertNull(o.timer)
        }
        assertTrue(open(alarmBundle(ClockIntents.API_DAYS to intArrayOf(99))).lines.contains("api edit ignored: days out of range"))
        for (days in listOf(intArrayOf(0), intArrayOf(8), intArrayOf(-1), intArrayOf(1, 2, 99), intArrayOf(Int.MIN_VALUE))) assertNull(open(alarmBundle(ClockIntents.API_DAYS to days)).alarm)
    }

    @Test fun `a stranger's bundle is ignored with a line, however well formed - and is not even read`() {
        var read = 0
        val bundle = ExtrasPort { read++; "alarm" }
        for (caller in listOf(stranger, 0, 1000, -1, ClockIntents.NO_CALLER, shell + 1, Int.MAX_VALUE)) {
            val o = open(alarmBundle(), caller)
            assertNull("caller $caller", o.alarm); assertNull(o.timer); assertNull(o.tab)
            assertTrue(o.lines.contains("api edit ignored: not the shell"))
            assertNull(open(bundle, caller).alarm)
        }
        assertEquals(0, read)
        // The shell's own uid unknown (below 0) is nobody's either.
        assertNull(ClockIntents.open(port(ClockIntents.EXTRA_API_EDIT to alarmBundle()), -1, -1).alarm)
        // A stranger's page still only chooses the tab.
        assertEquals(ClockTab.TIMER, open(alarmBundle(), stranger, ClockIntents.EXTRA_PAGE to "timer").tab)
    }

    @Test fun `the shell's own bundle opens the editor filled in with exactly what the handler validated`() {
        val o = open(alarmBundle())
        assertEquals(AlarmDraft.new().copy(hour = 6, minute = 45, name = "Gym", days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), o.alarm)
        assertEquals(ClockTab.ALARM, o.tab)
        assertTrue(o.lines.last().endsWith("api=alarm -> tab alarm"))
        assertEquals(AlarmSound(AlarmSound.Kind.VIBRATE), open(alarmBundle(ClockIntents.API_SOUND_KIND to "VIBRATE")).alarm!!.sound)
        // No hour: the editor's own default time.
        assertEquals(7, open(port(ClockIntents.API_KIND to "alarm", ClockIntents.API_MINUTE to 0, ClockIntents.API_MESSAGE to "")).alarm!!.hour)
        val t = open(port(ClockIntents.API_KIND to "timer", ClockIntents.API_TIMER_LENGTH_MS to 125_000L, ClockIntents.API_MESSAGE to "Tea"))
        assertEquals(TimerDraft(null, 0, 2, 5, "Tea"), t.timer); assertEquals(ClockTab.TIMER, t.tab)
        assertEquals(TimerDraft.new().copy(name = "Tea"), open(port(ClockIntents.API_KIND to "timer", ClockIntents.API_MESSAGE to "Tea")).timer)
    }

    @Test fun `a bundle can never carry what the API would refuse - one bad field and it is ignored whole, even from the shell`() {
        val bad = listOf(
            ClockIntents.API_HOUR to 24, ClockIntents.API_HOUR to 99, ClockIntents.API_HOUR to -1, ClockIntents.API_HOUR to "6",
            ClockIntents.API_MINUTE to 60, ClockIntents.API_MINUTE to -1, ClockIntents.API_MINUTE to 6L,
            ClockIntents.API_MESSAGE to "x".repeat(65), ClockIntents.API_MESSAGE to "a\nb", ClockIntents.API_MESSAGE to "a\u0000", ClockIntents.API_MESSAGE to "x".repeat(5_000), ClockIntents.API_MESSAGE to 5,
            ClockIntents.API_DAYS to arrayListOf<Any?>(Bad()), ClockIntents.API_DAYS to "1", ClockIntents.API_DAYS to IntArray(33) { 1 },
            ClockIntents.API_KIND to "other", ClockIntents.API_KIND to null, ClockIntents.API_KIND to 1,
            ClockIntents.API_SOUND_KIND to 3, ClockIntents.API_SOUND_URI to 3, ClockIntents.API_TIMER_LENGTH_MS to "x",
        )
        for (field in bad) {
            val o = open(alarmBundle(field))
            assertNull("$field", o.alarm); assertNull("$field", o.timer)
            assertTrue("$field: ${o.lines}", o.lines.any { it.startsWith("api edit ignored: ") })
        }
        for (length in listOf(0L, -1L, ClockTimer.MAX_LENGTH_MS + 1, Long.MAX_VALUE, Long.MIN_VALUE)) {
            assertNull("$length", open(port(ClockIntents.API_KIND to "timer", ClockIntents.API_TIMER_LENGTH_MS to length)).timer)
        }
        // The bundle itself of another type, or one whose every read throws (another app's own Parcelable inside it).
        assertNull(open("text").alarm); assertNull(open(7).alarm); assertNull(open(throwing).alarm); assertNull(open(throwing).timer)
        // A sound the bundle names is still weighed as for a caller nobody can be asked about (L18-2).
        assertEquals(AlarmSound.DEFAULT, open(alarmBundle(ClockIntents.API_SOUND_KIND to "TONE", ClockIntents.API_SOUND_URI to "file:///sdcard/a.mp3")).alarm!!.sound)
    }

    @Test fun `no extras of any kind can throw in open - a throwing port opens Clock as it would with none`() {
        for (caller in listOf(shell, stranger)) {
            val o = ClockIntents.open(throwing, caller, shell)
            assertNull(o.tab); assertNull(o.focusedTimer); assertNull(o.alarm); assertNull(o.timer)
            assertTrue(o.lines.isNotEmpty())
        }
        assertEquals(ClockOpen(), ClockIntents.open(null, shell, shell))
        // Wrong types for the page and the tile.
        val o = ClockIntents.open(port(ClockIntents.EXTRA_PAGE to 5, LiveTileProtocol.EXTRA_LAUNCH_TILE_ID to arrayListOf(1)), stranger, shell)
        assertNull(o.tab); assertNull(o.focusedTimer)
        assertTrue(ClockIntents.open(ExtrasPort { throw Bad() }, stranger, shell).lines.contains("api edit ignored: not the shell"))
    }

    @Test fun `the page and the tile only choose a tab, and reach the line as tokens`() {
        fun tab(vararg e: Pair<String, Any?>) = ClockIntents.open(port(*e), stranger, shell)
        assertEquals(ClockTab.STOPWATCH, tab(ClockIntents.EXTRA_PAGE to "stopwatch").tab)
        assertNull(tab(ClockIntents.EXTRA_PAGE to "nowhere").tab)
        assertEquals(ClockTab.STOPWATCH, tab(LiveTileProtocol.EXTRA_LAUNCH_TILE_ID to TileRouting.STOPWATCH_TILE_ID).tab)
        val timer = tab(LiveTileProtocol.EXTRA_LAUNCH_TILE_ID to TileRouting.TIMER_TILE_PREFIX + "t1a2b3c4d")
        assertEquals(ClockTab.TIMER, timer.tab); assertEquals("t1a2b3c4d", timer.focusedTimer)
        for (id in listOf("", "a b", "a\nb", "../x", "x".repeat(41))) assertNull(id, tab(LiveTileProtocol.EXTRA_LAUNCH_TILE_ID to TileRouting.TIMER_TILE_PREFIX + id).focusedTimer)
        val line = tab(ClockIntents.EXTRA_PAGE to "alarm\n[clock] forged ‮", LiveTileProtocol.EXTRA_LAUNCH_TILE_ID to "x y\r\nz").lines.single()
        assertEquals("open page=alarmclockforged tile=xyz api=none -> tab unchanged", line)
    }

    // ------------------------------------------------------------------------------------------------ AlarmApiActivity

    @Test fun `N4 a request with an extra that could not be read is refused whole, and its line carries no caller text`() {
        val ok = Request.ShowAlarms
        assertEquals(ok, AlarmApiRules.unlessUnreadable(ok, 0))
        assertEquals(Request.Refused("an extra could not be read"), AlarmApiRules.unlessUnreadable(ok, 1))
        assertTrue(AlarmApiRules.unlessUnreadable(Request.CreateAlarm(AlarmApiRules.AlarmFields(6, 0, emptySet(), "", AlarmSound.DEFAULT)), 3) is Request.Refused)
        assertEquals("api android.intent.action.SET_ALARM from com.example -> created a1", AlarmApiRules.line("android.intent.action.SET_ALARM", "com.example", "created a1"))
        assertEquals("api xalarmsforged from ab -> refused: unsupported action x[alarms] forged", AlarmApiRules.line("x\n[alarms] forged", "a\r\nb", "refused: unsupported action x\n[alarms] forged"))
        assertEquals("api (none) from ? -> x", AlarmApiRules.line(null, null, "x"))
        // The shared checks are the ones parse applies.
        assertTrue(AlarmApiRules.hourOk(0) && AlarmApiRules.hourOk(23) && !AlarmApiRules.hourOk(24) && !AlarmApiRules.hourOk(99) && !AlarmApiRules.hourOk(-1))
        assertTrue(AlarmApiRules.minuteOk(59) && !AlarmApiRules.minuteOk(60) && AlarmApiRules.dayOk(1) && AlarmApiRules.dayOk(7) && !AlarmApiRules.dayOk(0) && !AlarmApiRules.dayOk(8))
        assertNull(AlarmApiRules.messageRefusal("x".repeat(64)))
        assertEquals("message longer than 64 characters", AlarmApiRules.messageRefusal("x".repeat(65)))
        assertEquals("message holds a control character", AlarmApiRules.messageRefusal("a\nb"))
    }

    @Test fun `who launched Clock is the port's named uid, and nobody when it names none or throws`() {
        val base = object : app.tileshell.media.UriAccessPort {
            var from: app.tileshell.media.Platform<Int> = app.tileshell.media.Platform.Said(shell)
            override val apiLevel = 36
            override fun shellUid() = shell
            override fun uidOf(packageName: String) = app.tileshell.media.Platform.Threw("x")
            override fun provider(authority: String) = app.tileshell.media.Platform.Threw("x")
            override fun holdsGrant(uri: String, uid: Int, modeFlag: Int) = app.tileshell.media.Platform.Threw("x")
            override fun providerSays(uri: String, uid: Int, modeFlag: Int) = app.tileshell.media.Platform.Threw("x")
            override fun launchAnswer(uri: String, modeFlag: Int) = app.tileshell.media.Platform.Threw("x")
            override fun launchedFromUid() = from
            override fun nameOfUid(uid: Int) = app.tileshell.media.Platform.Threw("x")
        }
        assertEquals(shell, ClockIntents.launchCaller(base))
        base.from = app.tileshell.media.Platform.Said(-1); assertEquals(ClockIntents.NO_CALLER, ClockIntents.launchCaller(base))
        base.from = app.tileshell.media.Platform.Threw("SecurityException"); assertEquals(ClockIntents.NO_CALLER, ClockIntents.launchCaller(base))
    }
}
