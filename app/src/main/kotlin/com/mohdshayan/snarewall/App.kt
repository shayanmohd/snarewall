package com.mohdshayan.snarewall

import android.app.Application
import com.mohdshayan.snarewall.billing.LegacyOwner
import com.mohdshayan.snarewall.di.ServiceLocator

/**
 * Registered in the manifest as android:name=".App". It settles the prior-buyer answer, then wires
 * the service locator before any screen, worker or widget can ask for it.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Keep first: it looks for 1.0.0's database before anything in this build can open Room or DataStore.
        LegacyOwner.settle(this)
        ServiceLocator.init(this)
        // Built here, on the main thread, so no gate builds the one BillingClient first on another thread.
        ServiceLocator.unlock
    }
}
