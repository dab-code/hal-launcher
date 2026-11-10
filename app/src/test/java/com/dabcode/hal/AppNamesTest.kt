package com.dabcode.hal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNamesTest {

    private val spotify = AppInfo("Spotify", "com.spotify", isEnabled = true, hasNotifications = false,
        componentKey = "com.spotify/.Main")
    private val camera = AppInfo("Camera", "com.cam", isEnabled = true, hasNotifications = false,
        componentKey = "com.cam/.Main")

    @Test
    fun applyCustomNames_usesComponentKey() {
        val result = applyCustomNames(listOf(spotify, camera), mapOf("com.spotify/.Main" to "Music"))
        assertEquals(listOf("Music", "Camera"), result.map { it.appName })
        assertEquals("Spotify", result[0].originalName)
    }

    @Test
    fun applyCustomNames_otherActivityOfSamePackage_keepsOriginalName() {
        val result = applyCustomNames(listOf(spotify), mapOf("com.spotify/.Other" to "Music"))
        assertEquals("Spotify", result[0].appName)
    }

    @Test
    fun applyCustomNames_removedName_givesOriginalBack() {
        val renamed = applyCustomNames(listOf(spotify), mapOf("com.spotify/.Main" to "Music"))
        assertEquals("Spotify", applyCustomNames(renamed, emptyMap())[0].appName)
    }

    @Test
    fun customNameToStore_emptyOrOriginal_isReset() {
        assertNull(customNameToStore("   ", "Spotify"))
        assertNull(customNameToStore("Spotify", "Spotify"))
        assertEquals("Music", customNameToStore("  Music ", "Spotify"))
    }

    @Test
    fun json_roundTrip_andDamagedText() {
        val names = mapOf("com.spotify/.Main" to "Music", "com.cam/.Main" to "Foto")
        assertEquals(names, parseCustomNames(customNamesToJson(names)))
        assertEquals(emptyMap<String, String>(), parseCustomNames("not json"))
        assertEquals(emptyMap<String, String>(), parseCustomNames(null))
    }

    @Test
    fun withCustomName_setsAndRemoves() {
        val names = withCustomName(emptyMap(), "a/.B", "X")
        assertEquals(mapOf("a/.B" to "X"), names)
        assertEquals(emptyMap<String, String>(), withCustomName(names, "a/.B", null))
    }

    @Test
    fun search_matchesCustomAndOriginalName() {
        val apps = applyCustomNames(listOf(spotify, camera), mapOf("com.spotify/.Main" to "Music"))
        fun search(q: String) = fuzzySearchNames(apps, q) { listOf(it.appName, it.originalName) }.map { it.appName }
        assertEquals(listOf("Music"), search("mus"))
        assertEquals(listOf("Music"), search("spoti"))
        assertEquals(listOf("Camera"), search("cam"))
    }
}
