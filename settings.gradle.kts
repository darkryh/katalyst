plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "katalyst"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

includeBuild("build-logic")
if (providers.gradleProperty("includeSamplesComposite").orNull == "true") {
    includeBuild("samples")
}
if (providers.gradleProperty("includeBoshiComposite").orNull == "true") {
    includeBuild("projects/boshi")
}

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

include(":katalyst-testing-core")
include(":katalyst-testing-ktor")
include(":katalyst-ktor-engine-netty")
include(":katalyst-ktor-engine-jetty")
include(":katalyst-ktor-engine-cio")
include(":memory-validation")
