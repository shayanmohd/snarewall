package com.mohdshayan.snarewall

import android.app.Application
import com.mohdshayan.snarewall.di.ServiceLocator

/**
 * Registered in the manifest as android:name=".App". The only job here is to
 * wire the service locator before any screen, worker or widget can ask for it.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
