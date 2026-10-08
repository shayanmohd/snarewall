package com.mohdshayan.snarewall.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

// Its own file, datastore/entitlement.preferences_pb, so backup_rules.xml and data_extraction_rules.xml
// leave it out: a new phone reads the unlock back from Google Play, never from an old phone's copy.
private val Context.entitlementStore: DataStore<Preferences> by preferencesDataStore(name = "entitlement")

/** This phone's copy of Google Play's last answer about the unlock. Play stays the source of truth. */
class EntitlementStore(private val context: Context) {

    private val unlockedKey = booleanPreferencesKey("unlocked")

    suspend fun unlocked(): Boolean =
        context.entitlementStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()[unlockedKey] ?: false

    suspend fun setUnlocked(unlocked: Boolean) {
        try {
            context.entitlementStore.edit { it[unlockedKey] = unlocked }
        } catch (e: IOException) {
            // The in-memory state still holds, and the next sync writes again.
        }
    }
}
