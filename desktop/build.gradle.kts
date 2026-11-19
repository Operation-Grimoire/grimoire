import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":reading-core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.okhttp)
    implementation(libs.browser.sessions.jcef)
    implementation(libs.json) { version { strictly(libs.versions.json.get()) } }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:${libs.versions.okhttp.get()}")
}

abstract class DesktopTestClasspathArguments : CommandLineArgumentProvider {
    @get:Classpath
    abstract val runtimeFiles: ConfigurableFileCollection
    override fun asArguments() = listOf("-Ddesktop.test.classpath=${runtimeFiles.asPath}")
}

tasks.test {
    jvmArgumentProviders.add(objects.newInstance<DesktopTestClasspathArguments>().apply {
        runtimeFiles.from(sourceSets.test.get().runtimeClasspath)
    })
}

// JCEF's macOS window code reads AWT internals; harmless on other systems.
val jcefJvmArgs = listOf("sun.awt", "sun.lwawt", "sun.lwawt.macosx", "java.awt.peer")
    .map { "--add-opens=java.desktop/$it=ALL-UNNAMED" }

compose.desktop {
    application {
        mainClass = "io.grimoire.desktop.MainKt"
        jvmArgs += jcefJvmArgs
        providers.gradleProperty("desktopManifest").orNull?.let { args("--extension", rootProject.file(it).absolutePath) }
        providers.gradleProperty("desktopData").orNull?.let { args("--data", rootProject.file(it).absolutePath) }
    }
}

val hostApi = providers.gradleProperty("extensionsApiVersion").orElse(libs.versions.extensionsApi)
tasks.processResources {
    inputs.property("hostApi", hostApi)
    inputs.property("jsonVersion", libs.versions.json)
    val version = hostApi.get()
    val jsonCoordinate = "org.json:json:${libs.versions.json.get()}"
    filesMatching("host.properties") { expand("apiVersion" to version, "jsonCoordinate" to jsonCoordinate) }
}

// This fixture is packaged separately and never added to the host/test runtime classpath.
val readerFixture = sourceSets.create("readerFixture")
dependencies {
    add(readerFixture.implementationConfigurationName, project(":reading-core"))
    // This module's Compose compiler checks every source set, even this non-UI fixture.
    add(readerFixture.compileOnlyConfigurationName, compose.runtime)
}
val readerFixtureJar = tasks.register<Jar>("readerFixtureJar") {
    archiveClassifier.set("reader-fixture")
    from(readerFixture.output)
    manifest.attributes(mapOf(
        "Grimoire-Extension-Format" to "1",
        "Grimoire-Source-Package" to "io.grimoire.extension.fixture.reader",
        "Grimoire-Source-Class" to "io.grimoire.extension.fixture.reader.ReaderFixture",
        "Grimoire-Version-Code" to "1",
        "Grimoire-Version-Name" to "1.0.0",
        "Grimoire-Source-Version-Code" to "1",
        "Grimoire-Core-Api" to "1.5",
        "Grimoire-Host-Libraries" to "",
    ))
}
abstract class ReaderFixtureArguments : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fixtureJar: RegularFileProperty
    override fun asArguments() = listOf("-Ddesktop.readerFixtureJar=${fixtureJar.get().asFile.absolutePath}")
}

