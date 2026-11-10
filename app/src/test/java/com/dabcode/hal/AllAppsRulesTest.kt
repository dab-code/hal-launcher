package com.dabcode.hal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AllAppsRulesTest {

    private fun app(pkg: String) = AppInfo(pkg, pkg, isEnabled = false, hasNotifications = false)
    private val apps = listOf(app("a"), app("b"), app("c"))

    @Test
    fun noHiddenApps_showsAll() {
        assertEquals(apps, appsForAllApps(apps, emptySet(), ""))
    }

    @Test
    fun emptyQuery_leavesOutHiddenApps() {
        assertEquals(listOf(app("a"), app("c")), appsForAllApps(apps, setOf("b"), ""))
        assertEquals(listOf(app("a"), app("c")), appsForAllApps(apps, setOf("b"), "  "))
    }

    @Test
    fun search_keepsHiddenApps() {
        assertEquals(apps, appsForAllApps(apps, setOf("b"), "b"))
    }

    @Test
    fun unknownHiddenPackage_changesNothing() {
        assertEquals(apps, appsForAllApps(apps, setOf("gone"), ""))
    }

    @Test
    fun oneMatch_settingOn_opensIt() {
        assertEquals(app("a"), singleMatchToOpen(true, "a", listOf(app("a"))))
    }

    @Test
    fun oneMatch_settingOff_opensNothing() {
        assertNull(singleMatchToOpen(false, "a", listOf(app("a"))))
    }

    @Test
    fun twoMatchesOrNone_opensNothing() {
        assertNull(singleMatchToOpen(true, "a", listOf(app("a"), app("b"))))
        assertNull(singleMatchToOpen(true, "zz", emptyList()))
    }

    @Test
    fun emptyQuery_opensNothing() {
        // The list has one app, but the user did not search.
        assertNull(singleMatchToOpen(true, "", listOf(app("a"))))
    }

    @Test
    fun withoutSelf_removesHal() {
        val withHal = listOf(app("a"), app("com.dabcode.hal"), app("b"))
        assertEquals(listOf(app("a"), app("b")), withoutSelf(withHal, "com.dabcode.hal"))
    }

    @Test
    fun withoutSelf_noHal_changesNothing() {
        assertEquals(apps, withoutSelf(apps, "com.dabcode.hal"))
    }
}
