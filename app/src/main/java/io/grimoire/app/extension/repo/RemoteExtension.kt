package io.grimoire.app.extension.repo

import kotlinx.serialization.Serializable

@Serializable
data class RemoteExtension(
    val name: String,
    val pkg: String,
    val lang: String,
    val baseUrl: String,
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val url: String,
)
