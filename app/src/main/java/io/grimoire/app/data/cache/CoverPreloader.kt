package io.grimoire.app.data.cache

import android.content.Context
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import coil.request.CachePolicy
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.local.LibraryFavorites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverPreloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryFavorites: LibraryFavorites,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val warmed: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun start() {
        scope.launch {
            libraryFavorites.favorites
                .filterNotNull()
                .map { list -> list.mapNotNull { it.thumbnailUrl?.takeIf(String::isNotBlank) } }
                .distinctUntilChanged()
                .collect { urls -> urls.forEach(::warm) }
        }
    }

    // `warmed` is in-memory only, so on every cold start each library cover used to be
    // re-enqueued, and Coil 2 has no fetch-only mode: a target-less request still decodes
    // the bitmap at Size.ORIGINAL. That meant decoding every cover at full resolution on
    // the IO pool right while the Library was trying to draw its first page. Now a cover
    // that is already on disk is skipped outright, and a missing one is decoded at 1px
    // (the disk cache stores the source bytes regardless of the requested size).
    @OptIn(ExperimentalCoilApi::class)
    private fun warm(url: String) {
        if (!warmed.add(url)) return
        val imageLoader = Coil.imageLoader(context)
        val cached = runCatching { imageLoader.diskCache?.openSnapshot(url)?.use { true } }
            .getOrNull() == true
        if (cached) return
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(1)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
        imageLoader.enqueue(request)
    }
}
