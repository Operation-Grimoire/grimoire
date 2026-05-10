package io.grimoire.app.extension.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.network.defaultOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = defaultOkHttpClient()

    suspend fun download(apkUrl: String, packageName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "$packageName.apk")
                client.newCall(Request.Builder().url(apkUrl).build()).execute().use { response ->
                    check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                    file.outputStream().use { response.body!!.byteStream().copyTo(it) }
                }
                file
            }
        }

}
