package io.grimoire.app.data.cache

import android.content.Context
import coil.Coil
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

    private fun warm(url: String) {
        if (!warmed.add(url)) return
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
        Coil.imageLoader(context).enqueue(request)
    }
}
