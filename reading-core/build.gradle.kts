plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

group = "io.grimoire"
version = System.getenv("READING_CORE_RELEASE_TAG")
    ?.trim()
    ?.removePrefix("v")
    ?.takeIf { it.isNotEmpty() }
    ?: "1.0.0-SNAPSHOT"

kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

tasks.jar { archiveBaseName.set("reading-core") }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("io.grimoire:extensions-core:${providers.gradleProperty("extensionsApiVersion").getOrElse(libs.versions.extensionsApi.get())}")
    testImplementation(libs.junit)
}

publishing {
    publications {
        create<MavenPublication>("readingCore") {
            from(components["java"])
            artifactId = "reading-core"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Operation-Grimoire/grimoire")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
