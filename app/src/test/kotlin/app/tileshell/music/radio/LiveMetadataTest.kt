package app.tileshell.music.radio

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 20 (Decisions "live metadata", r3 D9): the title and the album line of a playing station. */
class LiveMetadataTest {
    @Test fun `a null or blank title is the station's name`() {
        for (icy in listOf(null, "", "   ", "\u0000", "\n", "​")) {
            assertEquals("$icy", LiveMetadata.Shown("QA Jazz One", "QA Jazz One"), LiveMetadata.merge("QA Jazz One", icy))
        }
    }

    @Test fun `a StreamTitle is the title, and the album line stays the station`() {
        assertEquals(LiveMetadata.Shown("QA Song 1", "QA Jazz One"), LiveMetadata.merge("QA Jazz One", "QA Song 1"))
        assertEquals(LiveMetadata.Shown("QA Song 2", "QA Jazz One"), LiveMetadata.merge("QA Jazz One", "QA Song 2"))
        assertEquals(LiveMetadata.Shown("Miles Davis - So What", "QA Jazz One"), LiveMetadata.merge("QA Jazz One", "  Miles Davis - So What  "))
    }

    @Test fun `an HLS item never has a StreamTitle - the station's name stands`() {
        assertEquals(LiveMetadata.Shown("QA HLS", "QA HLS"), LiveMetadata.merge("QA HLS", null))
    }

    @Test fun `a StreamTitle passes RadioText - no forged line, cut at 120 - and so does the name, at 80`() {
        val shown = LiveMetadata.merge("Real‮ FM\n" + "n".repeat(200), "Song\n[music] stream: gave up\u0000" + "t".repeat(300))
        assertEquals(120, shown.title.length)
        assertEquals("Song[music] stream: gave up" + "t".repeat(120 - 27), shown.title)
        assertEquals(80, shown.albumTitle.length)
        assertEquals("Real FM" + "n".repeat(73), shown.albumTitle)
    }

    @Test fun `no station name and no title is empty, not null`() {
        assertEquals(LiveMetadata.Shown("", ""), LiveMetadata.merge(null, null))
        assertEquals(LiveMetadata.Shown("Song", ""), LiveMetadata.merge("", "Song"))
    }
}
