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
        gitOutput("git", "describe", "--tags", "--abbrev=0")
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
val extensionsApiVersion = "0.8.0"

// OAuth App client ID — public, ships in the APK. Read from gradle.properties
// (or env GITHUB_OAUTH_CLIENT_ID) so a fork can substitute its own without
// patching code.
val githubOAuthClientId: String =
    (project.findProperty("GITHUB_OAUTH_CLIENT_ID") as? String)
        ?: System.getenv("GITHUB_OAUTH_CLIENT_ID")
        ?: ""

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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("io.grimoire:extensions-api:$extensionsApiVersion")
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}