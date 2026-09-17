package com.mohdshayan.snarewall.ui.board

import androidx.lifecycle.SavedStateHandle
import com.mohdshayan.snarewall.game.DailyGenerator
import com.mohdshayan.snarewall.ui.nav.Board

/**
 * How a Board launch treats the resume slot. The route arguments come back unchanged after the process
 * dies, so "fresh" must be a one-shot: once the fresh run has started, a restored screen resumes its save
 * instead of wiping it. A daily run pins its date the same way, so a restore after UTC midnight keeps the map.
 */
class RunLaunch(private val args: Board, private val state: SavedStateHandle, today: () -> String = DailyGenerator::todayKey) {

    val dateKey: String? = if (!args.daily) null else
        (state.get<String>(KEY_DATE) ?: args.dateKey ?: today()).also { state[KEY_DATE] = it }

    /** True when this launch should ignore any saved run and start from wave 1. */
    val ignoreSave: Boolean get() = args.fresh && state.get<Boolean>(KEY_FRESH_DONE) != true

    /** Call once the run has started, so a later restore of this screen resumes it. */
    fun started() {
        state[KEY_FRESH_DONE] = true
    }

    private companion object {
        const val KEY_DATE = "run_date_key"
        const val KEY_FRESH_DONE = "run_fresh_done"
    }
}
