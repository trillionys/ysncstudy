package com.cpaconceptlab.study.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cpaconceptlab.study.domain.usecase.ReviewScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val fontScale: Float = 1.0f,
    val themeMode: String = "SYSTEM",
    val dailyGoal: Int = 30,
    val sessionSize: Int = 10,
    val reviewIntervals: String = "1,3,7,14,30",
    val lastScope: String = "ALL",
    val lastMode: String = "BASIC",
) {
    val intervals: List<Int> get() = ReviewScheduler.parse(reviewIntervals)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val FONT = floatPreferencesKey("font_scale")
        val THEME = stringPreferencesKey("theme_mode")
        val GOAL = intPreferencesKey("daily_goal")
        val SESSION = intPreferencesKey("session_size")
        val INTERVALS = stringPreferencesKey("review_intervals")
        val LAST_SCOPE = stringPreferencesKey("last_scope")
        val LAST_MODE = stringPreferencesKey("last_mode")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            fontScale = p[Keys.FONT] ?: 1.0f,
            themeMode = p[Keys.THEME] ?: "SYSTEM",
            dailyGoal = p[Keys.GOAL] ?: 30,
            sessionSize = p[Keys.SESSION] ?: 10,
            reviewIntervals = p[Keys.INTERVALS] ?: "1,3,7,14,30",
            lastScope = p[Keys.LAST_SCOPE] ?: "ALL",
            lastMode = p[Keys.LAST_MODE] ?: "BASIC",
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(current())
        write(next)
    }

    suspend fun write(s: UserSettings) {
        context.dataStore.edit { p ->
            p[Keys.FONT] = s.fontScale.coerceIn(0.8f, 1.6f)
            p[Keys.THEME] = s.themeMode
            p[Keys.GOAL] = s.dailyGoal.coerceIn(5, 300)
            p[Keys.SESSION] = s.sessionSize.coerceIn(5, 100)
            p[Keys.INTERVALS] = ReviewScheduler.parse(s.reviewIntervals).joinToString(",")
            p[Keys.LAST_SCOPE] = s.lastScope
            p[Keys.LAST_MODE] = s.lastMode
        }
    }
}
