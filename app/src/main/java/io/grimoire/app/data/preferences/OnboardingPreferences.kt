package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPreferences @Inject constructor(store: PreferenceStore) {

    /** Whether the first-run welcome flow (app + reading languages) is done. */
    val done = store.getBoolean("onboarding_done", false)
}
