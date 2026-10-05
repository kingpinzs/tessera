package app.tileshell.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The trust review's A-L5: the volume stays in the comparison of a handed-in URI with the row Photos' own query found. */
class MediaRowUriTest {
    /** What `PhotoStore.uriOf` gives the external row with id 7. */
    private val externalRow = "content://media/external/images/media/7"

    private fun canonical(text: String) = MediaRowUri.canonical(text, text.substringAfter("content://media/").substringBefore('?').split('/'))

    @Test
    fun `the two spellings of the shared volume are the same row`() {
        assertEquals(externalRow, canonical("content://media/external/images/media/7"))
        assertEquals(externalRow, canonical("content://media/external_primary/images/media/7"))
        assertEquals("content://media/external/video/media/7", canonical("content://media/external_primary/video/media/7"))
    }

    @Test
    fun `an internal URI does not match the external row of the same id`() {
        assertNotEquals(externalRow, canonical("content://media/internal/images/media/7"))
        assertEquals("content://media/internal/images/media/7", canonical("content://media/internal/images/media/7"))
    }

    @Test
    fun `any other volume name is not the external row either`() {
        for (volume in listOf("1234-5678", "EXTERNAL", "external_primary2", "", "internal_primary")) {
            val uri = "content://media/$volume/images/media/7"
            assertNotEquals(uri, externalRow, canonical(uri))
        }
    }

    @Test
    fun `a URI of another shape is compared as it is`() {
        for (uri in listOf("content://media/external/images/media", "content://media/external/file/7/x/y", "content://media/picker/0/com.x/media/7")) {
            assertEquals(uri, uri, canonical(uri))
        }
    }
}
