package com.dabcode.hal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppSearchTest {

    private val apps = listOf("Calendar", "Gmail", "Google Maps", "Maps", "Photos", "Weather", "WhatsApp", "Ébook Reader")

    private fun search(query: String) = fuzzySearch(apps, query) { it }

    @Test
    fun wa_findsWhatsAppBeforeWeather() {
        // "WhatsApp" is an initials match. "Weather" is only a subsequence match.
        assertEquals(listOf("WhatsApp", "Weather"), search("wa"))
        assertEquals(2, matchRank("WhatsApp", "wa"))
        assertEquals(3, matchRank("Weather", "wa"))
    }

    @Test
    fun gmp_findsGoogleMaps() {
        assertEquals(listOf("Google Maps"), search("gmp"))
    }

    @Test
    fun prefix_thenWordStart_thenSubsequence() {
        // "Maps" starts with "ma". "Google Maps" has a word that starts with "ma". "Gmail" only has the letters.
        assertEquals(listOf("Maps", "Google Maps", "Gmail"), search("ma"))
    }

    @Test
    fun sameRank_isAlphabetical() {
        assertEquals(listOf("Gmail", "Google Maps"), fuzzySearch(listOf("Google Maps", "Gmail"), "g") { it })
    }

    @Test
    fun ignoresCaseAndAccents() {
        assertEquals(listOf("Ébook Reader"), search("EBOOK"))
        assertEquals(listOf("Ébook Reader"), search("ébo"))
        assertEquals(0, matchRank("éclair", "ECL"))
    }

    @Test
    fun camelCaseWord_isWordStart() {
        assertEquals(1, matchRank("WhatsApp", "app"))
    }

    @Test
    fun typos_findTheApp() {
        val list = listOf("Calendar", "Kamera", "Maps", "Spotify")
        assertEquals("Spotify", fuzzySearch(list, "spotfy") { it }.first())
        assertEquals("Spotify", fuzzySearch(list, "sptoify") { it }.first())
        assertEquals("Kamera", fuzzySearch(list, "kamra") { it }.first())
        assertEquals(4, matchRank("Spotify", "sptoify"))
    }

    @Test
    fun typos_matchOneWordOfTheLabel() {
        assertEquals(4, matchRank("Google Maps", "mpas"))
    }

    @Test
    fun typos_notForShortQueries() {
        // "xa" has 2 letters, so no typo is allowed.
        assertNull(matchRank("Maps", "xa"))
        // 3 to 5 letters allow 1 typo, not 2. "mxpz" has 2 typos for "maps".
        assertEquals(2, editDistance("mxpz", "maps"))
        assertNull(matchRank("Maps", "mxpz"))
    }

    @Test
    fun editDistance_countsSwapAsOne() {
        assertEquals(1, editDistance("sptoify", "spotify"))
        assertEquals(1, editDistance("spotfy", "spotify"))
        assertEquals(1, editDistance("kamra", "kamera"))
        assertEquals(0, editDistance("maps", "maps"))
        assertEquals(3, editDistance("", "abc"))
    }

    @Test
    fun noMatch_isNull() {
        assertNull(matchRank("Photos", "xyz"))
        assertEquals(emptyList<String>(), search("zz"))
    }

    @Test
    fun blankQuery_returnsAllUnchanged() {
        assertEquals(apps, search("  "))
    }

    @Test
    fun queryIsTrimmed() {
        assertEquals(0, matchRank("Maps", " ma "))
    }

    @Test
    fun jumpIndex_findsFirstAppAtOrAfterLetter() {
        val sorted = listOf("2048", "Calendar", "Éclair", "Maps", "Photos")
        assertEquals(0, jumpIndex(sorted, '#', "en"))
        assertEquals(1, jumpIndex(sorted, 'A', "en"))
        assertEquals(2, jumpIndex(sorted, 'E', "en"))
        assertEquals(3, jumpIndex(sorted, 'M', "en"))
        assertEquals(4, jumpIndex(sorted, 'N', "en"))
        assertEquals(4, jumpIndex(sorted, 'Z', "en"))
        assertEquals(-1, jumpIndex(emptyList(), 'A', "en"))
    }

    @Test
    fun jumpIndex_japanese_keepsTheFirstRule() {
        // Kana is after Z, so a tap on a letter after the last Latin name goes to the kana.
        val sorted = listOf("2048", "Maps", "カメラ")
        assertEquals(1, jumpIndex(sorted, 'M', "ja"))
        assertEquals(2, jumpIndex(sorted, 'X', "ja"))
    }

    @Test
    fun jumpLetters_perLanguage() {
        assertEquals(listOf('#') + ('A'..'Z') + listOf('Æ', 'Ø', 'Å'), jumpLetters("da"))
        assertEquals(jumpLetters("da"), jumpLetters("nb"))
        assertEquals(jumpLetters("da"), jumpLetters("no"))
        assertEquals(listOf('#') + ('A'..'Z') + listOf('Å', 'Ä', 'Ö'), jumpLetters("sv"))
        assertEquals(listOf('#') + ('A'..'N') + listOf('Ñ') + ('O'..'Z'), jumpLetters("es"))
        assertEquals(listOf('#') + ('A'..'Z'), jumpLetters("de"))
        assertEquals(listOf('#') + ('A'..'Z'), jumpLetters("en"))
    }

    @Test
    fun jumpLetter_danish() {
        assertEquals('Æ', jumpLetter("Ærø", "da"))
        assertEquals('Ø', jumpLetter("øresund", "da"))
        assertEquals('Å', jumpLetter("Åben", "da"))
        assertEquals('Å', jumpLetter("Aalborg", "da"))
        assertEquals('Æ', jumpLetter("Äpple", "da"))
        assertEquals('E', jumpLetter("Éclair", "da"))
        assertEquals('#', jumpLetter("1Password", "da"))
    }

    @Test
    fun jumpLetter_swedish() {
        assertEquals('Å', jumpLetter("Åben", "sv"))
        assertEquals('Ä', jumpLetter("Äpple", "sv"))
        assertEquals('Ö', jumpLetter("Örebro", "sv"))
        assertEquals('Ö', jumpLetter("Øresund", "sv"))
        assertEquals('A', jumpLetter("Aalborg", "sv"))
    }

    @Test
    fun jumpLetter_spanish() {
        assertEquals('Ñ', jumpLetter("ñandú", "es"))
        assertEquals('N', jumpLetter("Nube", "es"))
        assertEquals('E', jumpLetter("Éxito", "es"))
    }

    @Test
    fun jumpLetter_germanAndEnglish_foldToAZ() {
        assertEquals('A', jumpLetter("Ärzte", "de"))
        assertEquals('U', jumpLetter("Über", "de"))
        assertEquals('O', jumpLetter("Øresund", "de"))
        assertEquals('A', jumpLetter("Åben", "en"))
        assertEquals('E', jumpLetter("Éclair", "en"))
        assertEquals('N', jumpLetter("Ñandú", "en"))
        assertEquals('#', jumpLetter("カメラ", "en"))
        assertEquals('#', jumpLetter("", "en"))
    }

    @Test
    fun sortAppLabels_danish_putsExtraLettersAfterZ() {
        val sorted = sortAppLabels(listOf("Åben", "Zoom", "Øresund", "Aula", "Ærø"), Locale.forLanguageTag("da")) { it }
        assertEquals(listOf("Aula", "Zoom", "Ærø", "Øresund", "Åben"), sorted)
    }

    @Test
    fun jumpIndex_danish_findsExtraLetters() {
        val sorted = listOf("1Password", "Aula", "Zoom", "Ærø", "Øresund", "Åben")
        assertEquals(3, jumpIndex(sorted, 'Æ', "da"))
        assertEquals(4, jumpIndex(sorted, 'Ø', "da"))
        assertEquals(5, jumpIndex(sorted, 'Å', "da"))
        assertEquals(2, jumpIndex(sorted, 'B', "da"))
    }

    @Test
    fun jumpIndex_spanish_findsEnye() {
        val sorted = sortAppLabels(listOf("Oasis", "Ñandú", "Nube"), Locale.forLanguageTag("es")) { it }
        assertEquals(listOf("Nube", "Ñandú", "Oasis"), sorted)
        assertEquals(1, jumpIndex(sorted, 'Ñ', "es"))
        assertEquals(2, jumpIndex(sorted, 'O', "es"))
    }
}
