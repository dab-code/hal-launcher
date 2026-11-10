package com.dabcode.hal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotRuleTest {

    // A normal message: clearable, not ongoing, no media.
    private fun dot(
        isOngoing: Boolean = false,
        isClearable: Boolean = true,
        isGroupSummary: Boolean = false,
        category: String? = "msg",
        hasMediaSession: Boolean = false
    ) = isDotWorthy(isOngoing, isClearable, isGroupSummary, category, hasMediaSession)

    @Test
    fun normalMessage_getsDot() {
        assertTrue(dot())
        assertTrue(dot(category = null))
    }

    @Test
    fun pausedMediaNotification_getsNoDot() {
        // A paused player is not ongoing, but it has a media session.
        assertFalse(dot(hasMediaSession = true))
        // "transport" is Notification.CATEGORY_TRANSPORT.
        assertFalse(dot(category = "transport"))
    }

    @Test
    fun notClearable_getsNoDot() {
        assertFalse(dot(isClearable = false))
    }

    @Test
    fun ongoingOrGroupSummary_getsNoDot() {
        assertFalse(dot(isOngoing = true))
        assertFalse(dot(isGroupSummary = true))
    }
}
