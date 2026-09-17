package com.mohdshayan.snarewall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mohdshayan.snarewall.data.prefs.Settings
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.ui.nav.AppNav
import com.mohdshayan.snarewall.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by ServiceLocator.appPrefs.settings.collectAsState(initial = null as Settings?)
            val system = isSystemInDarkTheme()
            val dark = when (settings?.theme) {
                "dark" -> true
                "light" -> false
                else -> system
            }
            AppTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppNav()
                }
            }
        }
    }
}
