package io.grimoire.app.data.update

import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String = "",
    val body: String = "",
    val prerelease: Boolean = false,
    val target_commitish: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
)

data class ReleaseInfo(
    val tagName: String,
    val displayVersion: String,
    val releaseNotes: String,
    val apkUrl: String,
    val isPrerelease: Boolean,
    val sha256: String? = null,
)
