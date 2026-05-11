package io.grimoire.app.data.update

import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
)

data class ReleaseInfo(
    val tagName: String,
    val releaseNotes: String,
    val apkUrl: String,
)
