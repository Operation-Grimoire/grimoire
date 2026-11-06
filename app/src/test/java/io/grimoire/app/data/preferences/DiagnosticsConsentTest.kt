package io.grimoire.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsConsentTest {
    private fun preferences(data: Flow<Preferences>) = AnalyticsPreferences(DataStorePreferenceStore(
        object : DataStore<Preferences> {
            override val data = data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("No writes expected")
        },
    ))

    @Test fun readableMissingKeysRetainExistingDefaultOnBehavior() = runBlocking {
        val preferences = preferences(flowOf(emptyPreferences()))
        assertTrue(preferences.crashReportsEnabled.changes().first())
        assertTrue(preferences.usageAnalyticsEnabled.changes().first())
    }

    @Test fun persistedOptOutsRemainOff() = runBlocking {
        val preferences = preferences(flowOf(mutablePreferencesOf(
            booleanPreferencesKey("diagnostics_crash_reports") to false,
            booleanPreferencesKey("diagnostics_usage_analytics") to false,
        )))
        assertFalse(preferences.crashReportsEnabled.changes().first())
        assertFalse(preferences.usageAnalyticsEnabled.changes().first())
    }

    @Test fun unreadablePreferencesNeverBecomeDefaultOnConsent() = runBlocking {
        val preferences = preferences(flow { throw IOException("read failed") })
        assertFalse(preferences.crashReportsEnabled.changes().first())
        // The worker's separate first() read must also fail closed.
        assertFalse(preferences.crashReportsEnabled.changes().first())
        assertFalse(preferences.usageAnalyticsEnabled.changes().first())
    }

    @Test fun corruptFileRecoveryDoesNotTreatLostConsentAsANewInstall() = runBlocking {
        val preferences = preferences(flowOf(resetCorruptPreferences()))
        assertFalse(preferences.crashReportsEnabled.changes().first())
        assertFalse(preferences.usageAnalyticsEnabled.changes().first())
    }

    @Test fun cancellationAndProgrammingErrorsPropagate() = runBlocking {
        for (failure in listOf(CancellationException("cancel"), IllegalStateException("bug"))) {
            val preferences = preferences(flow { throw failure })
            try {
                preferences.crashReportsEnabled.changes().first()
                fail("Must propagate ${failure.javaClass.simpleName}")
            } catch (actual: Throwable) {
                assertSame(failure, actual)
            }
        }
    }
}
