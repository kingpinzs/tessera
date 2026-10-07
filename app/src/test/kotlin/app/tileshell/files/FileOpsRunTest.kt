package app.tileshell.files

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18, r3 D8 / V7: the copy service's pure half — one operation at a time, the wait for a conflict's answer, the
 * stop (Cancel, the time limit), the progress line's beat and the notification's words.
 */
class FileOpsRunTest {
    private val bed = FilesBed()
    private val run = FileOpsRun()
    private val dest = File("/storage/emulated/0/sub")

    @After fun tearDown() = bed.close()

    /** Waits until the operation asks, and returns the question. */
    private fun awaitQuestion(): OpState.AwaitingConflict {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < until) {
            (run.state.value as? OpState.AwaitingConflict)?.let { return it }
            Thread.sleep(2)
        }
        throw AssertionError("no conflict was asked; state = ${run.state.value}")
    }

    // ---- one at a time

    @Test
    fun `one operation at a time - a second start is refused until the first has ended`() {
        assertEquals(OpState.Idle, run.state.value)
        assertTrue(run.begin(OpKind.COPY, dest))
        assertEquals(OpState.Running(OpKind.COPY, 0, 0, dest), run.state.value)
        assertFalse(run.begin(OpKind.MOVE, dest))
        assertEquals(OpState.Running(OpKind.COPY, 0, 0, dest), run.state.value)

        run.progress(10, 40)
        assertEquals(OpState.Running(OpKind.COPY, 10, 40, dest), run.state.value)

        run.finish(OpResult.Done(listOf("/storage/emulated/0/sub/b.bin")))
        assertEquals(OpState.Finished(OpKind.COPY, OpResult.Done(listOf("/storage/emulated/0/sub/b.bin")), dest), run.state.value)
        assertFalse(run.active)
        // Finished stays for the page to land on; a new operation may start from it.
        assertTrue(run.begin(OpKind.MOVE, dest))
        run.finish(OpResult.Cancelled)
        run.dismiss()
        assertEquals(OpState.Idle, run.state.value)
    }

    @Test
    fun `nothing running - progress, finish, stop and dismiss change nothing`() {
        run.progress(1, 2)
        run.finish(OpResult.Cancelled)
        assertFalse(run.stop(StopReason.CANCEL))
        run.dismiss()
        assertEquals(OpState.Idle, run.state.value)
        assertNull(run.stopped())
    }

    @Test
    fun `a stop is forgotten by the next operation, and the first reason given is the one kept`() {
        run.begin(OpKind.COPY, dest)
        assertTrue(run.stop(StopReason.TIME_LIMIT))
        assertTrue(run.stop(StopReason.CANCEL))
        assertEquals(StopReason.TIME_LIMIT, run.stopped())
        run.finish(OpResult.Failed(FileOps.TIME_LIMIT))
        run.begin(OpKind.COPY, dest)
        assertNull(run.stopped())
    }

    // ---- the conflict's question

    @Test
    fun `conflict - an answer the page already had is used without asking`() {
        run.begin(OpKind.COPY, dest)
        assertEquals(Conflict.KEEP_BOTH, run.ask(File(dest, "b.bin"), Conflict.KEEP_BOTH))
        assertEquals(OpState.Running(OpKind.COPY, 0, 0, dest), run.state.value)
    }

    @Test
    fun `conflict - the operation waits for the answer, takes the first one and runs on`() {
        run.begin(OpKind.MOVE, dest)
        run.progress(5, 9)
        var got: Conflict? = null
        val worker = thread { got = run.ask(File(dest, "b.bin"), null) }
        val question = awaitQuestion()
        assertEquals(File(dest, "b.bin"), question.file)
        assertEquals(OpKind.MOVE, question.kind)
        assertEquals(5L to 9L, question.bytes to question.total)
        assertTrue(run.active)
        assertFalse("a second operation is refused while the first waits", run.begin(OpKind.COPY, dest))
        Thread.sleep(50)
        assertTrue("it waits", worker.isAlive)

        question.answer(Conflict.REPLACE)
        question.answer(Conflict.SKIP)
        worker.join(10_000)
        assertEquals(Conflict.REPLACE, got)
        assertEquals(OpState.Running(OpKind.MOVE, 5, 9, dest), run.state.value)

        // An old dialog's button, pressed late, answers nothing: the next question still waits for its own answer.
        var second: Conflict? = null
        val again = thread { second = run.ask(File(dest, "c.bin"), null) }
        val next = awaitQuestion()
        question.answer(Conflict.SKIP)
        Thread.sleep(50)
        assertTrue(again.isAlive)
        next.answer(Conflict.KEEP_BOTH)
        again.join(10_000)
        assertEquals(Conflict.KEEP_BOTH, second)
    }

    @Test
    fun `conflict - cancel during the question ends the copy cancelled, with nothing written`() {
        val src = bed.file("QA-Files/b.bin", FilesBed.bytes(200_000))
        val there = bed.file("QA-Files/sub/b.bin", "old".toByteArray())
        run.begin(OpKind.COPY, there.parentFile!!)
        var result: OpResult? = null
        val worker = thread {
            result = bed.ops.copy(listOf(src), there.parentFile!!, OpControl(stopped = run::stopped)) { run.ask(it, null) }
        }
        awaitQuestion()
        assertTrue(run.stop(StopReason.CANCEL))
        worker.join(10_000)
        assertEquals(OpResult.Cancelled, result)
        assertEquals("old", there.readText())
        assertEquals(listOf("b.bin"), bed.names(there.parentFile!!))
        assertEquals(listOf("copy 1 files 200000 -> ${there.parent} cancelled"), bed.lines)
        assertEquals(emptyList<OpsJournal.Entry>(), bed.journal.entries())
    }

    @Test
    fun `conflict - the time limit during the question fails the move as time limit, the original in place`() {
        val src = bed.file("QA-Files/b.bin", FilesBed.bytes(1_000))
        val there = bed.file("QA-Files/sub/b.bin", "old".toByteArray())
        run.begin(OpKind.MOVE, there.parentFile!!)
        var result: OpResult? = null
        val worker = thread {
            result = bed.ops.move(listOf(src), there.parentFile!!, OpControl(stopped = run::stopped)) { run.ask(it, null) }
        }
        awaitQuestion()
        run.stop(StopReason.TIME_LIMIT)
        worker.join(10_000)
        assertEquals(OpResult.Failed(FileOps.TIME_LIMIT), result)
        assertTrue("the original is still there", src.isFile)
        assertEquals("old", there.readText())
        assertEquals("move 1 files 1000 -> ${there.parent} failed time limit", bed.lines.single())
    }

    @Test
    fun `conflict - a stop already given answers the next question at once, even a known one`() {
        run.begin(OpKind.COPY, dest)
        run.stop(StopReason.CANCEL)
        val known = runCatching { run.ask(File(dest, "b.bin"), Conflict.REPLACE) }.exceptionOrNull()
        val asked = runCatching { run.ask(File(dest, "b.bin"), null) }.exceptionOrNull()
        assertEquals(StopReason.CANCEL, (known as Stopped).reason)
        assertEquals(StopReason.CANCEL, (asked as Stopped).reason)
    }

    @Test
    fun `cancel through the service's control - a running copy ends cancelled with no part file`() {
        val src = bed.file("QA-Files/big.bin", FilesBed.bytes(600_000))
        val sub = bed.dir("QA-Files/sub")
        run.begin(OpKind.COPY, sub)
        val control = OpControl(progress = { bytes, _ -> if (bytes >= 2L * Meter.CHUNK) run.stop(StopReason.CANCEL) }, stopped = run::stopped)
        val result = bed.ops.copy(listOf(src), sub, control) { run.ask(it, null) }
        run.finish(result)
        assertEquals(OpState.Finished(OpKind.COPY, OpResult.Cancelled, sub), run.state.value)
        assertEquals(emptyList<String>(), bed.names(sub))
    }

    // ---- the progress line's beat (r3 V7)

    /** A copy of [total] bytes at [bytesPerSecond], one call per chunk: the lines that would be written, with their times. */
    private fun linesOf(total: Long, bytesPerSecond: Long, stallAtChunk: Int = -1, stallMs: Long = 0): List<Pair<Long, String>> {
        var nowNs = 5_000_000_000L
        val beat = ProgressBeat(nanoTime = { nowNs })
        val out = ArrayList<Pair<Long, String>>()
        var bytes = 0L
        var chunk = 0
        while (bytes < total) {
            val n = minOf(Meter.CHUNK.toLong(), total - bytes)
            bytes += n
            nowNs += n * 1_000_000_000L / bytesPerSecond
            if (chunk++ == stallAtChunk) nowNs += stallMs * 1_000_000
            if (beat.due()) out += nowNs / 1_000_000 to FileOpsText.progressLine(OpKind.COPY, bytes, total)
        }
        return out
    }

    @Test
    fun `progress - at least once a second - a paced 200 MB copy writes a line every half second`() {
        val total = 200L * 1024 * 1024
        val lines = linesOf(total, bytesPerSecond = 8_388_608)
        // About 25 s at 8 MB/s: two lines a second, never a second without one.
        assertTrue("lines = ${lines.size}", lines.size in 45..55)
        val gaps = lines.zipWithNext { a, b -> b.first - a.first }
        assertTrue("longest gap ${gaps.max()} ms", gaps.max() < 1_000)
        assertTrue("shortest gap ${gaps.min()} ms", gaps.min() >= 500)
        assertEquals("copy progress 65536/$total", lines.first().second)
    }

    @Test
    fun `progress - one mid line - the first chunk of any operation is a line with 0 less than bytes less than total`() {
        // Unpaced on the AVD: 200 MB in about a fifth of a second, far less than one beat.
        val total = 200L * 1024 * 1024
        val lines = linesOf(total, bytesPerSecond = 900L * 1024 * 1024)
        assertEquals(listOf("copy progress 65536/$total"), lines.map { it.second })
        // And an operation of over a second, however slow its start: the line is written at the first chunk.
        val slow = linesOf(300_000, bytesPerSecond = 100_000)
        val mid = slow.map { it.second.substringAfter("progress ").substringBefore('/').toLong() }.filter { it in 1 until 300_000 }
        assertTrue("mid lines = $mid", mid.isNotEmpty())
    }

    @Test
    fun `progress - after a stall the next chunk writes a line at once`() {
        val lines = linesOf(1_000_000, bytesPerSecond = 8_388_608, stallAtChunk = 3, stallMs = 3_000)
        assertEquals(listOf("copy progress 65536/1000000", "copy progress 262144/1000000"), lines.map { it.second })
    }

    @Test
    fun `progress and pace lines - the doc's grammar for each kind`() {
        assertEquals("copy progress 1/2", FileOpsText.progressLine(OpKind.COPY, 1, 2))
        assertEquals("move progress 1/2", FileOpsText.progressLine(OpKind.MOVE, 1, 2))
        assertEquals("zip extract progress 1/2", FileOpsText.progressLine(OpKind.EXTRACT, 1, 2))
        assertEquals("zip create progress 1/2", FileOpsText.progressLine(OpKind.CREATE_ZIP, 1, 2))
        assertEquals("qa pace 8388608", FileOpsText.paceLine(8_388_608))
    }

    // ---- the notification's words

    @Test
    fun `notification - the operation's name, and the percentage that the app does not show`() {
        assertEquals("Copying files…", FileOpsText.title(OpKind.COPY))
        assertEquals("Moving files…", FileOpsText.title(OpKind.MOVE))
        assertEquals("Extracting…", FileOpsText.title(OpKind.EXTRACT))
        assertEquals("Creating zip…", FileOpsText.title(OpKind.CREATE_ZIP))
        assertEquals("Cancel", FileOpsText.CANCEL)

        assertEquals(FileOpsText.Note("Copying files…", "42%", 42), FileOpsText.note(OpState.Running(OpKind.COPY, 42, 100, dest), filesVisible = false))
        // Before the total is known: no number, and the bar runs without one.
        assertEquals(FileOpsText.Note("Moving files…", "", null), FileOpsText.note(OpState.Running(OpKind.MOVE, 0, 0, dest), filesVisible = true))
        assertNull(FileOpsText.note(OpState.Idle, filesVisible = true))
        assertNull(FileOpsText.note(OpState.Finished(OpKind.COPY, OpResult.Cancelled, dest), filesVisible = true))
    }

    @Test
    fun `notification - percent is whole, never over 100, and does not overflow on a big copy`() {
        assertEquals(0, FileOpsText.percent(0, 10))
        assertEquals(0, FileOpsText.percent(65_536, 200L * 1024 * 1024))
        assertEquals(99, FileOpsText.percent(999, 1_000))
        assertEquals(100, FileOpsText.percent(1_000, 1_000))
        // An extract may write past what the archive declared (the bomb guard's allowance): still 100.
        assertEquals(100, FileOpsText.percent(1_500, 1_000))
        assertEquals(50, FileOpsText.percent(Long.MAX_VALUE / 2, Long.MAX_VALUE))
        assertNull(FileOpsText.percent(5, 0))
        assertNull(FileOpsText.percent(5, -1))
    }

    @Test
    fun `notification - a question nobody can see is said in the notification`() {
        val waiting = OpState.AwaitingConflict(OpKind.EXTRACT, 30, 60, dest, File(dest, "qa")) {}
        assertEquals(FileOpsText.Note("Extracting…", "Waiting for an answer in Files", 50), FileOpsText.note(waiting, filesVisible = false))
        assertEquals(FileOpsText.Note("Extracting…", "50%", 50), FileOpsText.note(waiting, filesVisible = true))
    }
}
