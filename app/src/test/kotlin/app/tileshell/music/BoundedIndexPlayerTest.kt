package app.tileshell.music

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.tileshell.diag.Diagnostics
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ledger L18-5 (adversarial pass 3, P3-1; RUN on the emulator, 2026-10-20: any app killed the launcher): the player
 * Music's session is built on, as MusicService builds it — [BoundedIndexPlayer] over the real [KnownDurationPlayer] over
 * the ExoPlayer. The ExoPlayer here is [Exo]: ExoPlayer 1.9.0 where an index matters (read from its bytecode — it throws
 * IllegalArgumentException where `ExoPlayerImpl`'s `checkArgument` does, after `BasePlayer`'s `index + 1` for a single
 * remove / replace / move), recording every index command that reaches it. `media/UriAccessWiringScanTest` holds the
 * service to building the session on exactly this.
 */
class BoundedIndexPlayerTest {
    private val uid = 10_187

    /** What MusicService hands `MediaSession.Builder`: the bound over the known-duration player over the ExoPlayer. */
    private fun session(exo: Exo): Player = BoundedIndexPlayer(KnownDurationPlayer(exo.player)) { uid }

    private val item = MediaItem.Builder().setMediaId("7").build()

    /** The `[music]` lines written while [block] ran. */
    private fun musicLinesDuring(block: () -> Unit): List<String> {
        val mark = Diagnostics.snapshot().lastOrNull()
        block()
        val all = Diagnostics.snapshot()
        val from = if (mark == null) 0 else all.indexOfLast { it === mark } + 1
        return all.drop(from).filter { it.tag == "music" }.map { it.message }
    }

    private fun refused(call: String, count: Int) = "controller uid $uid: $call refused, the queue has $count item(s)"

    @Test fun `the device run - a legacy skipToQueueItem of 2^31, 2^32 - 2 and 2^32 - 1 never reaches the player, and each says so`() {
        val exo = Exo(count = 3)
        val p = session(exo)
        // What MediaSessionLegacyStub.onSkipToQueueItem calls for each (l2i of the queue id).
        val lines = musicLinesDuring {
            p.seekToDefaultPosition(2_147_483_648L.toInt())
            p.seekToDefaultPosition(4_294_967_294L.toInt())
            p.seekToDefaultPosition(4_294_967_295L.toInt())
        }
        assertEquals(emptyList<String>(), exo.reached)
        assertEquals(
            listOf(
                refused("seekToDefaultPosition(-2147483648)", 3),
                refused("seekToDefaultPosition(-2)", 3),
                refused("seekToDefaultPosition(-1)", 3),
            ),
            lines,
        )
    }

    @Test fun `a Media3 controller's remove, replace or move of Int MAX_VALUE never reaches the player`() {
        val exo = Exo(count = 3)
        val p = session(exo)
        val lines = musicLinesDuring {
            p.removeMediaItem(Int.MAX_VALUE)
            p.replaceMediaItem(Int.MAX_VALUE, item)
            p.moveMediaItem(Int.MAX_VALUE, 0)
            // The session's own replace of a list longer than one: its index + 1 has already wrapped.
            p.replaceMediaItems(Int.MAX_VALUE, Int.MIN_VALUE, listOf(item, item))
        }
        assertEquals(emptyList<String>(), exo.reached)
        assertEquals(
            listOf(
                refused("removeMediaItem(2147483647)", 3),
                refused("replaceMediaItem(2147483647)", 3),
                refused("moveMediaItem(2147483647, 0)", 3),
                refused("replaceMediaItems(2147483647, -2147483648)", 3),
            ),
            lines,
        )
    }

