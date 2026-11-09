pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Operation-Grimoire/grimoire-extensions-api")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
        maven {
            name = "BrowserSessionsPackages"
            url = uri("https://maven.pkg.github.com/Operation-Grimoire/browser-sessions")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
        google()
        mavenCentral()
        maven { url = uri("https://www.jitpack.io") } // Aptabase analytics SDK
    }
}

rootProject.name = "Grimoire"
include(":app")

// Opt-in cross-repository development; released dependency pins remain the default.
providers.gradleProperty("grimoireApiDir").orNull?.let { apiDirectory ->
    includeBuild(apiDirectory) {
        dependencySubstitution {
            substitute(module("io.grimoire:extensions-api")).using(project(":api"))
            substitute(module("io.grimoire:extensions-core")).using(project(":core"))
        }
    }
}
