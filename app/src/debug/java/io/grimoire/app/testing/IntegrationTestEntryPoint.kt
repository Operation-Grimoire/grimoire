package io.grimoire.app.testing

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.grimoire.app.data.local.AppDatabase
import io.grimoire.app.data.preferences.AnalyticsPreferences
import io.grimoire.app.data.preferences.IncognitoManager
import io.grimoire.app.data.preferences.OnboardingPreferences
import io.grimoire.app.data.preferences.ReaderPreferences
import io.grimoire.app.data.preferences.TourPreferences
import io.grimoire.app.data.reading.AndroidOpenedHistory

/** Debug-host access to real bindings. No replacement providers; absent from the release source set. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface IntegrationTestEntryPoint {
    fun database(): AppDatabase
    fun onboarding(): OnboardingPreferences
    fun reader(): ReaderPreferences
    fun tours(): TourPreferences
    fun analytics(): AnalyticsPreferences
    fun incognito(): IncognitoManager
    fun openedHistory(): AndroidOpenedHistory
}
