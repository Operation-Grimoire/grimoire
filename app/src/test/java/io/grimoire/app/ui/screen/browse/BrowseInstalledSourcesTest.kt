package io.grimoire.app.ui.screen.browse

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.Source
import io.grimoire.app.extension.ExtensionInfo
import io.grimoire.app.extension.LoadedExtension
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.RemoteExtension
import org.junit.Assert.*
import org.junit.Test

class BrowseInstalledSourcesTest {
    private val remote = RemoteExtension("Test", "test.extension", "en", "https://example.com", 2, "2.0", "test.apk", "https://example.com/test.apk")

    private fun loaded(compatible: Boolean, pkg: String = remote.pkg): LoadedExtension {
        val source = object : Source {
            override val name = "Test"
            override val lang: Language get() = if (compatible) Language.EN else throw AbstractMethodError("Source.getLang")
            override suspend fun getNovelDetails(novel: Novel): Novel = novel
        }
        return LoadedExtension(ExtensionInfo(pkg, "Grimoire: Test", "1.0", 1, "Test", "test.apk"), source)
    }

    @Test fun remoteCatalogAloneDoesNotCountAsInstalled() {
        for (items in listOf(emptyList(), listOf(ExtensionItem.Available(remote)))) {
            val state = projectInstalledBrowseSources(items)
            assertTrue(state.usable.isEmpty())
            assertFalse(state.hasUnavailable)
        }
    }

    @Test fun outdatedApksAreUnavailableRatherThanUninstalledWithOrWithoutRemoteMetadata() {
        val outdated = loaded(compatible = false)
        for (item in listOf(ExtensionItem.Installed(outdated, remote), ExtensionItem.InstalledOnly(outdated))) {
            val state = projectInstalledBrowseSources(listOf(item, ExtensionItem.Available(remote)))
            assertTrue(state.usable.isEmpty())
            assertTrue(state.hasUnavailable)
        }
    }

    @Test fun mixedCatalogKeepsOnlyUsableInstalledSourcesNavigable() {
        val usable = ExtensionItem.InstalledOnly(loaded(compatible = true, pkg = "test.usable"))
        val unavailable = ExtensionItem.Installed(loaded(compatible = false), remote)
        val state = projectInstalledBrowseSources(listOf(unavailable, ExtensionItem.Available(remote), usable))
        assertEquals(listOf(usable), state.usable)
        assertTrue(state.hasUnavailable)
        val repaired = projectInstalledBrowseSources(listOf(usable))
        assertEquals(listOf(usable), repaired.usable)
        assertFalse(repaired.hasUnavailable)
    }
}
