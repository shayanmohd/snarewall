package com.mohdshayan.snarewall.di

import android.content.Context
import com.mohdshayan.snarewall.audio.Sfx
import com.mohdshayan.snarewall.content.ContentLoader
import com.mohdshayan.snarewall.data.ProgressRepository
import com.mohdshayan.snarewall.data.SaveTransfer
import com.mohdshayan.snarewall.data.db.AppDatabase
import com.mohdshayan.snarewall.data.prefs.AppPrefs

/** Manual dependency container, initialised in App.onCreate. */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun ctx(): Context =
        appContext ?: error("ServiceLocator.init() must be called before use")

    val appPrefs: AppPrefs by lazy { AppPrefs(ctx()) }
    val database: AppDatabase by lazy { AppDatabase.get(ctx()) }
    val content: ContentLoader by lazy { ContentLoader(ctx()) }
    val progress: ProgressRepository by lazy { ProgressRepository(database) }
    val saveTransfer: SaveTransfer by lazy { SaveTransfer(ctx(), progress, appPrefs) }
    val sfx: Sfx by lazy { Sfx(ctx()) }
}
