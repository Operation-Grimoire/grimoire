import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

fun gitOutput(vararg args: String): String =
    providers.exec {
        commandLine(*args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

fun gitTag(): String {
    val env = System.getenv("APP_VERSION_TAG")
    if (!env.isNullOrBlank() && env.startsWith("v")) return env
    return runCatching {
        gitOutput("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*.[0-9]*.[0-9]*")
            .takeIf { it.startsWith("v") } ?: "v0.0.0"
    }.getOrDefault("v0.0.0")
}

fun String.toVersionCode(): Int {
    val core = removePrefix("v").substringBefore('-').substringBefore('+')
    val parts = core.split(".").map { it.toIntOrNull() ?: 0 }
    val base = parts.getOrElse(0) { 0 } * 10_000_000 +
               parts.getOrElse(1) { 0 } * 100_000 +
               parts.getOrElse(2) { 0 } * 1_000
    val beta = System.getenv("APP_BETA_NUMBER")?.toIntOrNull() ?: 0
    return base + beta.coerceIn(0, 999)
}

fun gitSha(): String {
    val env = System.getenv("APP_GIT_SHA")
    if (!env.isNullOrBlank()) return env
    return runCatching {
        gitOutput("git", "rev-parse", "HEAD")
    }.getOrDefault("")
}

val appTag = gitTag()
val appVersionName = System.getenv("APP_VERSION_NAME")
    ?.takeIf { it.isNotBlank() }
    ?: appTag.removePrefix("v")
val appGitSha = gitSha()

// Single source of truth for the extensions-api version — used for the Gradle
// dependency and for the BuildConfig field shown on the About screen.
val extensionsApiVersion = providers.gradleProperty("extensionsApiVersion").orElse(libs.versions.extensionsApi).get()

// OAuth App client ID — public, ships in the APK. Read from gradle.properties
// (or env GITHUB_OAUTH_CLIENT_ID) so a fork can substitute its own without
// patching code.
val githubOAuthClientId: String =
    (project.findProperty("GITHUB_OAUTH_CLIENT_ID") as? String)
        ?: System.getenv("GITHUB_OAUTH_CLIENT_ID")
        ?: ""

// Aptabase app key for anonymous usage analytics and crash reports.
// Resolved from gradle property > env var > local.properties (gitignored), so the
// key can vary by build. Empty ⇒ both integrations are inert;
// each also respects its separate opt-out preference at runtime.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
fun resolveSecret(name: String): String =
    (project.findProperty(name) as? String)
        ?: System.getenv(name)
        ?: localProperties.getProperty(name)
        ?: ""
val aptabaseAppKey: String = resolveSecret("APTABASE_APP_KEY")

android {
    namespace = "io.grimoire.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.grimoire.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appTag.toVersionCode()
        versionName = appVersionName

        buildConfigField("String", "GIT_SHA", "\"$appGitSha\"")
        buildConfigField("String", "EXTENSIONS_API_VERSION", "\"$extensionsApiVersion\"")
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"$githubOAuthClientId\"")
        buildConfigField("String", "APTABASE_APP_KEY", "\"$aptabaseAppKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":reading-core"))
    implementation(project(":shared-ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("io.grimoire:extensions-api:$extensionsApiVersion")
    implementation("com.github.aptabase:aptabase-kotlin:0.0.8") { // privacy-first analytics
        // Upstream POM leaks androidx.test:monitor into production runtime;
        // debug/androidTest declare it explicitly below for ActivityScenario (#456).
        exclude(group = "androidx.test")
    }
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.reorderable)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.media)
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.junit)
    // Real SQLite on the JVM for the chapter-content migration test.
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.monitor)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Match the existing instrumentation core. Its helper Activities must run in the host,
    // alongside ActivityInvoker already provided by the host runtime (see #456).
    debugImplementation(libs.androidx.test.core)
    // Align the compile graph with the host's existing runtime version and trusted CI cache.
    debugImplementation(libs.androidx.test.monitor)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

abstract class MergedAssetsArguments : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsDir: DirectoryProperty
    override fun asArguments() = listOf("-Dgrimoire.mergedAssetsDir=${assetsDir.get().asFile.absolutePath}")
}

abstract class VerifyReleaseRuntimeClasspathTask : DefaultTask() {
    @get:Input
    abstract val runtimeModules: ListProperty<String>

    @TaskAction
    fun verify() {
        val forbidden = runtimeModules.get().filter { coord ->
            val group = coord.substringBefore(':')
            group == "androidx.test" || group.startsWith("androidx.test.") || group == "junit"
        }
        check(forbidden.isEmpty()) {
            "Test-only dependencies leaked into releaseRuntimeClasspath: ${forbidden.joinToString()}"
        }
    }
}

androidComponents.onVariants { variant ->
    val unitTestName = "test${variant.name.replaceFirstChar { it.uppercase() }}UnitTest"
    val mergedAssets = variant.artifacts.get(SingleArtifact.ASSETS)
    tasks.matching { it.name == unitTestName }.configureEach {
        if (this is Test) {
            jvmArgumentProviders.add(objects.newInstance<MergedAssetsArguments>().apply {
                assetsDir.set(mergedAssets)
            })
        }
    }
}

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val verifyReleaseDeps = tasks.register<VerifyReleaseRuntimeClasspathTask>(
        "verifyNoTestDependenciesInRelease",
    ) {
        description = "Fails if test-only dependencies (androidx.test, junit) leak into releaseRuntimeClasspath."
        group = "verification"
        runtimeModules.set(
            variant.runtimeConfiguration.incoming.resolutionResult.rootComponent.map { root ->
                val modules = linkedSetOf<String>()
                val queue = ArrayDeque(listOf(root))
                val visited = hashSetOf(root)
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    (node.id as? ModuleComponentIdentifier)?.let { id ->
                        modules.add("${id.group}:${id.module}:${id.version}")
                    }
                    for (dep in node.dependencies) {
                        if (dep is ResolvedDependencyResult && visited.add(dep.selected)) {
                            queue.add(dep.selected)
                        }
                    }
                }
                modules.sorted()
            },
        )
    }
    tasks.matching { it.name == "testDebugUnitTest" || it.name == "assembleRelease" }.configureEach {
        dependsOn(verifyReleaseDeps)
    }
}
