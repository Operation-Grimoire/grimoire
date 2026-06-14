package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AthenaeumPreferences @Inject constructor(store: PreferenceStore) {
    /**
     * Opt-in: contribute scraped catalogue data (series + chapters) to
     * Athenaeum as you browse and on library refresh. Default off — nothing
     * leaves the device until the user turns it on (and usually pairs).
     */
    val contributeEnabled = store.getBoolean("athenaeum_contribute_enabled", false)
}
