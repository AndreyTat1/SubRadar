package com.subradar.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.subradar.app.domain.Money
import com.subradar.app.domain.Preferences
import com.subradar.app.domain.Theme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore("subradar_settings")

@Singleton
class PreferenceStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsStore
    private val theme = stringPreferencesKey("theme")
    private val currency = stringPreferencesKey("currency")
    private val notifications = booleanPreferencesKey("notifications")
    private val monday = booleanPreferencesKey("monday")
    private val language = stringPreferencesKey("language")
    val values = store.data.map(::read)
    val languages = store.data.map { it[language].orEmpty() }
    suspend fun current() = values.first()
    suspend fun language() = languages.first()
    suspend fun setLanguage(tag: String) { store.edit { it[language] = tag } }
    suspend fun update(transform: (Preferences) -> Preferences) {
        store.edit { write(it, transform(read(it))) }
    }
    suspend fun restore(value: Preferences, tag: String) {
        store.edit { write(it, value); it[language] = tag }
    }

    private fun read(p: androidx.datastore.preferences.core.Preferences) = Preferences(
        Theme.entries.firstOrNull { it.name == p[theme] } ?: Theme.DARK,
        p[currency]?.takeIf { it in Money.currencies } ?: "USD",
        p[notifications] ?: true, p[monday] ?: true
    )

    private fun write(p: MutablePreferences, value: Preferences) {
        p[theme] = value.theme.name
        p[currency] = value.currency
        p[notifications] = value.notifications
        p[monday] = value.mondayFirst
    }
}
