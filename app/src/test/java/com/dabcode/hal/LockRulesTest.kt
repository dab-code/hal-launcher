package com.dabcode.hal

import com.dabcode.hal.focus.FocusProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRulesTest {

    private val minute = 60_000L
    private val systemApp = FocusProfile.ALWAYS_ALLOWED_PACKAGES.first()
    private val allowed = setOf("allowed")

    @Test
    fun limitReached_atThreshold() {
        assertTrue(isLockdownActive(true, 120 * minute, 120))
    }

    @Test
    fun limitNotReached_belowThreshold() {
        assertFalse(isLockdownActive(true, 120 * minute - 1, 120))
    }

    @Test
    fun limitNotReached_whenOff() {
        assertFalse(isLockdownActive(false, 500 * minute, 120))
    }

    @Test
    fun dailyLimit_locksAppNotAllowed() {
        assertEquals(LockReason.DAILY_LIMIT, lockReason("x", limitReached = true, focusActive = false, alwaysAllowed = allowed))
    }

    @Test
    fun focus_locksAppNotAllowed() {
        assertEquals(LockReason.FOCUS, lockReason("x", limitReached = false, focusActive = true, alwaysAllowed = allowed))
    }

    @Test
    fun oneSharedList_keepsAppOpenForBoth() {
        assertNull(lockReason("allowed", limitReached = true, focusActive = true, alwaysAllowed = allowed))
    }

    @Test
    fun systemApp_isNeverLocked() {
        assertNull(lockReason(systemApp, limitReached = true, focusActive = true, alwaysAllowed = emptySet()))
    }

    @Test
    fun dailyLimit_hasPriorityOverFocus() {
        assertEquals(LockReason.DAILY_LIMIT, lockReason("x", limitReached = true, focusActive = true, alwaysAllowed = allowed))
    }

    @Test
    fun break_opensTheApp_forBothLocks() {
        assertNull(lockReason("x", limitReached = true, focusActive = true, alwaysAllowed = allowed, onBreak = true))
    }

    @Test
    fun noRules_noLock() {
        assertNull(lockReason("x", limitReached = false, focusActive = false, alwaysAllowed = emptySet()))
    }

    // The lock texts come from the string resources (lockMessage needs a Context). StringResourcesTest checks them.
}
