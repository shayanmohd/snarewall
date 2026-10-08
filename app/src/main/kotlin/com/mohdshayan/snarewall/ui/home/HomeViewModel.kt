package com.mohdshayan.snarewall.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.game.Access
import com.mohdshayan.snarewall.game.DailyGenerator
import com.mohdshayan.snarewall.game.ProgressRules
import com.mohdshayan.snarewall.game.SaveCodec
import com.mohdshayan.snarewall.game.Scoring
import com.mohdshayan.snarewall.ui.nav.Board
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeState {
    data object Loading : HomeState
    data object Error : HomeState
    data class Ready(
        val primaryLabel: String,
        val primaryNote: String?,
        val primary: Board,
        val firstLaunch: Boolean,
        val medals: Int,
        val cleared: Int,
        val owned: Boolean,
    ) : HomeState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.progress
    private val retry = MutableStateFlow(0)
    private val unlocked = ServiceLocator.unlock.state.map { it.unlocked }.distinctUntilChanged()

    // Retry restarts the whole pipeline, because catch ends the flow it guards.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = retry.flatMapLatest { combine(
        repo.progress, repo.resume, ServiceLocator.appPrefs.settings, unlocked,
    ) { rows, resume, settings, owned ->
        val count = ServiceLocator.content.load().levels.size
        val today = DailyGenerator.todayKey()
        val diffLabel = { id: String -> id.replaceFirstChar { it.uppercase() } }
        val save = resume?.let { SaveCodec.decodeRun(it.stateJson) }
        val cleared = rows.filter { it.clears > 0 }.map { it.levelId }.toSet().size
        val medals = rows.count { it.medal != Scoring.Medal.NONE.id }
        val ready: HomeState = when {
            save != null && save.dailyKey == today ->
                HomeState.Ready("Continue today's map", "Saved at wave ${save.wave + 1}", Board(0, "standard", true, false, today), false, medals, cleared, owned)
            save != null && save.dailyKey != null ->
                HomeState.Ready("Continue the daily map", "Map for ${save.dailyKey}, saved at wave ${save.wave + 1}", Board(0, "standard", true, false, save.dailyKey), false, medals, cleared, owned)
            // Never skip a saved run, even one in the full game: Home's fresh start would overwrite it unwarned.
            // The tap reaches the Board gate, which shows the Unlock screen.
            save != null && save.dailyKey == null && save.levelId in 1..count ->
                HomeState.Ready(
                    "Continue level ${save.levelId}",
                    if (Access.levelOpen(save.levelId, owned)) "Saved at wave ${save.wave + 1}, ${diffLabel(save.difficulty)}"
                    else "Level ${save.levelId} is in the full game.",
                    Board(save.levelId, save.difficulty, false, false), false, medals, cleared, owned,
                )
            else -> {
                val next = ProgressRules.nextLevel(rows, count)
                val playable = Access.nextPlayable(next, owned)
                when {
                    rows.isEmpty() -> HomeState.Ready("Play level 1", null, Board(1, settings.lastDifficulty, false, true), true, 0, 0, owned)
                    playable != null -> HomeState.Ready("Play level $playable", "${diffLabel(settings.lastDifficulty)} difficulty", Board(playable, settings.lastDifficulty, false, true), false, medals, cleared, owned)
                    next != null -> HomeState.Ready("Play today's map", "Chalk Downs cleared. Levels 6 to 15 are in the full game.", Board(0, "standard", true, false), false, medals, cleared, owned)
                    else -> HomeState.Ready("Play today's map", "Every level cleared", Board(0, "standard", true, false), false, medals, cleared, owned)
                }
            }
        }
        ready
    }
        .flowOn(Dispatchers.IO)
        .catch { emit(HomeState.Error) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Loading)

    fun retry() {
        retry.value++
    }

    fun startFresh(done: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.replaceAll(emptyList(), null, emptyList())
            } catch (_: Exception) {
            }
            retry.value++
            done()
        }
    }
}
