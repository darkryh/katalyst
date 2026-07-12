plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "katalyst"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        // The Dispatch terminal-UI framework (io.github.darkryh:dispatch-*) is consumed by
        // katalyst-tui from the local Maven repo where it is published.
        mavenLocal()
        // Dispatch runs on the Compose runtime, which pulls androidx.compose.*/androidx.savedstate.*
        // transitive artifacts hosted on Google's Maven repository.
        google()
    }
}

includeBuild("build-logic")

// The samples are NOT part of this build. They are standalone consumer projects (under
// samples/) that depend on the PUBLISHED katalyst-* artifacts + the io.github.darkryh.katalyst
// plugin from a Maven repo — exactly like a real external app, and the same approach JetBrains
// uses for Exposed's samples. Validate them against local changes with `samples/validate-samples.sh`
// (publishToMavenLocal + build). Keeping them out of this build is what makes a single IDE sync of
// the library clean (no nested composite, no "Missing ExternalProject").

// The IntelliJ plugin is a separate composite build: it applies the (heavy,
// version-sensitive) IntelliJ Platform Gradle plugin and is not published as a Maven
// library, so it must not be pulled into the main library build by default. Opt in with
// -PincludeIntellijPluginComposite=true (e.g. when developing the plugin).
if (providers.gradleProperty("includeIntellijPluginComposite").orNull == "true") {
    includeBuild("katalyst-intellij-plugin")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Shared conventions / contracts (zero-dependency single source of truth)
include(":katalyst-conventions")

// Core infrastructure modules
include(":katalyst-scanner")
include(":katalyst-ktor")

// Dependency Injection and Orchestration
include(":katalyst-di")
include(":katalyst-koin-bean")

// Domain modules
include(":katalyst-core")
include(":katalyst-persistence")
include(":katalyst-scheduler")
include(":katalyst-websockets")

// Configuration management modules
include(":katalyst-config-provider")
include(":katalyst-config-yaml")
include(":katalyst-config-spi")

include(":katalyst-migrations")

// Event domain + local bus
include(":katalyst-events")
include(":katalyst-events-bus")

include(":katalyst-transactions")

// Semantic analysis layer consumed by Gradle tooling, CLIs, tests and the IDE plugin
include(":katalyst-analysis")

// Observability: bounded in-process telemetry capture + the terminal-UI inspector that reads it.
// -model is the zero-dependency wire contract shared by the backend feature and the TUI.
include(":katalyst-telemetry-model")
include(":katalyst-telemetry")
include(":katalyst-tui")

include(":katalyst-testing-core")
include(":katalyst-testing-ktor")
include(":katalyst-bom")
include(":katalyst-starter-core")
include(":katalyst-starter-web")
include(":katalyst-starter-persistence")
include(":katalyst-starter-migrations")
include(":katalyst-starter-scheduler")
include(":katalyst-starter-websockets")
include(":katalyst-starter-test")
include(":katalyst-starter-observability")
include(":katalyst-starter-engine-netty")
include(":katalyst-starter-engine-jetty")
include(":katalyst-starter-engine-cio")

listOf(
    "katalyst-starter-core",
    "katalyst-starter-web",
    "katalyst-starter-persistence",
    "katalyst-starter-migrations",
    "katalyst-starter-scheduler",
    "katalyst-starter-websockets",
    "katalyst-starter-test",
    "katalyst-starter-observability",
    "katalyst-starter-engine-netty",
    "katalyst-starter-engine-jetty",
    "katalyst-starter-engine-cio",
).forEach { starter ->
    project(":$starter").projectDir = file("starter/$starter")
}

include(":katalyst-ktor-engine-netty")
include(":katalyst-ktor-engine-jetty")
include(":katalyst-ktor-engine-cio")
include(":memory-validation")

// Consumer-facing Gradle plugin (id "io.github.darkryh.katalyst"). Applies kotlin.jvm +
// serialization + application so consumer builds never wire third-party plugins themselves.
include(":katalyst-gradle-plugin")
