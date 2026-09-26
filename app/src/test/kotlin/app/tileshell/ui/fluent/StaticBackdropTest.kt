package app.tileshell.ui.fluent

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The static layer's bookkeeping (phase 13 gate review A, B1): a build cancelled because acrylic turned off again is not
 * a failure. Recorded as one, it made the next "on" for the same key return without building, so the app list drew the
 * fallback under `acrylic=on` with a false `static backdrop failed` line.
 */
class StaticBackdropTest {
    private val key = StaticBackdrop.Key("content://media/external/images/media/1", IntSize(1080, 2196), Rgb(0, 0, 0), 90f)

    @Before
    fun clean() = StaticBackdrop.drop()

    @Test
    fun aCancelledBuildIsNotRecordedAndTheNextOneRuns() = runBlocking {
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            StaticBackdrop.build(key) { awaitCancellation() }
        }
        cancelled.cancel()
        cancelled.join()
        yield()
        assertNull("a cancelled build leaves no layer and no failure", StaticBackdrop.layer.value)
        var tries = 0
        StaticBackdrop.build(key) { tries++; throw IOException("gone") }
        assertEquals("the next build for the same key runs", 1, tries)
    }

    @Test
    fun aRealFailureIsRecordedOncePerKey() = runBlocking {
        var tries = 0
        StaticBackdrop.build(key) { tries++; throw IOException("gone") }
        assertTrue(StaticBackdrop.layer.value is StaticBackdrop.Layer.Failed)
        StaticBackdrop.build(key) { tries++; throw IOException("gone") }
        assertEquals("a real failure is not retried for the same key", 1, tries)
    }

    @Test
    fun dropForgetsAFailure() = runBlocking {
        StaticBackdrop.build(key) { throw IOException("gone") }
        StaticBackdrop.drop()
        var tries = 0
        StaticBackdrop.build(key) { tries++; throw IOException("gone") }
        assertEquals("after a drop the key is built again", 1, tries)
    }
}