val integration = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}
configurations[integration.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integration.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
dependencies { add(integration.implementationConfigurationName, compose.desktop.uiTestJUnit4) }

tasks.register<Test>("integrationTest") {
    description = "Run source-neutral packaged-extension reading/restart/offline journeys"
    group = "verification"
    testClassesDirs = integration.output.classesDirs
    classpath = integration.runtimeClasspath
    exclude("**/ReaderUiTest*", "**/ExtensionInstallUiTest*")
    dependsOn(readerFixtureJar)
    jvmArgumentProviders.add(objects.newInstance<ReaderFixtureArguments>().apply {
        fixtureJar.set(readerFixtureJar.flatMap { it.archiveFile })
    })
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

tasks.register<Test>("uiTest") {
    description = "Exercise Compose reader controls with the local test extension"
    group = "verification"
    testClassesDirs = integration.output.classesDirs
    classpath = integration.runtimeClasspath
    include("**/ReaderUiTest*", "**/ExtensionInstallUiTest*")
    dependsOn(readerFixtureJar)
    jvmArgumentProviders.add(objects.newInstance<ReaderFixtureArguments>().apply {
        fixtureJar.set(readerFixtureJar.flatMap { it.archiveFile })
    })
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    systemProperty("desktop.ui.evidence", layout.buildDirectory.dir("ui-evidence").get().asFile.absolutePath)
}

// Real packaged-extension compatibility is separate from app behavior tests.
val compatibility = sourceSets.create("compatibilityTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}
configurations[compatibility.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[compatibility.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
dependencies { add(compatibility.implementationConfigurationName, compose.desktop.uiTestJUnit4) }
val fixtureRepo = providers.gradleProperty("desktopFixturesRepo").orNull?.let { rootProject.file(it) }
val fixtureManifest = providers.gradleProperty("desktopTestManifest").orNull?.let { rootProject.file(it) }
fixtureRepo?.let {
    kotlin.sourceSets.getByName("compatibilityTest").kotlin.srcDir(it.resolve("contracts/royalroad/src/main/kotlin"))
}
val verifyFixtures = tasks.register("verifyDesktopCompatibilityFixtures") {
    val contracts = fixtureRepo?.resolve("contracts/royalroad/src/main/kotlin")
    val selectedManifest = fixtureManifest
    doLast {
        require(contracts?.isDirectory == true && selectedManifest?.isFile == true) {
            "Pass -PdesktopFixturesRepo=TRUSTED_CHECKOUT and -PdesktopTestManifest=LOCAL_MANIFEST (see docs/desktop.md)"
        }
    }
}
tasks.named("compileCompatibilityTestKotlin") { dependsOn(verifyFixtures) }

tasks.register<Test>("extensionCompatibilityTest") {
    description = "Smoke-test the host with a real packaged extension and its external fixtures"
    group = "verification"
    testClassesDirs = compatibility.output.classesDirs
    classpath = compatibility.runtimeClasspath
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    fixtureManifest?.let {
        inputs.file(it)
        systemProperty("desktop.test.manifest", it.absolutePath)
    }
    systemProperty("desktop.ui.evidence", layout.buildDirectory.dir("compatibility-evidence").get().asFile.absolutePath)
}

// Explicit live smoke only. Never included in check or normal CI; no login/cookies/browser.
tasks.register<Test>("liveSmoke") {
    description = "Run one public Royal Road UI reading/offline journey against the real site"
    group = "verification"
    testClassesDirs = compatibility.output.classesDirs
    classpath = compatibility.runtimeClasspath
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    systemProperty("desktop.ui.live", "true")
    systemProperty("desktop.ui.evidence", layout.buildDirectory.dir("live-evidence").get().asFile.absolutePath)
    fixtureManifest?.let { systemProperty("desktop.test.manifest", it.absolutePath) }
}

// Native embedded-browser check: real Chromium, local HTTPS fixture, one process per phase.
val browserCheck = sourceSets.create("browserTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}
configurations[browserCheck.implementationConfigurationName].extendsFrom(configurations.implementation.get())
dependencies {
    add(browserCheck.implementationConfigurationName, "com.squareup.okhttp3:mockwebserver:${libs.versions.okhttp.get()}")
    add(browserCheck.implementationConfigurationName, "com.squareup.okhttp3:okhttp-tls:${libs.versions.okhttp.get()}")
}
tasks.register<JavaExec>("browserCheck") {
    description = "Sign in, share cookies with HTTP, restart and shut down real embedded Chromium"
    group = "verification"
    classpath = browserCheck.runtimeClasspath
    mainClass.set("io.grimoire.desktop.BrowserHostCheckKt")
    jvmArgs(jcefJvmArgs)
    timeout.set(Duration.ofMinutes(6))
    args(providers.gradleProperty("browserPhase").orElse("session").get(),
        layout.buildDirectory.dir("browser-check").get().asFile.absolutePath)
}
