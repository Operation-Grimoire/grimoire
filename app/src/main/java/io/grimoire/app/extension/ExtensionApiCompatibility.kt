package io.grimoire.app.extension

import io.grimoire.api.source.Source
import io.grimoire.reading.supportsExtensionApi

internal fun loadCompatibleExtension(
    info: ExtensionInfo,
    hostApiVersion: String,
    load: () -> Source?,
): LoadedExtension? {
    if (!supportsExtensionApi(info.minimumApiVersion, hostApiVersion)) return LoadedExtension(info, null)
    return load()?.let { LoadedExtension(info, it) }
}

internal fun declaredExtensionApi(value: String?): String? = io.grimoire.reading.declaredExtensionApi(value)
