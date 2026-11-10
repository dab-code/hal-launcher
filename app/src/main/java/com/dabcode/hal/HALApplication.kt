package com.dabcode.hal

import android.app.Application
import com.dabcode.settings.SettingsRepository
import com.dabcode.settings.ThemeSettings
import com.dabcode.settings.UiStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HALApplication : Application() {
    val settingsRepository by lazy {
        SettingsRepository(applicationContext)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The theme values, so that the first Compose frame of each Activity has the correct theme.
    // Only applyTheme() writes them.
    @Volatile var cachedIsDarkMode: Boolean = SettingsRepository.DEFAULT_IS_DARK_MODE
        private set
    @Volatile var cachedUiStyle: UiStyle = SettingsRepository.DEFAULT_UI_STYLE
        private set
    @Volatile var cachedFontSizeScale: String = SettingsRepository.DEFAULT_FONT_SIZE_SCALE
        private set

    override fun onCreate() {
        super.onCreate()
        // Read the theme one time before the first Activity starts.
        try {
            applyTheme(runBlocking { settingsRepository.themeSettings.first() })
        } catch (_: Exception) {
            // Keep the defaults if the read fails.
        }
        // The review line waits 7 days from the first run with this key. Current users also wait.
        appScope.launch {
            try { settingsRepository.markFirstSeenDay(com.dabcode.settings.localEpochDay()) } catch (_: Exception) {}
        }
        // Keep the cache up to date when the user changes the theme.
        appScope.launch {
            settingsRepository.themeSettings.collect { applyTheme(it) }
        }
    }

    private fun applyTheme(theme: ThemeSettings) {
        cachedIsDarkMode = theme.isDarkMode
        cachedUiStyle = theme.uiStyle
        cachedFontSizeScale = theme.fontSizeScale
    }
}
