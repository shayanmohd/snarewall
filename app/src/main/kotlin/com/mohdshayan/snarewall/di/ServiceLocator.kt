package com.mohdshayan.snarewall.di

import android.content.Context
import com.mohdshayan.snarewall.audio.Sfx
import com.mohdshayan.snarewall.billing.EntitlementOverride
import com.mohdshayan.snarewall.billing.LegacyOwner
import com.mohdshayan.snarewall.billing.UnlockRepository
import com.mohdshayan.snarewall.content.ContentLoader
import com.mohdshayan.snarewall.data.ProgressRepository
import com.mohdshayan.snarewall.data.SaveTransfer
import com.mohdshayan.snarewall.data.db.AppDatabase
import com.mohdshayan.snarewall.data.prefs.AppPrefs
import com.mohdshayan.snarewall.data.prefs.EntitlementStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    /** Outlives every screen, so a purchase result that lands mid-rotation is never lost. */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    /** The one BillingClient for the process, with this phone's copy of Google Play's answer. */
    val unlock: UnlockRepository by lazy {
        UnlockRepository(
            context = ctx(),
            store = EntitlementStore(ctx()),
            scope = appScope,
            // Snarewall was a paid app: LegacyOwner decided in App.onCreate whether this install bought it.
            // A debug build may force the answer for screenshots (src/debug); a release build never does.
            legacyOwner = { EntitlementOverride.forced() ?: LegacyOwner.isOwner(ctx()) },
        )
    }
}
