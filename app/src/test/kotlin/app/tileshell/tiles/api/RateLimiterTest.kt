package app.tileshell.tiles.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test fun sixtyPerMinuteThenRate() {
        val limiter = RateLimiter(limit = 60, windowMs = 60_000)
        repeat(60) { i -> assertTrue("call $i", limiter.tryAcquire("com.example.a", 1_000L + i)) }
        assertFalse(limiter.tryAcquire("com.example.a", 1_060))
        assertFalse(limiter.tryAcquire("com.example.a", 30_000))
    }

    @Test fun callersAreIsolated() {
        val limiter = RateLimiter(limit = 60, windowMs = 60_000)
        repeat(60) { limiter.tryAcquire("com.example.a", 0) }
        assertFalse(limiter.tryAcquire("com.example.a", 1))
        assertTrue(limiter.tryAcquire("com.example.b", 1))
    }

    @Test fun windowSlidesAndRejectionsDoNotConsumeBudget() {
        val limiter = RateLimiter(limit = 60, windowMs = 60_000)
        repeat(60) { i -> limiter.tryAcquire("a", i * 1_000L) } // one per second, t = 0..59 s
        repeat(1_000) { assertFalse(limiter.tryAcquire("a", 59_500)) } // a flood of rejections
        assertTrue(limiter.tryAcquire("a", 60_000)) // the t=0 call aged out: exactly one slot
        assertFalse(limiter.tryAcquire("a", 60_000))
        assertTrue(limiter.tryAcquire("a", 61_000))
    }

    @Test fun retryAfterReportsWhenTheOldestCallAgesOut() {
        val limiter = RateLimiter(limit = 60, windowMs = 60_000)
        repeat(60) { limiter.tryAcquire("a", 10_000) }
        assertEquals(60_000L, limiter.retryAfterMs("a", 10_000))
        assertEquals(15_000L, limiter.retryAfterMs("a", 55_000))
        assertEquals(0L, limiter.retryAfterMs("a", 70_000))
        assertEquals(0L, limiter.retryAfterMs("never-called", 0))
    }

    @Test fun forgetResetsACaller() {
        val limiter = RateLimiter(limit = 2, windowMs = 60_000)
        limiter.tryAcquire("a", 0)
        limiter.tryAcquire("a", 0)
        assertFalse(limiter.tryAcquire("a", 1))
        limiter.forget("a")
        assertTrue(limiter.tryAcquire("a", 2))
    }
}
