// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Always re-resolve SNAPSHOT dependencies. Without this, Gradle caches
// changing modules for 24h and CI runs that restore ~/.gradle/caches
// happily link against the previously-published SNAPSHOT even after a
// fresh API publish.
allprojects {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}