package io.grimoire.desktop

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.source.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider

class LocalExtensionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val pkg = "io.grimoire.extension.fixture"

    @Before fun resetProbe() { System.clearProperty("grimoire.fixture.initialized"); System.clearProperty("grimoire.fixture.helper") }

    @Test fun verifiedSnapshotSupportsLateClassLoadingAfterOriginalIsReplaced() = runBlocking {
        val manifest = fixture()
        LocalExtension.open(manifest, "1.5.0").use { extension ->
            assertEquals("Fixture", extension.source.name)
            assertEquals("yes", System.getProperty("grimoire.fixture.initialized"))
            assertNull(System.getProperty("grimoire.fixture.helper"))
            Files.write(manifest.parent.resolve("source.jar"), byteArrayOf(0))
            val novel = Novel("/novel", "Test", Language.EN)
            assertEquals(novel, extension.source.getNovelDetails(novel))
            assertEquals("yes", System.getProperty("grimoire.fixture.helper"))
        }
        resetProbe()
        rejectsBeforeInitialization(manifest)
    }

    @Test fun digestMismatchIsRejectedBeforeSourceCodeRuns() {
        rejectsBeforeInitialization(fixture(configure = { setProperty("source.sha256", "0".repeat(64)) }))
    }

    @Test fun identityAndVersionMismatchAreRejectedBeforeSourceCodeRuns() {
        for (field in listOf("source.package", "source.class", "source.versionCode", "source.versionName", "source.sourceVersionCode")) {
            val manifest = fixture(configure = { setProperty(field, if (field.endsWith("Code")) "999" else "mismatch") })
            rejectsBeforeInitialization(manifest)
        }
    }

    @Test fun missingAndMalformedOrIncompatibleApiAreRejectedBeforeSourceCodeRuns() {
        rejectsBeforeInitialization(fixture(attributes = { remove(Attributes.Name("Grimoire-Core-Api")) }))
        for (api in listOf("1.5.0", "2.0", "1.6", "01.5", "1.2147483648")) {
            rejectsBeforeInitialization(fixture(
                attributes = { putValue("Grimoire-Core-Api", api) },
                configure = { setProperty("source.coreApi", api) },
            ))
        }
    }

    @Test fun unknownHostLibrariesAndExternalClasspathAreRejectedBeforeSourceCodeRuns() {
        rejectsBeforeInitialization(fixture(attributes = { putValue("Grimoire-Host-Libraries", "unknown:library:1") }))
        rejectsBeforeInitialization(fixture(attributes = { putValue("Class-Path", "unverified.jar") }))
        rejectsBeforeInitialization(fixture(extraClass = "io/grimoire/api/Injected.class"))
    }

    @Test fun annotationMismatchIsRejectedWithoutInitializingTheClass() {
        rejectsBeforeInitialization(fixture(
            attributes = { putValue("Grimoire-Source-Version-Code", "8") },
            configure = { setProperty("source.sourceVersionCode", "8") },
        ))
    }

    private fun rejectsBeforeInitialization(manifest: Path) {
        try {
            LocalExtension.open(manifest, "1.5.0").use { fail("Invalid artifact was loaded") }
        } catch (failure: IllegalArgumentException) {
            assertNotNull(failure.message)
        }
        assertNull("Rejected artifact initialized source code", System.getProperty("grimoire.fixture.initialized"))
    }

    private fun fixture(
        attributes: Attributes.() -> Unit = {},
        configure: Properties.() -> Unit = {},
        extraClass: String? = null,
    ): Path {
        val root = temporary.newFolder().toPath()
        val source = root.resolve("Fixture.java")
        Files.writeString(source, """
            package $pkg;
            @io.grimoire.api.source.SourceInfo(name="Fixture", lang=io.grimoire.api.model.lang.Language.EN,
              baseUrl="https://example.org", versionCode=7)
            public class Fixture implements io.grimoire.api.source.Source {
              static { System.setProperty("grimoire.fixture.initialized", "yes"); }
              public String getName() { return "Fixture"; }
              public io.grimoire.api.model.lang.Language getLang() { return io.grimoire.api.model.lang.Language.EN; }
              public Object getNovelDetails(io.grimoire.api.model.novel.Novel n,
                kotlin.coroutines.Continuation<? super io.grimoire.api.model.novel.Novel> c) { return Helper.details(n); }
            }
            class Helper {
              static { System.setProperty("grimoire.fixture.helper", "yes"); }
              static Object details(Object novel) { return novel; }
            }
        """.trimIndent())
        val classpath = listOf(Source::class.java, kotlin.coroutines.Continuation::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct().joinToString(java.io.File.pathSeparator)
        val errors = java.io.ByteArrayOutputStream()
        val status = ToolProvider.getSystemJavaCompiler().run(null, null, errors, "-classpath", classpath, "-d", root.toString(), source.toString())
        check(status == 0) { errors.toString() }
        val manifest = Manifest().apply { mainAttributes.apply {
            putValue("Manifest-Version", "1.0")
            putValue("Grimoire-Extension-Format", "1")
            putValue("Grimoire-Source-Package", pkg)
            putValue("Grimoire-Source-Class", "$pkg.Fixture")
            putValue("Grimoire-Version-Code", "3")
            putValue("Grimoire-Version-Name", "1.0.0")
            putValue("Grimoire-Source-Version-Code", "7")
            putValue("Grimoire-Core-Api", "1.5")
            putValue("Grimoire-Host-Libraries", "org.json:json:20240303")
            attributes()
        } }
        val jar = root.resolve("source.jar")
        JarOutputStream(Files.newOutputStream(jar), manifest).use { output ->
            for (name in listOf("Fixture", "Helper")) {
                val entry = pkg.replace('.', '/') + "/$name.class"
                output.putNextEntry(JarEntry(entry)); Files.copy(root.resolve(entry), output); output.closeEntry()
            }
            extraClass?.let { output.putNextEntry(JarEntry(it)); output.write(0); output.closeEntry() }
        }
        val config = Properties().apply {
            setProperty("source.file", "source.jar")
            setProperty("source.sha256", sha256(Files.readAllBytes(jar)))
            setProperty("source.package", pkg)
            setProperty("source.class", "$pkg.Fixture")
            setProperty("source.versionCode", "3")
            setProperty("source.versionName", "1.0.0")
            setProperty("source.sourceVersionCode", "7")
            setProperty("source.coreApi", "1.5")
            configure()
        }
        return root.resolve("extension.properties").also { path -> Files.newOutputStream(path).use { config.store(it, "Trusted test artifacts") } }
    }
}
