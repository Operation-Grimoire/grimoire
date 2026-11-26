plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Shared Compose UI library (theme, icons, reader and novel components) consumed by Android.
kotlin {
    androidLibrary {
        namespace = "io.grimoire.ui"
        compileSdk = 36
        minSdk = 26
        // Package Compose Multiplatform composeResources into Android assets so
        // DefaultAndroidResourceReader opens them via AssetManager instead of
        // falling back to InstrumentationRegistry.
        androidResources.enable = true
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.ui)
            api(compose.foundation)
            api(compose.material3)
            implementation(compose.components.resources)
            api(project(":reading-core"))
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "io.grimoire.ui.resources"
    generateResClass = always
}
