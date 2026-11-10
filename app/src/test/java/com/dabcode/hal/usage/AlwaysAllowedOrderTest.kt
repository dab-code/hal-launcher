package com.dabcode.hal.usage

import com.dabcode.hal.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AlwaysAllowedOrderTest {

    private fun app(name: String) = AppInfo(name, "pkg.$name", isEnabled = false, hasNotifications = false)

    @Test
    fun selectedApps_comeFirst_thenByName() {
        val apps = listOf(app("Zoom"), app("bank"), app("Maps"), app("Camera"))
        val order = selectedFirst(apps, setOf("pkg.Zoom", "pkg.Camera")).map { it.appName }
        assertEquals(listOf("Camera", "Zoom", "bank", "Maps"), order)
    }
}
