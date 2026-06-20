package io.grimoire.app.extension

import io.grimoire.api.source.Source
import io.grimoire.api.source.sourceIdFor

data class LoadedExtension(
    val info: ExtensionInfo,
    val source: Source,
) {
    /** Canonical identity the app keys saved novels by — derived from the package, not [Source.id]. */
    val id: Long get() = sourceIdFor(info.packageName)
}
