package app.tileshell.cortana.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AskedLineTest {
    @Test fun `a spoken all-caps query reads in sentence case`() {
        assertEquals("What's on my calendar", askedLine("WHAT'S ON MY CALENDAR"))
    }

    @Test fun `typed text is shown exactly as typed`() {
        assertEquals("what time is it", askedLine("what time is it"))
        assertEquals("Call Mom", askedLine(" Call Mom "))
    }
}
