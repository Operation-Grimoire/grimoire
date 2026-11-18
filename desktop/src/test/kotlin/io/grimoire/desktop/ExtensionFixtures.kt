package io.grimoire.desktop

import io.grimoire.api.source.Source
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider

/** A compiled extension JAR; [initProperty] is set when its source class initializes. */
internal class FixtureJar(
    val pkg: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val bytes: ByteArray,
) {
    val sourceClass get() = "$pkg.Fixture"
    val file get() = "$pkg-$versionName.jar"
    val initProperty get() = "grimoire.fixture.init.$pkg.$versionCode"
}

internal fun fixtureJar(
    root: Path,
    pkg: String = "io.grimoire.extension.en.fixture",
    name: String = "Fixture",
    versionCode: Int = 1,
    versionName: String = "1.0.$versionCode",
    sourceVersionCode: Int = 7,
    coreApi: String = "1.5",
    failOnLoad: Boolean = false,
): FixtureJar {
    val work = Files.createTempDirectory(root, "jar-")
    val probe = "grimoire.fixture.init.$pkg.$versionCode"
    val source = work.resolve("Fixture.java")
    Files.writeString(source, """
        package $pkg;
        @io.grimoire.api.source.SourceInfo(name="$name", lang=io.grimoire.api.model.lang.Language.EN,
          baseUrl="https://example.org", versionCode=$sourceVersionCode)
        public class Fixture implements io.grimoire.api.source.Source {
          static {
            System.setProperty("$probe", "yes");
            if ($failOnLoad) throw new IllegalStateException("fixture refuses to load");
          }
          public String getName() { return "$name"; }
          public io.grimoire.api.model.lang.Language getLang() { return io.grimoire.api.model.lang.Language.EN; }
          public Object getNovelDetails(io.grimoire.api.model.novel.Novel n,
            kotlin.coroutines.Continuation<? super io.grimoire.api.model.novel.Novel> c) { return n; }
        }
    """.trimIndent())
    val classpath = listOf(Source::class.java, kotlin.coroutines.Continuation::class.java)
        .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct().joinToString(java.io.File.pathSeparator)
    val errors = java.io.ByteArrayOutputStream()
    val status = ToolProvider.getSystemJavaCompiler().run(null, null, errors, "-classpath", classpath, "-d", work.toString(), source.toString())
    check(status == 0) { errors.toString() }
    val manifest = Manifest().apply { mainAttributes.apply {
        putValue("Manifest-Version", "1.0")
        putValue("Grimoire-Extension-Format", "1")
        putValue("Grimoire-Source-Package", pkg)
        putValue("Grimoire-Source-Class", "$pkg.Fixture")
        putValue("Grimoire-Version-Code", versionCode.toString())
        putValue("Grimoire-Version-Name", versionName)
        putValue("Grimoire-Source-Version-Code", sourceVersionCode.toString())
        putValue("Grimoire-Core-Api", coreApi)
        putValue("Grimoire-Host-Libraries", HostRuntime.jsonCoordinate)
    } }
    val output = java.io.ByteArrayOutputStream()
    JarOutputStream(output, manifest).use { jar ->
        val entry = pkg.replace('.', '/') + "/Fixture.class"
        jar.putNextEntry(JarEntry(entry)); Files.copy(work.resolve(entry), jar); jar.closeEntry()
    }
    return FixtureJar(pkg, name, versionCode, versionName, output.toByteArray())
}

/** Signs indexes like the publication workflow does. */
internal class TestRepository(
    val repository: String = "io.grimoire.test",
    val indexUrl: String = "https://repo.example/$repository/jar-index.json",
    private val keys: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(),
) {
    val publicKey: String = Base64.getEncoder().encodeToString(keys.public.encoded.takeLast(32).toByteArray())
    val trust get() = RepositoryTrust(repository, indexUrl, publicKey)
    var sequence = 1L

    fun index(jars: List<FixtureJar>, sequence: Long = this.sequence, coreApi: String = "1.5",
              edit: (JSONObject, FixtureJar) -> Unit = { _, _ -> }): ByteArray {
        val extensions = JSONArray()
        jars.forEach { jar ->
            extensions.put(JSONObject()
                .put("pkg", jar.pkg).put("name", jar.name).put("lang", "en").put("sourceClass", jar.sourceClass)
                .put("versionCode", jar.versionCode).put("versionName", jar.versionName).put("sourceVersionCode", 7)
                .put("coreApi", coreApi).put("hostLibraries", JSONArray(listOf(HostRuntime.jsonCoordinate)))
                .put("file", jar.file).put("sha256", sha256(jar.bytes)).put("size", jar.bytes.size)
                .put("adultContent", "NONE")
                .also { edit(it, jar) })
        }
        return JSONObject().put("format", 1).put("repository", repository).put("sequence", sequence)
            .put("libraries", JSONArray()).put("extensions", extensions).toString().toByteArray()
    }

    fun sign(bytes: ByteArray): ByteArray = Signature.getInstance("Ed25519").run {
        initSign(keys.private); update(bytes)
        (Base64.getEncoder().encodeToString(sign()) + "\n").toByteArray()
    }

    /** Publishes the index, its signature and every JAR to [fetcher]. */
    fun publish(fetcher: FakeFetcher, jars: List<FixtureJar>, index: ByteArray = index(jars)) {
        val base = indexUrl.substringBeforeLast('/') + "/"
        jars.forEach { fetcher.files[base + it.file] = it.bytes }
        fetcher.files[indexUrl] = index
        fetcher.files["$indexUrl.sig"] = sign(index)
    }
}

internal class FakeFetcher : ArtifactFetcher {
    val files = mutableMapOf<String, ByteArray>()
    val requests = mutableListOf<String>()
    var online = true

    override fun fetch(url: String, limit: Long): ByteArray {
        requests += url
        if (!online) throw IOException("Offline mode is enabled")
        val bytes = files[url] ?: throw IOException("HTTP 404 for $url")
        if (bytes.size > limit) throw IOException("Download exceeds $limit bytes: $url")
        return bytes
    }
}
