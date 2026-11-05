package io.grimoire.app.data.update

import io.grimoire.app.data.preferences.UpdateChannel
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class AppUpdateSelectionTest {
    private fun release(tag: String, checksum: Boolean = false) = GitHubRelease(
        tag_name = tag,
        prerelease = tag.contains("-beta."),
        assets = listOf(GitHubAsset("grimoire.apk", "https://release/apk")) +
            if (checksum) listOf(GitHubAsset("grimoire.apk.sha256", "https://release/hash")) else emptyList(),
    )

    private fun check(
        current: String = "1.0.0",
        stable: GitHubRelease? = release("v1.0.1"),
        channel: UpdateChannel = UpdateChannel.STABLE,
        releases: List<GitHubRelease> = emptyList(),
        checksum: String? = null,
    ) = checkAppUpdate(channel, current, releaseVersionCode(current), { stable }, { releases }, { checksum })

    @Test fun versionCodeMirrorsStableAndNightlyBuilds() {
        assertEquals(10_203_000, releaseVersionCode("v1.2.3"))
        assertEquals(10_203_302, releaseVersionCode("v1.2.3-beta.302"))
        assertEquals(38_999, releaseVersionCode("v0.0.38-beta.999"))
        assertTrue(releaseVersionCode("v0.0.38-beta.999") < releaseVersionCode("v1.0.0"))
    }

    @Test fun versionCodeClampsBetaAndIgnoresBuildMetadata() {
        assertEquals(10_000_000, releaseVersionCode("v1.0.0-beta.-1"))
        assertEquals(10_000_999, releaseVersionCode("v1.0.0-beta.1000+sha"))
        assertEquals(releaseVersionCode("1.0.0-beta.999"), releaseVersionCode("1.0.0-beta.1001"))
    }

    @Test fun stableDoesNotOfferSameVersionWithDifferentMetadata() {
        assertNull(check(current = "1.0.0+local", stable = release("v1.0.0")).getOrThrow())
    }

    @Test fun stableDoesNotOfferAnOlderRelease() {
        assertNull(check(current = "1.0.1", stable = release("v1.0.0")).getOrThrow())
    }

    @Test fun beta302IsNotOfferedTheUninstallableSameBaseStable() {
        assertTrue(compareSemver("1.0.0", "1.0.0-beta.302") > 0)
        assertNull(check(current = "1.0.0-beta.302", stable = release("v1.0.0")).getOrThrow())
    }

    @Test fun stableOnBetaChannelReceivesSameBaseBeta303() {
        val actual = check(stable = release("v1.0.0"), channel = UpdateChannel.BETA,
            releases = listOf(release("v1.0.0-beta.303"))).getOrThrow()
        assertEquals("v1.0.0-beta.303", actual?.tagName)
    }

    @Test fun betaChannelReceivesAHigherStableRelease() {
        val actual = check(current = "1.0.0-beta.302", channel = UpdateChannel.BETA,
            releases = listOf(release("v1.0.0-beta.303"))).getOrThrow()
        assertEquals("v1.0.1", actual?.tagName)
    }

    @Test fun stableChannelBetaBuildCanReceiveTheNextStableBase() {
        assertEquals("v1.0.1", check(current = "1.0.0-beta.302").getOrThrow()?.tagName)
    }

    @Test fun olderBaseBetasRankBelowTheStableRelease() {
        val actual = check(current = "0.0.38-beta.900", stable = release("v1.0.0"),
            channel = UpdateChannel.BETA, releases = listOf(release("v0.0.38-beta.999"))).getOrThrow()
        assertEquals("v1.0.0", actual?.tagName)
    }

    @Test fun betaAfter999CannotUpdateAnEqualAndroidVersionCode() {
        val actual = check(current = "1.0.0-beta.999", stable = release("v1.0.0"),
            channel = UpdateChannel.BETA, releases = listOf(release("v1.0.0-beta.1000")))
        assertTrue(actual.isSuccess)
        assertNull(actual.getOrThrow())
    }

    @Test fun betaChannelIncludesStableEvenWhenItIsAbsentFromTheListPage() {
        val actual = check(channel = UpdateChannel.BETA, releases = listOf(release("v1.0.0-beta.303")))
        assertEquals("v1.0.1", actual.getOrThrow()?.tagName)
    }

    @Test fun betaChannelDoesNotAdmitUnrelatedPrereleases() {
        val alpha = release("v2.0.0-alpha.1").copy(prerelease = true)
        val actual = check(stable = release("v1.0.0"), channel = UpdateChannel.BETA,
            releases = listOf(alpha, release("v1.0.0-beta.303")))
        assertEquals("v1.0.0-beta.303", actual.getOrThrow()?.tagName)
    }

    @Test fun offlineRateLimitAndMalformedReleaseFailuresRemainFailures() {
        for (failure in listOf(IOException("offline"), IOException("HTTP 403"), IllegalArgumentException("bad JSON"))) {
            val actual = checkAppUpdate(UpdateChannel.STABLE, "1.0.0", 10_000_000,
                { throw failure }, { emptyList() }, { null })
            assertSame(failure, actual.exceptionOrNull())
        }
    }

    @Test fun betaListFailureIsNotNoUpdate() {
        val failure = IOException("HTTP 429")
        val actual = checkAppUpdate(UpdateChannel.BETA, "1.0.0", 10_000_000,
            { release("v1.0.1") }, { throw failure }, { null })
        assertSame(failure, actual.exceptionOrNull())
    }

    @Test fun noPublishedReleaseIsASuccessfulNoUpdate() {
        val actual = check(stable = null)
        assertTrue(actual.isSuccess)
        assertNull(actual.getOrThrow())
    }

    @Test fun missingApkIsAFailedCheck() {
        assertTrue(check(stable = release("v1.0.1").copy(assets = emptyList())).isFailure)
    }

    @Test fun oldReleaseWithoutChecksumStillWorksWithoutFetchingOne() {
        val actual = checkAppUpdate(UpdateChannel.STABLE, "1.0.0", 10_000_000,
            { release("v1.0.1") }, { emptyList() }, { error("No checksum should be fetched") })
        assertNull(actual.getOrThrow()?.sha256)
        assertEquals("v1.0.1", actual.getOrThrow()?.tagName)
    }

    @Test fun validChecksumSupportsSha256sumWhitespaceAndUppercase() {
        val hash = "ABCDEF0123456789".repeat(4)
        val actual = check(stable = release("v1.0.1", true), checksum = "$hash\tgrimoire.apk\n")
        assertEquals(hash, actual.getOrThrow()?.sha256)
    }

    @Test fun advertisedMissingOrMalformedChecksumsFailClosed() {
        for (body in listOf(null, "", "a".repeat(63), "z".repeat(64), "\u0661".repeat(64))) {
            assertTrue(check(stable = release("v1.0.1", true), checksum = body).isFailure)
        }
    }

    @Test fun checksumFetchExceptionBecomesAFailedCheck() {
        val failure = IOException("HTTP 503")
        val actual = checkAppUpdate(UpdateChannel.STABLE, "1.0.0", 10_000_000,
            { release("v1.0.1", true) }, { emptyList() }, { throw failure })
        assertSame(failure, actual.exceptionOrNull())
    }

    @Test fun checksumBelongsToTheSelectedApk() {
        val stable = release("v1.0.1").copy(assets = release("v1.0.1").assets +
            GitHubAsset("other.apk.sha256", "https://release/other"))
        val actual = checkAppUpdate(UpdateChannel.STABLE, "1.0.0", 10_000_000,
            { stable }, { emptyList() }, { error("Unrelated checksum must not be used") })
        assertEquals("v1.0.1", actual.getOrThrow()?.tagName)
        assertNull(actual.getOrThrow()?.sha256)
    }

    @Test fun betaNotesIncludeStableTransitionsInVersionCodeOrder() {
        val actual = notesReleasesSince(listOf(release("v1.0.0-beta.302"), release("v1.0.0-beta.303"),
            release("v1.0.1"), release("v0.0.38-beta.999")), "1.0.0-beta.302", "1.0.1", UpdateChannel.BETA)
        assertEquals(listOf("v1.0.1", "v1.0.0-beta.303"), actual.map { it.tag_name })
    }

    @Test fun betaNotesIncludeLatestStableOutsideTheListPage() {
        val actual = notesReleasesSince(listOf(release("v1.0.0-beta.303")),
            "1.0.0-beta.302", "1.0.1", UpdateChannel.BETA, release("v1.0.1"))
        assertEquals(listOf("v1.0.1", "v1.0.0-beta.303"), actual.map { it.tag_name })
    }

    @Test fun latestStableAppearsOnlyOnceWhenAlreadyListed() {
        val actual = notesReleasesSince(listOf(release("v1.0.1"), release("v1.0.0-beta.303")),
            "1.0.0-beta.302", "1.0.1", UpdateChannel.BETA, release("v1.0.1"))
        assertEquals(listOf("v1.0.1", "v1.0.0-beta.303"), actual.map { it.tag_name })
    }

    @Test fun betaNotesFromStableIncludeNewerSameBaseBetas() {
        val actual = notesReleasesSince(listOf(release("v1.0.0"), release("v1.0.0-beta.302"),
            release("v1.0.0-beta.303"), release("v1.0.1")), "1.0.0", "1.0.0-beta.303", UpdateChannel.BETA)
        assertEquals(listOf("v1.0.0-beta.303", "v1.0.0-beta.302"), actual.map { it.tag_name })
    }

    @Test fun stableNotesExcludePrereleasesAndRespectTheRange() {
        val actual = notesReleasesSince(listOf(release("v1.0.0"), release("v1.0.1"),
            release("v1.0.1-beta.303"), release("v1.0.2")), "0.0.38", "1.0.1", UpdateChannel.STABLE)
        assertEquals(listOf("v1.0.1", "v1.0.0"), actual.map { it.tag_name })
    }

    @Test fun aggregatedNotesKeepTheTenNewestEligibleReleases() {
        val actual = notesReleasesSince((1..20).map { release("v1.0.0-beta.$it") },
            "1.0.0", "1.0.0-beta.20", UpdateChannel.BETA)
        assertEquals((20 downTo 11).map { "v1.0.0-beta.$it" }, actual.map { it.tag_name })
    }
}