    /** Every index-taking command, each with the indices its list does not have: none throws, none reaches, each says so. */
    @Test fun `every index the list does not have, of every index-taking command, is refused before the player`() {
        val hostile = listOf(Int.MIN_VALUE, -2, -1, Int.MAX_VALUE)
        for (count in listOf(0, 1, 3)) {
            val exo = Exo(count)
            val p = session(exo)
            val calls = mutableListOf<Pair<String, () -> Unit>>()
            for (i in hostile + count + (count + 1)) {
                calls += "seekTo($i, 5000)" to { p.seekTo(i, 5_000L) }
                calls += "seekToDefaultPosition($i)" to { p.seekToDefaultPosition(i) }
                calls += "removeMediaItem($i)" to { p.removeMediaItem(i) }
                calls += "replaceMediaItem($i)" to { p.replaceMediaItem(i, item) }
                calls += "moveMediaItem($i, 0)" to { p.moveMediaItem(i, 0) }
            }
            for (i in hostile.filter { it < 0 }) {
                calls += "moveMediaItem(0, $i)" to { p.moveMediaItem(0, i) }
                calls += "addMediaItem($i)" to { p.addMediaItem(i, item) }
                calls += "addMediaItems($i)" to { p.addMediaItems(i, listOf(item)) }
                calls += "removeMediaItems($i, 1)" to { p.removeMediaItems(i, 1) }
                calls += "moveMediaItems($i, 1, 0)" to { p.moveMediaItems(i, 1, 0) }
                calls += "moveMediaItems(0, 0, $i)" to { p.moveMediaItems(0, 0, i) }
                calls += "replaceMediaItems($i, 1)" to { p.replaceMediaItems(i, 1, listOf(item)) }
            }
            // Backwards, and starting past the end.
            calls += "removeMediaItems(1, 0)" to { p.removeMediaItems(1, 0) }
            calls += "moveMediaItems(1, 0, 0)" to { p.moveMediaItems(1, 0, 0) }
            calls += "replaceMediaItems(1, 0)" to { p.replaceMediaItems(1, 0, listOf(item)) }
            calls += "removeMediaItems(${count + 1}, ${count + 2})" to { p.removeMediaItems(count + 1, count + 2) }
            calls += "replaceMediaItems(${count + 1}, ${count + 2})" to { p.replaceMediaItems(count + 1, count + 2, listOf(item)) }
            for ((call, run) in calls) {
                val lines = musicLinesDuring { run() }
                assertEquals("$call, a queue of $count", emptyList<String>(), exo.reached)
                assertEquals("$call, a queue of $count", listOf(refused(call, count)), lines)
            }
        }
    }

    @Test fun `what the shell's own controller sends reaches the player exactly as before, and writes nothing`() {
        val exo = Exo(count = 3)
        val p = session(exo)
        val lines = musicLinesDuring {
            for (i in 0 until 3) p.seekTo(i, 0L)   // MusicPlayer.playAt: a row of the open queue
            p.seekTo(41_000L)                     // the scrubber
            p.seekTo(0L)                          // "previous" near the start
            p.seekToNextMediaItem()
            p.seekToPreviousMediaItem()
            p.seekToDefaultPosition(2)
            p.seekTo(1, C.TIME_UNSET)
        }
        assertEquals(
            listOf("seekTo(0, 0)", "seekTo(1, 0)", "seekTo(2, 0)", "seekTo(41000)", "seekTo(0)", "seekToNextMediaItem()", "seekToPreviousMediaItem()",
                "seekToDefaultPosition(2)", "seekTo(1, ${C.TIME_UNSET})"),
            exo.reached,
        )
        assertEquals(emptyList<String>(), lines)
    }

    @Test fun `what the Player contract clamps reaches the player as it was sent - the same items, the same indices`() {
        val exo = Exo(count = 3)
        val p = session(exo)
        val more = listOf(item, MediaItem.Builder().setMediaId("8").build())
        val lines = musicLinesDuring {
            p.removeMediaItems(0, Int.MAX_VALUE)     // a Media3 controller's clearMediaItems()
            p.addMediaItems(99, more)                // past the end: added at the end
            p.addMediaItem(3, item)
            p.moveMediaItem(0, 99)                   // to the end
            p.moveMediaItems(1, 99, 0)
            p.removeMediaItem(2)
            p.replaceMediaItem(0, item)
            p.replaceMediaItems(3, 5, more)          // at the end: appended
            p.addMediaItems(more)                    // no index at all
        }
        assertEquals(
            listOf("removeMediaItems(0, 2147483647)", "addMediaItems(99)", "addMediaItem(3)", "moveMediaItem(0, 99)", "moveMediaItems(1, 99, 0)",
                "removeMediaItem(2)", "replaceMediaItem(0)", "replaceMediaItems(3, 5)", "addMediaItems()"),
            exo.reached,
        )
        assertSame(more, exo.items[1])
        assertSame(item, exo.items[2])
        assertSame(item, exo.items[6])
        assertSame(more, exo.items[7])
        assertEquals(emptyList<String>(), lines)
    }

