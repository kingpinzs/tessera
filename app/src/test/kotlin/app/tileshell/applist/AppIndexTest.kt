package app.tileshell.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppIndexTest {
    private val collator = AppIndex.collator(Locale.US)

    private fun group(labels: List<String>) = AppIndex.group(labels, { it }, { it }, collator)

    @Test
    fun normalizeFoldsCaseAndAccents() {
        assertEquals("cafe", AppIndex.normalize("Café"))
        assertEquals("ecran", AppIndex.normalize("ÉCRAN"))
        assertEquals("strasse", AppIndex.normalize("Straße"))
        assertEquals("oresund", AppIndex.normalize("Øresund"))
        assertEquals("a", AppIndex.normalize("Ａ")) // full-width Latin
    }

    @Test
    fun letterOfUsesBaseLatinLetterElseHash() {
        assertEquals("E", AppIndex.letterOf("Épicerie"))
        assertEquals("Z", AppIndex.letterOf("zoom"))
        assertEquals("O", AppIndex.letterOf("Øresund"))
        assertEquals(AppIndex.OTHER, AppIndex.letterOf("3D Viewer"))
        assertEquals(AppIndex.OTHER, AppIndex.letterOf("#hashtag"))
        assertEquals(AppIndex.OTHER, AppIndex.letterOf("Телеграм"))
        assertEquals(AppIndex.OTHER, AppIndex.letterOf("微信"))
        assertEquals(AppIndex.OTHER, AppIndex.letterOf(""))
        assertEquals("M", AppIndex.letterOf("  Maps"))
    }

    @Test
    fun groupsAreHashFirstThenLettersWithNoRecentlyAddedGroup() {
        val groups = group(listOf("Zoom", "maps", "Mail", "7-Zip", "Ångström", "Контакты", "alarm"))
        assertEquals(listOf("#", "A", "M", "Z"), groups.map { it.first })
        assertEquals(listOf("alarm", "Ångström"), groups[1].second)
        assertEquals(listOf("Mail", "maps"), groups[2].second)
        assertTrue(groups[0].second.containsAll(listOf("7-Zip", "Контакты")))
    }

    @Test
    fun manyAppsUnderOneLetterStaySortedAndComplete() {
        val labels = (1..350).map { "App %03d".format(it) }.shuffled(java.util.Random(7))
        val groups = group(labels)
        assertEquals(1, groups.size)
        assertEquals("A", groups[0].first)
        assertEquals((1..350).map { "App %03d".format(it) }, groups[0].second)
    }

    @Test
    fun rankPrefersNamePrefixThenWordPrefixThenAnywhere() {
        assertEquals(0, AppIndex.rank("calendar", "cal"))
        assertEquals(1, AppIndex.rank("google calendar", "cal"))
        assertEquals(2, AppIndex.rank("vocal", "cal"))
        assertEquals(1, AppIndex.rank("vocal calendar", "cal"))
        assertNull(AppIndex.rank("maps", "cal"))
        assertNull(AppIndex.rank("maps", ""))
    }

    @Test
    fun searchIsCaseAndAccentInsensitiveAndRanked() {
        val labels = listOf("Vocal", "Calculator", "Google Calendar", "Café Finder", "Maps")
        val result = AppIndex.search(labels, { AppIndex.normalize(it) }, "CAL")
        assertEquals(listOf("Calculator", "Google Calendar", "Vocal"), result)
        assertEquals(listOf("Café Finder"), AppIndex.search(labels, { AppIndex.normalize(it) }, "cafe"))
        assertEquals(listOf("Café Finder"), AppIndex.search(labels, { AppIndex.normalize(it) }, "CAFÉ"))
        assertTrue(AppIndex.search(labels, { AppIndex.normalize(it) }, "   ").isEmpty())
    }

    @Test
    fun nonLatinSearchMatches() {
        val labels = listOf("Телеграм", "微信", "Ёлка")
        assertEquals(listOf("Телеграм"), AppIndex.search(labels, { AppIndex.normalize(it) }, "теле"))
        assertEquals(listOf("微信"), AppIndex.search(labels, { AppIndex.normalize(it) }, "微"))
        assertEquals(listOf("Ёлка"), AppIndex.search(labels, { AppIndex.normalize(it) }, "елка"))
    }
}
