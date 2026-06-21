package io.grimoire.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves / shares a Coil-loadable image ([model] may be an http url, a local file
 * path/File, or a content uri). Bytes are fetched through the app's Coil
 * [coil.ImageLoader] so the same cache / headers as on-screen rendering apply.
 *
 * Downloads land in Pictures/Grimoire and show up in the system gallery: via
 * MediaStore on API 29+, and via the public Pictures dir + media scan on 26-28
 * (which needs WRITE_EXTERNAL_STORAGE, declared with maxSdkVersion=28).
 */
object ImageSaver {

    private const val ALBUM = "Grimoire"

    private suspend fun loadBitmap(context: Context, model: Any?): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(model)
            .allowHardware(false) // need a software bitmap to compress
            .build()
        val result = context.imageLoader.execute(request)
        return result.drawable?.toBitmap()
    }

    /** Saves [model] to Pictures/Grimoire. Returns true on success. */
    suspend fun saveToGallery(context: Context, model: Any?, baseName: String): Boolean =
        withContext(Dispatchers.IO) {
            val bitmap = loadBitmap(context, model) ?: return@withContext false
            val fileName = "${baseName.toFileSlug()}_${System.currentTimeMillis()}.jpg"
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveViaMediaStore(context, bitmap, fileName)
                } else {
                    saveToPublicDir(context, bitmap, fileName)
                }
            }.getOrDefault(false)
        }

    /**
     * Writes [model] to the share cache and returns a FileProvider uri suitable for
     * an ACTION_SEND intent, or null if the image could not be loaded.
     */
    suspend fun cacheForShare(context: Context, model: Any?, baseName: String): Uri? =
        withContext(Dispatchers.IO) {
            val bitmap = loadBitmap(context, model) ?: return@withContext null
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(dir, "${baseName.toFileSlug()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }

    private fun saveViaMediaStore(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        resolver.openOutputStream(uri).use { out ->
            if (out == null) return false
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDir(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        ).apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
        return true
    }

    private fun String.toFileSlug(): String =
        ifBlank { "image" }.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
}