    /**
     * Every command of Media3's [Player] that takes an index is bounded here — a new one in a later Media3 fails this.
     * Not indices: the repeat mode, a command's number, the device volume and its flags. Not a controller's: the start of
     * `setMediaItems(items, index, position)`, which the session hands the player only from MusicQueueStart (L18-4), and
     * `getMediaItemAt`, a read the session makes with indices of its own.
     */
    @Test fun `every index-taking command of Media3's Player is bounded here`() {
        fun sig(m: Method) = "${m.name}(${m.parameterTypes.joinToString { it.simpleName }})"
        val notIndices = setOf(
            "isCommandAvailable(int)", "setRepeatMode(int)", "setDeviceVolume(int)", "setDeviceVolume(int, int)",
            "increaseDeviceVolume(int)", "decreaseDeviceVolume(int)", "setDeviceMuted(boolean, int)",
            "setMediaItems(List, int, long)", "getMediaItemAt(int)",
        )
        val indexed = Player::class.java.methods.filter { m -> m.parameterTypes.any { it == Int::class.javaPrimitiveType } }.map(::sig).toSet() - notIndices
        val bounded = BoundedIndexPlayer::class.java.declaredMethods.filter { m ->
            runCatching { Player::class.java.getMethod(m.name, *m.parameterTypes) }.isSuccess
        }.map(::sig).toSet()
        assertEquals(10, indexed.size)
        assertEquals(indexed.sorted(), bounded.sorted())
    }

    /**
     * ExoPlayer 1.9.0 where an index matters, read from its bytecode: `BasePlayer.removeMediaItem(i)` is
     * `removeMediaItems(i, i + 1)`, `replaceMediaItem(i, x)` is `replaceMediaItems(i, i + 1, [x])`, `moveMediaItem(c, n)`
     * is `moveMediaItems(c, c + 1, n)` unless `c == n`, `addMediaItem(i, x)` is `addMediaItems(i, [x])`; and
     * `ExoPlayerImpl` ignores a seek to −1 (C.INDEX_UNSET), then `checkArgument`s `index >= 0` (seekTo), `from >= 0 &&
     * to >= from` (remove, replace), `from >= 0 && from <= to && new >= 0` (move) and `index >= 0` (add). Each command
     * that reaches it is recorded first, as it arrived.
     */
    private class Exo(val count: Int) {
        val reached = mutableListOf<String>()
        val items = mutableListOf<Any?>()
        val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, m, args -> answer(m, args.orEmpty()) } as Player

        private fun check(ok: Boolean) { if (!ok) throw IllegalArgumentException() }

        private fun reach(call: String, list: Any? = null) { reached += call; items += list }

        private fun answer(m: Method, a: Array<out Any?>): Any? {
            val indexed = a.firstOrNull() is Int
            when (m.name) {
                "getMediaItemCount" -> return count
                "seekTo" -> if (a.size == 2) {
                    val i = a[0] as Int
                    reach("seekTo($i, ${a[1]})")
                    if (i != C.INDEX_UNSET) check(i >= 0)
                } else reach("seekTo(${a[0]})")
                "seekToDefaultPosition" -> if (a.size == 1) {
                    val i = a[0] as Int
                    reach("seekToDefaultPosition($i)")
                    if (i != C.INDEX_UNSET) check(i >= 0)
                }
                "seekToNextMediaItem", "seekToPreviousMediaItem" -> reach("${m.name}()")
                "removeMediaItem" -> { val i = a[0] as Int; reach("removeMediaItem($i)"); removes(i, i + 1) }
                "removeMediaItems" -> { val f = a[0] as Int; val t = a[1] as Int; reach("removeMediaItems($f, $t)"); removes(f, t) }
                "replaceMediaItem" -> { val i = a[0] as Int; reach("replaceMediaItem($i)", a[1]); removes(i, i + 1) }
                "replaceMediaItems" -> { val f = a[0] as Int; val t = a[1] as Int; reach("replaceMediaItems($f, $t)", a[2]); removes(f, t) }
                "moveMediaItem" -> { val c = a[0] as Int; val n = a[1] as Int; reach("moveMediaItem($c, $n)"); if (c != n) moves(c, c + 1, n) }
                "moveMediaItems" -> { val f = a[0] as Int; val t = a[1] as Int; val n = a[2] as Int; reach("moveMediaItems($f, $t, $n)"); moves(f, t, n) }
                "addMediaItem" -> if (indexed) { val i = a[0] as Int; reach("addMediaItem($i)", a[1]); check(i >= 0) } else reach("addMediaItem()", a[0])
                "addMediaItems" -> if (indexed) { val i = a[0] as Int; reach("addMediaItems($i)", a[1]); check(i >= 0) } else reach("addMediaItems()", a[0])
            }
            return when (m.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                else -> null
            }
        }

        private fun removes(from: Int, to: Int) = check(from >= 0 && to >= from)
        private fun moves(from: Int, to: Int, new: Int) = check(from >= 0 && from <= to && new >= 0)
    }
}
