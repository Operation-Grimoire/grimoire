package io.grimoire.app.extension

import io.grimoire.api.source.Source

data class LoadedExtension(
    val info: ExtensionInfo,
    val source: Source,
)
