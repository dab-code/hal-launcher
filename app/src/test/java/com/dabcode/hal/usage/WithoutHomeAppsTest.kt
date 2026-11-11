package com.dabcode.hal.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class WithoutHomeAppsTest {

    @Test
    fun removesHomeApps() {
        val usage = mapOf("hal" to 50L, "launcher" to 14L, "mail" to 9L)
        assertEquals(mapOf("mail" to 9L), withoutHomeApps(usage, setOf("hal", "launcher")))
    }

    @Test
    fun keepsAllWhenNoHomeApp() {
        val usage = mapOf("mail" to 9L, "maps" to 3L)
        assertEquals(usage, withoutHomeApps(usage, setOf("hal")))
    }

    @Test
    fun emptyUsageGivesEmpty() {
        assertEquals(emptyMap<String, Long>(), withoutHomeApps(emptyMap(), setOf("hal")))
    }
}

class UsageRowNameTest {

    @Test
    fun customNameWins() {
        assertEquals("Music", usageRowName("spotify", "Spotify", mapOf("spotify" to "Music")))
    }

    @Test
    fun noNameFallsBackToLabel() {
        assertEquals("Spotify", usageRowName("spotify", "Spotify", mapOf("mail" to "Post")))
    }
}

class ScreenTimeTest {

    @Test
    fun sumLeavesOutHomeApps() {
        val usage = mapOf("hal" to 50L, "launcher" to 14L, "mail" to 9L, "maps" to 3L)
        assertEquals(12L, screenTimeMs(usage, setOf("hal", "launcher")))
    }

    @Test
    fun onlyHomeAppsGiveZero() {
        assertEquals(0L, screenTimeMs(mapOf("hal" to 50L), setOf("hal")))
    }

    @Test
    fun noHomeAppsGivesFullSum() {
        assertEquals(12L, screenTimeMs(mapOf("mail" to 9L, "maps" to 3L), emptySet()))
    }
}

class HomePackagesTest {

    @Test
    fun fallbackHomeIsNotAHomeApp() {
        val found = listOf(
            HomeCandidate("com.android.launcher", 0),
            HomeCandidate("com.dabcode.hal", 0),
            HomeCandidate("com.android.settings", -1000)
        )
        assertEquals(setOf("com.android.launcher", "com.dabcode.hal"), homePackagesOf(found, "com.dabcode.hal"))
    }

    @Test
    fun ownPackageIsAlwaysIncluded() {
        assertEquals(setOf("hal"), homePackagesOf(emptyList(), "hal"))
    }
}

class WithoutIconlessAppsTest {

    private val launcher = setOf("mail", "maps", "dialer")

    @Test
    fun removesAppsWithNoIcon() {
        val usage = mapOf("mail" to 9L, "com.oplus.screenshot" to 4L)
        assertEquals(mapOf("mail" to 9L), withoutIconlessApps(usage, launcher, emptySet(), "dialer"))
    }

    @Test
    fun inCallTimeGoesToDialer() {
        val usage = mapOf("dialer" to 2L, "incallui" to 5L, "mail" to 1L)
        assertEquals(
            mapOf("dialer" to 7L, "mail" to 1L),
            withoutIconlessApps(usage, launcher, setOf("incallui"), "dialer")
        )
    }

    @Test
    fun inCallTimeGoesToDialerWithNoDialerRow() {
        assertEquals(
            mapOf("dialer" to 5L),
            withoutIconlessApps(mapOf("incallui" to 5L), launcher, setOf("incallui"), "dialer")
        )
    }

    @Test
    fun inCallPackageWithIconKeepsItsOwnRow() {
        val usage = mapOf("dialer" to 5L)
        assertEquals(usage, withoutIconlessApps(usage, launcher, setOf("dialer"), "other"))
    }

    @Test
    fun noDialerDropsInCallTime() {
        assertEquals(
            emptyMap<String, Long>(),
            withoutIconlessApps(mapOf("incallui" to 5L), launcher, setOf("incallui"), null)
        )
    }

    @Test
    fun totalEqualsRows() {
        val usage = mapOf("mail" to 9L, "x" to 4L, "incallui" to 3L)
        val rows = withoutIconlessApps(usage, launcher, setOf("incallui"), "dialer")
        assertEquals(12L, screenTimeMs(rows, emptySet()))
    }
}
