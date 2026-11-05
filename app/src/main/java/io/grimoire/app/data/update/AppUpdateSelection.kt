package io.grimoire.app.data.update

import io.grimoire.app.data.preferences.UpdateChannel

private val BETA_TAG = Regex("""^v.+-beta\..+""")
private val SHA256 = Regex("[0-9a-fA-F]{64}")
private val CHECKSUM_WHITESPACE = Regex("""\s+""")
private const val MAX_AGGREGATED_RELEASES = 10

/** Mirrors app/build.gradle.kts; nightly.yml uses the tag's beta number as APP_BETA_NUMBER. */
internal fun releaseVersionCode(tag: String): Int {
    val name = tag.removePrefix("v")
    val parts = name.substringBefore('-').substringBefore('+').split('.').map { it.toIntOrNull() ?: 0 }
    val base = parts.getOrElse(0) { 0 } * 10_000_000 +
        parts.getOrElse(1) { 0 } * 100_000 + parts.getOrElse(2) { 0 } * 1_000
    val beta = name.substringAfter("-beta.", "").substringBefore('+').toIntOrNull() ?: 0
    return base + beta.coerceIn(0, 999)
}

internal fun compareSemver(a: String, b: String): Int =
    AppUpdateChecker.SemverKey(a.removePrefix("v")).compareTo(AppUpdateChecker.SemverKey(b.removePrefix("v")))

private fun betaChannelRelease(release: GitHubRelease): Boolean =
    !release.prerelease || BETA_TAG.matches(release.tag_name)

private fun List<GitHubRelease>.withLatestStable(stable: GitHubRelease?): List<GitHubRelease> =
    stable?.takeUnless { latest -> any { it.tag_name == latest.tag_name } }?.let { this + it } ?: this

private val VERSION_CODE_ORDER = compareBy<GitHubRelease> { releaseVersionCode(it.tag_name) }
    .thenBy { AppUpdateChecker.SemverKey(it.tag_name.removePrefix("v")) }

internal fun checkAppUpdate(
    channel: UpdateChannel,
    currentVersion: String,
    currentVersionCode: Int,
    fetchStable: () -> GitHubRelease?,
    fetchReleases: () -> List<GitHubRelease>,
    fetchChecksum: (String) -> String?,
): Result<ReleaseInfo?> = runCatching {
    val release = when (channel) {
        UpdateChannel.STABLE -> fetchStable()
        UpdateChannel.BETA -> fetchReleases().withLatestStable(fetchStable())
            .filter { it.tag_name.isNotBlank() && betaChannelRelease(it) }
            .maxWithOrNull(VERSION_CODE_ORDER)
    } ?: return@runCatching null
    if (releaseVersionCode(release.tag_name) <= currentVersionCode) return@runCatching null
    if (channel == UpdateChannel.STABLE && compareSemver(release.tag_name, currentVersion) <= 0) {
        return@runCatching null
    }
    val apk = requireNotNull(release.assets.firstOrNull { it.name.endsWith(".apk") })
    val checksum = release.assets.firstOrNull { it.name == "${apk.name}.sha256" }?.let {
        val hash = requireNotNull(fetchChecksum(it.browser_download_url)).trim()
            .split(CHECKSUM_WHITESPACE, limit = 2).first()
        require(SHA256.matches(hash))
        hash
    }
    ReleaseInfo(
        tagName = release.tag_name,
        displayVersion = release.name.ifBlank { release.tag_name }
            .removePrefix("Grimoire").trim().removePrefix("v"),
        releaseNotes = release.body,
        apkUrl = apk.browser_download_url,
        isPrerelease = release.prerelease,
        sha256 = checksum,
    )
}

internal fun notesReleasesSince(
    releases: List<GitHubRelease>,
    fromTag: String,
    toTag: String,
    channel: UpdateChannel,
    fetchLatestStable: () -> GitHubRelease? = { null },
): List<GitHubRelease> {
    val latestStable = runCatching { fetchLatestStable() }.getOrNull()
    val included = releases.withLatestStable(latestStable).filter { release ->
        if (release.tag_name.isBlank()) return@filter false
        when (channel) {
            UpdateChannel.STABLE -> !release.prerelease &&
                compareSemver(release.tag_name, fromTag) > 0 && compareSemver(release.tag_name, toTag) <= 0
            UpdateChannel.BETA -> betaChannelRelease(release) &&
                releaseVersionCode(release.tag_name) > releaseVersionCode(fromTag) &&
                releaseVersionCode(release.tag_name) <= releaseVersionCode(toTag)
        }
    }
    val order = when (channel) {
        UpdateChannel.STABLE -> compareBy<GitHubRelease> { AppUpdateChecker.SemverKey(it.tag_name.removePrefix("v")) }
        UpdateChannel.BETA -> VERSION_CODE_ORDER
    }
    return included.sortedWith(order.reversed()).take(MAX_AGGREGATED_RELEASES)
}
