package com.pact.app

import com.pact.app.core.PactState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daily-allowance math on [PactState.Snapshot] is pure, so it can be
 * pinned here without a device. This is the heart of the new model: apps are
 * budgeted per day, not switched off, and the streak counts days kept within
 * those budgets.
 */
class LimitTest {

    private val ig = "com.instagram.android"

    private fun snap(limit: Int, usedMillis: Long) = PactState.Snapshot(
        setupComplete = true,
        blocked = setOf(ig),
        dailyLimits = mapOf(ig to limit),
        usedTodayMillis = mapOf(ig to usedMillis),
    )

    @Test fun `fresh app has its full allowance`() {
        val s = snap(limit = 30, usedMillis = 0)
        assertTrue(s.hasLimit(ig))
        assertEquals(30 * 60_000L, s.remainingMillis(ig))
        assertEquals(30, s.remainingMinutes(ig))
    }

    @Test fun `partly used app reports what is left, rounded up`() {
        val s = snap(limit = 30, usedMillis = 20 * 60_000L + 30_000L) // 20m30s used
        assertEquals(10, s.remainingMinutes(ig))   // 9m30s left rounds up to 10
    }

    @Test fun `spent budget leaves nothing`() {
        val s = snap(limit = 15, usedMillis = 20 * 60_000L)
        assertEquals(0L, s.remainingMillis(ig))
        assertEquals(0, s.remainingMinutes(ig))
    }

    @Test fun `zero limit is a hard lock with no allowance`() {
        val s = snap(limit = 0, usedMillis = 0)
        assertFalse(s.hasLimit(ig))
        assertEquals(0L, s.remainingMillis(ig))
    }

    @Test fun `screen time sums only limited apps`() {
        val other = "com.other"
        val s = PactState.Snapshot(
            setupComplete = true,
            blocked = setOf(ig),
            dailyLimits = mapOf(ig to 30),
            usedTodayMillis = mapOf(ig to 12 * 60_000L, other to 99 * 60_000L),
        )
        assertEquals(12, s.screenTimeTodayMinutes())
    }
}
