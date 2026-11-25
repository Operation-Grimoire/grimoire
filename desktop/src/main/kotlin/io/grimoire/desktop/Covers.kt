package io.grimoire.desktop

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** Covers are cached on disk so the library keeps them offline; offline mode blocks cover requests too. */
fun installCoverLoader(data: Path, networkEnabled: AtomicBoolean) {
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            if (!networkEnabled.get()) throw IOException("Offline")
            chain.proceed(chain.request())
        }
        .build()
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .diskCache { DiskCache.Builder().directory(data.resolve("cover-cache").toOkioPath()).maxSizeBytes(256L * 1024 * 1024).build() }
            .build()
    }
}

/** Tracks whether desktop network requests are enabled so offline cover failures retry when back online. */
internal val LocalCoversOnline = compositionLocalOf { true }

/** A book cover, or an empty tile when the source gave none or it cannot load. */
@Composable
fun BookCover(url: String?, title: String, modifier: Modifier) {
    val online = LocalCoversOnline.current
    var failedOffline by remember(url) { mutableStateOf(false) }
    var retryKey by remember(url) { mutableStateOf(0) }
    LaunchedEffect(online, failedOffline) {
        if (online && failedOffline) {
            failedOffline = false
            retryKey++
        }
    }
    key(retryKey) {
        AsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            onError = { if (!online) failedOffline = true },
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
