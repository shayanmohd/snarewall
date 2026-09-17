package com.mohdshayan.snarewall.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.snarewall.di.ServiceLocator
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
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
    ) : HomeState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.progress
    private val retry = MutableStateFlow(0)

    // Retry restarts the whole pipeline, because catch ends the flow it guards.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = retry.flatMapLatest { combine(
        repo.progress, repo.resume, ServiceLocator.appPrefs.settings,
    ) { rows, resume, settings ->
        val count = ServiceLocator.content.load().levels.size
        val today = DailyGenerator.todayKey()
        val diffLabel = { id: String -> id.replaceFirstChar { it.uppercase() } }
        val save = resume?.let { SaveCodec.decodeRun(it.stateJson) }
        val cleared = rows.filter { it.clears > 0 }.map { it.levelId }.toSet().size
        val medals = rows.count { it.medal != Scoring.Medal.NONE.id }
        val ready: HomeState = when {
            save != null && save.dailyKey == today ->
                HomeState.Ready("Continue today's map", "Saved at wave ${save.wave + 1}", Board(0, "standard", true, false, today), false, medals, cleared)
            save != null && save.dailyKey != null ->
                HomeState.Ready("Continue the daily map", "Map for ${save.dailyKey}, saved at wave ${save.wave + 1}", Board(0, "standard", true, false, save.dailyKey), false, medals, cleared)
            save != null && save.dailyKey == null && save.levelId in 1..count ->
                HomeState.Ready(
                    "Continue level ${save.levelId}",
                    "Saved at wave ${save.wave + 1}, ${diffLabel(save.difficulty)}",
                    Board(save.levelId, save.difficulty, false, false), false, medals, cleared,
                )
            else -> {
                val next = ProgressRules.nextLevel(rows, count)
                when {
                    rows.isEmpty() -> HomeState.Ready("Play level 1", null, Board(1, settings.lastDifficulty, false, true), true, 0, 0)
                    next != null -> HomeState.Ready("Play level $next", "${diffLabel(settings.lastDifficulty)} difficulty", Board(next, settings.lastDifficulty, false, true), false, medals, cleared)
                    else -> HomeState.Ready("Play today's map", "Every level cleared", Board(0, "standard", true, false), false, medals, cleared)
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
