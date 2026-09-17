package com.mohdshayan.snarewall.ui.levels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.game.GameContent
import com.mohdshayan.snarewall.game.LevelProgressRow
import com.mohdshayan.snarewall.game.RunSave
import com.mohdshayan.snarewall.game.SaveCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LevelsState {
    data object Loading : LevelsState
    data object Error : LevelsState
    data class Ready(
        val content: GameContent,
        val rows: List<LevelProgressRow>,
        val difficulty: String,
        val resume: RunSave?,
    ) : LevelsState
}

class LevelsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs

    private val contentFlow = flow { emit(ServiceLocator.content.load()) }.flowOn(Dispatchers.IO)

    val state: StateFlow<LevelsState> = combine(
        contentFlow, ServiceLocator.progress.progress, prefs.settings, ServiceLocator.progress.resume,
    ) { content, rows, settings, resume ->
        LevelsState.Ready(content, rows, settings.lastDifficulty, resume?.let { SaveCodec.decodeRun(it.stateJson) }) as LevelsState
    }
        .catch { emit(LevelsState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LevelsState.Loading)

    fun setDifficulty(id: String) {
        viewModelScope.launch { prefs.setLastDifficulty(id) }
    }
}
