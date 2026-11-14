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
    implementation(libs.json) { version { strictly(libs.versions.json.get()) } }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    testImplementation(libs.junit)
}

compose.desktop {
    application { mainClass = "io.grimoire.desktop.MainKt" }
}

tasks.withType<JavaExec>().configureEach {
    providers.gradleProperty("desktopManifest").orNull?.let { args("--extension", rootProject.file(it).absolutePath) }
    providers.gradleProperty("desktopData").orNull?.let { args("--data", rootProject.file(it).absolutePath) }
}

val hostApi = providers.gradleProperty("extensionsApiVersion").orElse(libs.versions.extensionsApi)
tasks.processResources {
    inputs.property("hostApi", hostApi)
    inputs.property("jsonVersion", libs.versions.json)
    val version = hostApi.get()
    val jsonCoordinate = "org.json:json:${libs.versions.json.get()}"
    filesMatching("host.properties") { expand("apiVersion" to version, "jsonCoordinate" to jsonCoordinate) }
}

// Reuse the maintained source contract from the pinned extensions checkout in CI.
// These are fixtures only; production parsers are loaded exclusively from JARs.
val integration = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}
configurations[integration.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integration.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
dependencies { add(integration.implementationConfigurationName, compose.desktop.uiTestJUnit4) }
val fixtureRepo = providers.gradleProperty("desktopFixturesRepo").orNull?.let { rootProject.file(it) }
val fixtureManifest = providers.gradleProperty("desktopTestManifest").orNull?.let { rootProject.file(it) }
fixtureRepo?.let {
    kotlin.sourceSets.getByName("integrationTest").kotlin.srcDir(it.resolve("contracts/royalroad/src/main/kotlin"))
}
val verifyFixtures = tasks.register("verifyDesktopFixtures") {
    val contracts = fixtureRepo?.resolve("contracts/royalroad/src/main/kotlin")
    val selectedManifest = fixtureManifest
    doLast {
        require(contracts?.isDirectory == true && selectedManifest?.isFile == true) {
            "Pass -PdesktopFixturesRepo=TRUSTED_CHECKOUT and -PdesktopTestManifest=LOCAL_MANIFEST (see docs/desktop.md)"
        }
    }
}
tasks.named("compileIntegrationTestKotlin") { dependsOn(verifyFixtures) }
tasks.register<Test>("integrationTest") {
    description = "Run the packaged Royal Road reading/restart/offline journey with shared source fixtures"
    group = "verification"
    testClassesDirs = integration.output.classesDirs
    classpath = integration.runtimeClasspath
    exclude("**/RoyalRoadUiTest*")
    // External local JARs must actually be exercised, even if a manifest path is unchanged.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    fixtureManifest?.let {
        inputs.file(it)
        systemProperty("desktop.test.manifest", it.absolutePath)
    }
}

tasks.register<Test>("uiTest") {
    description = "Exercise the Compose reader controls with the packaged-source fixtures"
    group = "verification"
    testClassesDirs = integration.output.classesDirs
    classpath = integration.runtimeClasspath
    include("**/RoyalRoadUiTest*")
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    val evidence = layout.buildDirectory.dir("ui-evidence").get().asFile
    systemProperty("desktop.ui.evidence", evidence.absolutePath)
    fixtureManifest?.let { systemProperty("desktop.test.manifest", it.absolutePath) }
}

// Explicit live smoke only. Never included in check or normal CI; no login/cookies/browser.
tasks.register<Test>("liveSmoke") {
    description = "Run one public Royal Road UI reading/offline journey against the real site"
    group = "verification"
    testClassesDirs = integration.output.classesDirs
    classpath = integration.runtimeClasspath
    include("**/RoyalRoadUiTest*")
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    systemProperty("desktop.ui.live", "true")
    systemProperty("desktop.ui.evidence", layout.buildDirectory.dir("live-evidence").get().asFile.absolutePath)
    fixtureManifest?.let { systemProperty("desktop.test.manifest", it.absolutePath) }
}
