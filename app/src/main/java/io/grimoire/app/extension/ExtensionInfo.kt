package io.grimoire.app.extension

data class ExtensionInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val sourceClassName: String,
    val apkPath: String,
)
