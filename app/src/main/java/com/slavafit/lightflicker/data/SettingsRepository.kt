package com.slavafit.lightflicker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val language: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

class SettingsRepository(private val context: Context) {
    private val onboarding = booleanPreferencesKey("onboarding_done")
    private val language = stringPreferencesKey("language")
    private val theme = stringPreferencesKey("theme")

    val settings: Flow<AppSettings> = context.dataStore.data.map { values ->
        AppSettings(
            onboardingDone = values[onboarding] ?: false,
            language = values[language].orEmpty(),
            theme = runCatching {
                ThemeMode.valueOf(values[theme] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
        )
    }

    suspend fun completeOnboarding() = context.dataStore.edit { it[onboarding] = true }
    suspend fun setLanguage(value: String) = context.dataStore.edit { it[language] = value }
    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[theme] = value.name }
}
