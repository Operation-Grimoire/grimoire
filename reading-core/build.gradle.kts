plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    api("io.grimoire:extensions-core:${providers.gradleProperty("extensionsApiVersion").getOrElse(libs.versions.extensionsApi.get())}")
    testImplementation(libs.junit)
}
