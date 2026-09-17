package com.mohdshayan.snarewall.ui.board

import androidx.lifecycle.SavedStateHandle
import com.mohdshayan.snarewall.ui.nav.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunLaunchTest {

    @Test
    fun aFreshStartIsOneShotSoARestoredScreenResumesItsSave() {
        val args = Board(1, "standard", daily = false, fresh = true)
        val state = SavedStateHandle()
        val first = RunLaunch(args, state)
        assertTrue(first.ignoreSave)
        first.started()
        // Process death: same route arguments, the saved state comes back.
        val restored = RunLaunch(args, SavedStateHandle(state.keys().associateWith { state.get<Any>(it) }))
        assertFalse(restored.ignoreSave)
    }

    @Test
    fun aContinueNeverIgnoresTheSave() {
        assertFalse(RunLaunch(Board(3, "iron", daily = false, fresh = false), SavedStateHandle()).ignoreSave)
    }

    @Test
    fun aDailyRunKeepsItsDateAcrossARestoreAfterMidnight() {
        val args = Board(0, "standard", daily = true, fresh = true)
        val state = SavedStateHandle()
        assertEquals("2026-09-17", RunLaunch(args, state) { "2026-09-17" }.dateKey)
        val restored = RunLaunch(args, SavedStateHandle(state.keys().associateWith { state.get<Any>(it) })) { "2026-09-18" }
        assertEquals("2026-09-17", restored.dateKey)
        assertEquals("2026-09-10", RunLaunch(args.copy(dateKey = "2026-09-10"), SavedStateHandle()) { "2026-09-18" }.dateKey)
        assertNull(RunLaunch(Board(2, "standard", daily = false, fresh = true), SavedStateHandle()) { "x" }.dateKey)
    }
}
