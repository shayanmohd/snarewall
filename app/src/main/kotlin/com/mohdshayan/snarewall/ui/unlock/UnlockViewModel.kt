package com.mohdshayan.snarewall.ui.unlock

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.snarewall.billing.UnlockState
import com.mohdshayan.snarewall.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UnlockViewModel : ViewModel() {
    private val unlock = ServiceLocator.unlock

    val state: StateFlow<UnlockState> = unlock.state

    private val _note = MutableStateFlow(UnlockNote.NONE)
    val note: StateFlow<UnlockNote> = _note.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        // Asks Google Play for the price and the purchases as the screen opens.
        unlock.refresh()
        viewModelScope.launch {
            unlock.outcomes.collect { _note.value = UnlockRules.outcomeNote(it) }
        }
    }

    fun buy(activity: Activity) {
        _note.value = UnlockRules.launchNote(unlock.launchPurchase(activity))
    }

    /** Try again: the screen shows Loading until Google Play answers, then the state it answered with. */
    fun retry() = sync { UnlockNote.NONE }

    fun restore() = sync { answered -> UnlockRules.restoreNote(answered, unlock.state.value) }

    fun emailMissing() {
        _note.value = UnlockNote.NO_EMAIL
    }

    private fun sync(noteFor: (Boolean) -> UnlockNote) {
        if (_busy.value) return
        _note.value = UnlockNote.NONE
        _busy.value = true
        viewModelScope.launch {
            try {
                _note.value = noteFor(unlock.syncNow())
            } finally {
                _busy.value = false
            }
        }
    }
}
