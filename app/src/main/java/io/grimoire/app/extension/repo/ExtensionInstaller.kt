package io.grimoire.app.extension.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.di.GitHubAuthorized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

class HashMismatchException(
    val expected: String,
    val actual: String,
) : Exception("Downloaded APK hash did not match: expected $expected, got $actual")

@Singleton
class ExtensionInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @GitHubAuthorized private val client: OkHttpClient,
) {

    suspend fun download(
        apkUrl: String,
        packageName: String,
        expectedSha256: String? = null,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "$packageName.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(Request.Builder().url(apkUrl).build()).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body!!
                val total = body.contentLength().coerceAtLeast(0L)
                var read = 0L
                onProgress(read, total)
                file.outputStream().buffered().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            read += n
                            onProgress(read, total)
                        }
                    }
                }
            }
            if (expectedSha256 != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                    file.delete()
                    throw HashMismatchException(expectedSha256, actual)
                }
            }
            file
        }
    }
}
