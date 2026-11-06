package io.grimoire.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.grimoire.app.data.preferences.DataStorePreferenceStore
import io.grimoire.app.data.preferences.PreferenceStore
import io.grimoire.app.data.preferences.resetCorruptPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindPreferenceStore(impl: DataStorePreferenceStore): PreferenceStore

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                // Explicit reset-on-corruption: without a handler a corrupted file
                // throws on every read forever; with it ordinary settings reset
                // while diagnostics stay off because a prior opt-out is unknown.
                corruptionHandler = ReplaceFileCorruptionHandler { resetCorruptPreferences() },
                produceFile = { context.preferencesDataStoreFile("app_preferences") }
            )
    }
}
