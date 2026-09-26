package app.tileshell.start

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** [SingleFlight]: the Start picture is decoded once however many pages ask for it at once (phase 13 gate round 2, A). */
class SingleFlightTest {

    @Test
    fun concurrentCallersForOneKeyShareOneRun() = runBlocking {
        val flights = SingleFlight<String, Any>(this)
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val a = async(start = CoroutineStart.UNDISPATCHED) { flights.run("pic") { runs++; gate.await(); Any() } }
        val b = async(start = CoroutineStart.UNDISPATCHED) { flights.run("pic") { runs++; gate.await(); Any() } }
        yield()
        gate.complete(Unit)
        val first = a.await()
        assertSame("both callers hold the same value, not two copies", first, b.await())
        assertEquals("one run for one key", 1, runs)
    }

    @Test
    fun differentKeysRunSeparately() = runBlocking {
        val flights = SingleFlight<String, String>(this)
        var runs = 0
        val a = async { flights.run("one") { runs++; "1" } }
        val b = async { flights.run("two") { runs++; "2" } }
        assertEquals("1", a.await())
        assertEquals("2", b.await())
        assertEquals(2, runs)
    }

    @Test
    fun aCancelledCallerDoesNotCancelTheRunForTheOthers() = runBlocking {
        val flights = SingleFlight<String, String>(this)
        val gate = CompletableDeferred<Unit>()
        val quitter = launch(start = CoroutineStart.UNDISPATCHED) { flights.run("pic") { gate.await(); "decoded" } }
        val stayer = async(start = CoroutineStart.UNDISPATCHED) { flights.run("pic") { "second run" } }
        quitter.cancel()
        yield()
        gate.complete(Unit)
        assertEquals("the waiting caller gets the shared run's value", "decoded", stayer.await())
    }

    @Test
    fun aFinishedRunIsNotReusedForALaterCall() = runBlocking {
        val flights = SingleFlight<String, Int>(this)
        var runs = 0
        flights.run("pic") { ++runs }
        assertEquals(2, flights.run("pic") { ++runs })
    }
}
