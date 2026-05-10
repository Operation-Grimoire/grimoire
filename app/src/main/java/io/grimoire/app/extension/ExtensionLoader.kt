package io.grimoire.app.extension

import android.content.Context
import dalvik.system.PathClassLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.source.Source
import javax.inject.Inject

class ExtensionLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun load(info: ExtensionInfo): Source? = runCatching {
        val classLoader = PathClassLoader(info.apkPath, context.classLoader)
        classLoader.loadClass(info.sourceClassName)
            .getDeclaredConstructor()
            .newInstance() as Source
    }.getOrNull()
}
