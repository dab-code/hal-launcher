package com.dabcode.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {

    @Test
    fun emptyTags_isSystemDefault() {
        assertNull(selectedAppLanguage(""))
    }

    @Test
    fun exactTag() {
        assertEquals("da", selectedAppLanguage("da")?.tag)
        assertEquals("pt-BR", selectedAppLanguage("pt-BR")?.tag)
    }

    @Test
    fun tagWithRegion_usesTheLanguage() {
        assertEquals("de", selectedAppLanguage("de-DE")?.tag)
        assertEquals("pt-BR", selectedAppLanguage("pt-PT")?.tag)
    }

    @Test
    fun oldNorwegianCode_isBokmal() {
        assertEquals("nb", selectedAppLanguage("no")?.tag)
        assertEquals("nb", selectedAppLanguage("no-NO")?.tag)
    }

    @Test
    fun firstTagOfAList() {
        assertEquals("fr", selectedAppLanguage("fr,de")?.tag)
    }

    @Test
    fun unknownLanguage_isNull() {
        assertNull(selectedAppLanguage("fi"))
    }

    @Test
    fun englishIsFirst_andTagsAreUnique() {
        assertEquals("en", APP_LANGUAGES.first().tag)
        assertEquals(APP_LANGUAGES.size, APP_LANGUAGES.map { it.tag }.toSet().size)
    }
}
