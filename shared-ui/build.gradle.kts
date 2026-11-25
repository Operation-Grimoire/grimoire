plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Compose UI shared by the Android app and Desktop. Platform state holders stay in each app.
kotlin {
    androidLibrary {
        namespace = "io.grimoire.ui"
        compileSdk = 36
        minSdk = 26
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
    jvm("desktop") {
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
        val desktopMain by getting {
            dependencies { implementation("org.jsoup:jsoup:1.17.2") }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.junit)
            }
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "io.grimoire.ui.resources"
    generateResClass = always
}
