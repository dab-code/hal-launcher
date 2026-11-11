package com.dabcode.hal.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class EssentialAppsTest {

    private val usage = mapOf("maps" to 80L, "mail" to 9L, "music" to 5L)

    @Test
    fun `withoutEssentialApps removes the essential apps`() {
        assertEquals(mapOf("mail" to 9L), withoutEssentialApps(usage, setOf("maps", "music")))
    }

    @Test
    fun `withoutEssentialApps keeps all apps when the set is empty`() {
        assertEquals(usage, withoutEssentialApps(usage, emptySet()))
    }

    @Test
    fun `an essential app with no usage changes nothing`() {
        assertEquals(usage, withoutEssentialApps(usage, setOf("other")))
        assertEquals(emptyMap<String, Long>(), onlyEssentialApps(usage, setOf("other")))
    }

    @Test
    fun `onlyEssentialApps keeps only the essential apps`() {
        assertEquals(mapOf("maps" to 80L, "music" to 5L), onlyEssentialApps(usage, setOf("maps", "music")))
    }

    @Test
    fun `the two parts add up to the full time`() {
        val essential = setOf("maps")
        val total = withoutEssentialApps(usage, essential).values.sum() + onlyEssentialApps(usage, essential).values.sum()
        assertEquals(usage.values.sum(), total)
    }

    @Test
    fun `the screen time has no essential apps and no home apps`() {
        val perApp = usage + ("hal" to 7L)
        assertEquals(9L, screenTimeMs(withoutEssentialApps(perApp, setOf("maps", "music")), setOf("hal")))
    }

    @Test
    fun `essentialNames shows up to two names, then a count`() {
        assertEquals("", essentialNames(emptyList()))
        assertEquals("Maps", essentialNames(listOf("Maps")))
        assertEquals("Maps, Spotify", essentialNames(listOf("Maps", "Spotify")))
        assertEquals("Maps, Spotify +1", essentialNames(listOf("Maps", "Spotify", "Waze")))
        assertEquals("Maps, Spotify +2", essentialNames(listOf("Maps", "Spotify", "Waze", "Bus")))
    }
}
