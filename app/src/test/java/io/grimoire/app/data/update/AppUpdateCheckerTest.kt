package io.grimoire.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun semverKey_orderingBetweenBetas() {
        val a = AppUpdateChecker.SemverKey("0.0.21-beta.5")
        val b = AppUpdateChecker.SemverKey("0.0.21-beta.6")
        assertTrue(a < b)
    }

    @Test
    fun semverKey_releaseGreaterThanItsBeta() {
        val beta = AppUpdateChecker.SemverKey("0.0.21-beta.58")
        val release = AppUpdateChecker.SemverKey("0.0.21")
        assertTrue(beta < release)
    }

    @Test
    fun semverKey_newBaseBetaGreaterThanOlderRelease() {
        val older = AppUpdateChecker.SemverKey("0.0.21")
        val newer = AppUpdateChecker.SemverKey("0.0.22-beta.1")
        assertTrue(older < newer)
    }

    @Test
    fun semverKey_buildMetadataIgnored() {
        val withMeta = AppUpdateChecker.SemverKey("0.0.21-beta.58+abc123d")
        val withoutMeta = AppUpdateChecker.SemverKey("0.0.21-beta.58")
        assertEquals(0, withMeta.compareTo(withoutMeta))
    }

    @Test
    fun semverKey_doubleDigitBetaSortsNumerically() {
        val a = AppUpdateChecker.SemverKey("0.0.21-beta.9")
        val b = AppUpdateChecker.SemverKey("0.0.21-beta.10")
        // String compare would put "10" < "9"; semver numeric identifiers must
        // sort numerically.
        assertTrue(a < b)
    }
}
