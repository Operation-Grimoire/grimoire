package io.grimoire.app.extension

import io.grimoire.api.model.lang.Language
import io.grimoire.api.source.Source
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.RemoteExtension
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ExtensionCompatibilityTest {
    private val info = ExtensionInfo("test.extension", "Grimoire: Test", "1.0", 1, "Test", "test.apk")
    private val remote = RemoteExtension("Test", info.packageName, "en", "https://example.com", 2, "2.0", "test.apk", "https://example.com/test.apk")

    private fun source(language: () -> Language): Source = Proxy.newProxyInstance(
        Source::class.java.classLoader, arrayOf(Source::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getLang" -> language()
            "getName" -> "Test"
            else -> error("Unexpected source call: ${method.name}")
        }
    } as Source

    @Test fun oldApiMetadataFailureKeepsUpdateAndRemovalIdentityAvailable() {
        val old = LoadedExtension(info, source { throw AbstractMethodError("Source.getLang") })
        assertFalse(old.isCompatible)
        val installed = ExtensionItem.Installed(old, remote)
        val sideloaded = ExtensionItem.InstalledOnly(old)
        assertFalse(installed.isCompatible)
        assertFalse(sideloaded.isCompatible)
        assertEquals("en", installed.lang)
        assertEquals(Language.UNKNOWN.code, sideloaded.lang)
        assertEquals(info.packageName, installed.packageName)
        assertEquals(info.packageName, sideloaded.packageName)
        assertTrue(installed.hasUpdate)
        assertEquals(remote.url, installed.apkUrl)
        // A bad source must not stop construction of the next installed row.
        val valid = LoadedExtension(info.copy(packageName = "test.valid"), source { Language.EN })
        assertTrue(ExtensionItem.InstalledOnly(valid).isCompatible)
        assertEquals("en", valid.lang)
    }

    @Test fun metadataIsReadOnceAtTheBoundary() {
        var calls = 0
        val loaded = LoadedExtension(info, source { calls++; Language.EN })
        repeat(3) { ExtensionItem.Installed(loaded, remote); ExtensionItem.InstalledOnly(loaded) }
        assertEquals(1, calls)
    }

    @Test fun cancellationAndFatalRuntimeFailuresAreNotTreatedAsIncompatibility() {
        for (failure in listOf(CancellationException("cancelled"), OutOfMemoryError("heap"), ThreadDeath())) {
            try {
                LoadedExtension(info, source { throw failure })
                fail("Must propagate ${failure.javaClass.simpleName}")
            } catch (actual: Throwable) {
                assertSame(failure, actual)
            }
        }
    }

    @Test fun unsupportedRequirementsNeverExecuteExtensionCodeAndRemainManageable() {
        for (requirement in listOf("1.6", "2.0", "0.9", "", "nonsense", "1.5.0", "1.5-SNAPSHOT", "2147483648.0")) {
            var calls = 0
            val loaded = loadCompatibleExtension(info.copy(minimumApiVersion = requirement), "1.5.0") {
                calls++
                source { Language.EN }
            }
            assertNotNull(requirement, loaded)
            assertEquals("Must reject $requirement before class loading", 0, calls)
            assertFalse(requirement, loaded!!.isCompatible)
            assertNull(loaded.source)
            val installed = ExtensionItem.Installed(loaded, remote)
            assertEquals(info.packageName, installed.packageName)
            assertTrue(installed.hasUpdate)
            assertFalse(installed.isCompatible)
        }
    }

    @Test fun legacyAndSupportedRequirementsLoadOnce() {
        for (host in listOf("1.5.0", "1.5.3", "1.5.0-SNAPSHOT")) {
            for (requirement in listOf(null, "1.0", "1.4", "1.5")) {
                var calls = 0
                val loaded = loadCompatibleExtension(info.copy(minimumApiVersion = requirement), host) {
                    calls++
                    source { Language.EN }
                }
                assertTrue(loaded!!.isCompatible)
                assertEquals(1, calls)
            }
        }
    }

    @Test fun invalidHostVersionRejectsDeclaredRequirementsBeforeLoading() {
        var called = false
        val loaded = loadCompatibleExtension(info.copy(minimumApiVersion = "1.5"), "not-a-version") {
            called = true
            source { Language.EN }
        }
        assertFalse(called)
        assertFalse(loaded!!.isCompatible)
    }
}
