package com.dabcode.hal.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAppsTest {

    @Test
    fun defaultApp_wins() {
        assertEquals(
            setOf("com.samsung.android.dialer"),
            packagesForRole(RoleCandidates("com.samsung.android.dialer", listOf("com.android.dialer")))
        )
    }

    @Test
    fun chooser_givesTheSystemApps() {
        // The user did not pick a default camera: the system chooser ("android") resolves the intent.
        assertEquals(
            setOf("com.oneplus.camera", "com.google.android.GoogleCamera"),
            packagesForRole(RoleCandidates("android", listOf("com.oneplus.camera", "com.google.android.GoogleCamera")))
        )
    }

    @Test
    fun noApp_givesNothing() {
        assertEquals(emptySet<String>(), packagesForRole(RoleCandidates(null, emptyList())))
    }

    @Test
    fun allRoles_areAsked_andJoined() {
        val asked = mutableListOf<DefaultAppRole>()
        val samsung = mapOf(
            DefaultAppRole.DIALER to "com.samsung.android.dialer",
            DefaultAppRole.SMS to "com.samsung.android.messaging",
            DefaultAppRole.CAMERA to "com.sec.android.app.camera",
            DefaultAppRole.CLOCK to "com.sec.android.app.clockpackage",
            DefaultAppRole.MAPS to "com.google.android.apps.maps",
            DefaultAppRole.CALENDAR to "com.samsung.android.calendar",
            DefaultAppRole.MUSIC to null
        )
        val result = roleDefaultPackages { role ->
            asked += role
            RoleCandidates(samsung.getValue(role), emptyList())
        }
        assertEquals(DefaultAppRole.entries, asked)
        assertEquals(samsung.values.filterNotNull().toSet(), result)
    }
}
