package app.tileshell.cortana.speech

import android.os.IBinder
import app.tileshell.cortana.CortanaPrefsState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The voice table, round-tripped through the parser that actually consumes it.
 *
 * [SpeechClient.voices] splits each line on `|` and drops anything it cannot parse, silently. A name
 * containing a `|`, or a description containing a newline, would not fail loudly — the voice would just
 * stop existing in Settings. So the emission is tested against the real parser rather than against a
 * copy of the format, and the fields are tested for the characters that would break it.
 */
class VoicesTest {

    /**
     * An [ISpeech] that answers [voices] and nothing else. Deliberately NOT an `ISpeech.Stub`: a Binder
     * cannot be constructed in a host JVM test, and the parser under test never touches one.
     */
    private class FakeSpeech(private val payload: String) : ISpeech {
        override fun asBinder(): IBinder? = null
        override fun voices(): String = payload
        override fun register(callback: ISpeechCallback?) = Unit
        override fun unregister(callback: ISpeechCallback?) = Unit
        override fun startListening(owner: ISpeechCallback?, hotwords: String?, who: String?) = Unit
        override fun holdMicrophone(cb: ISpeechCallback?, who: String?): String? = null
        override fun releaseMicrophone(cb: ISpeechCallback?) = Unit
        override fun stopListening(owner: ISpeechCallback?) = Unit
        override fun speak(owner: ISpeechCallback?, utteranceId: String?, text: String?, speakerId: Int) = Unit
        override fun stopSpeaking(owner: ISpeechCallback?) = Unit
        override fun preload() = Unit
        override fun status(): String = ""
    }

    private fun throughSpeechClient(payload: String): List<Voice> {
        val field = SpeechClient::class.java.getDeclaredField("service").apply { isAccessible = true }
        field.set(SpeechClient, FakeSpeech(payload))
        return SpeechClient.voices()
    }

    @After
    fun clearTheClient() {
        SpeechClient::class.java.getDeclaredField("service").apply { isAccessible = true }
            .set(SpeechClient, null)
    }

    @Test
    fun `the eleven bundled voices survive the trip through SpeechClient's parser`() {
        val parsed = throughSpeechClient(Voices.lines(Voices.EXPECTED_SPEAKERS))

        assertEquals(Voices.EXPECTED_SPEAKERS, parsed.size)
        assertEquals(Voices.BUNDLED.size, parsed.size)
        Voices.BUNDLED.forEachIndexed { index, bundled ->
            val voice = parsed[index]
            assertEquals(bundled.id, voice.id)
            assertEquals(bundled.name, voice.name)
            assertEquals(bundled.description, voice.description)
        }
    }

    @Test
    fun `ids are the model's own - contiguous from zero, in order`() {
        assertEquals((0 until Voices.EXPECTED_SPEAKERS).toList(), Voices.BUNDLED.map { it.id })
    }

    @Test
    fun `no field can contain the separator or a newline`() {
        for (voice in Voices.BUNDLED) {
            assertFalse("${voice.name} contains the separator", voice.name.contains('|'))
            assertFalse("${voice.name}'s description contains the separator", voice.description.contains('|'))
            assertFalse(voice.name.contains('\n'))
            assertFalse(voice.description.contains('\n'))
            assertTrue("a voice needs a name to show", voice.name.isNotBlank())
            assertTrue("a voice needs a description to show", voice.description.isNotBlank())
        }
    }

    @Test
    fun `the emitted format is exactly id-pipe-name-pipe-description`() {
        val lines = Voices.lines().lines()
        assertEquals(Voices.EXPECTED_SPEAKERS, lines.size)
        assertEquals("1|Bella|Warm American female", lines[1])
        assertEquals("5|Adam|Clear American male", lines[5])
        assertEquals("9|George|British male", lines[9])
        for (line in lines) {
            assertEquals("each line has exactly three fields", 3, line.split('|').size)
        }
    }

    @Test
    fun `the launcher's default voice is one this table offers`() {
        val default = Voices.BUNDLED.firstOrNull { it.id == CortanaPrefsState.DEFAULT_VOICE_ID }
        assertNotNull("CortanaPrefs defaults to a voice the table does not list", default)
        assertEquals(2, CortanaPrefsState.DEFAULT_VOICE_ID)
        // Kokoro's own ordering puts af_nicole at 2. CortanaPrefs' comment says af_bella, which is 1 here.
        // The id the launcher picks is unchanged; only the identifier in that comment disagrees.
        assertEquals("af_nicole", default!!.kokoroId)
        assertTrue("the default has to be a female voice per H2", default.description.contains("female"))
    }

    @Test
    fun `a model with fewer speakers than the table offers only the ids it has`() {
        val parsed = throughSpeechClient(Voices.lines(numSpeakers = 4))
        assertEquals(listOf(0, 1, 2, 3), parsed.map { it.id })
    }

    @Test
    fun `a model reporting more speakers than the table names offers only the named ones`() {
        val parsed = throughSpeechClient(Voices.lines(numSpeakers = 40))
        assertEquals(Voices.EXPECTED_SPEAKERS, parsed.size)
        assertEquals(10, parsed.last().id)
    }

    @Test
    fun `a model reporting no speakers offers nothing rather than a voice that cannot speak`() {
        assertEquals("", Voices.lines(numSpeakers = 0))
        assertEquals(emptyList<Voice>(), throughSpeechClient(Voices.lines(numSpeakers = 0)))
    }

    @Test
    fun `the parser drops a malformed line instead of the whole table`() {
        // Proves the round-trip above is a real assertion: this is what a broken field would look like.
        val parsed = throughSpeechClient("0|Ava|American female\nnot-a-voice\n2|Nicole|Warm, close American female")
        assertEquals(listOf(0, 2), parsed.map { it.id })
    }
}
