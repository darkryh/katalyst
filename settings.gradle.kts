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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Core infrastructure modules
include(":katalyst-scanner")
include(":katalyst-ktor")

// Dependency Injection and Orchestration
include(":katalyst-di")

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
include(":katalyst-testing-core")
include(":katalyst-testing-ktor")
include(":katalyst-ktor-engine-netty")
include(":katalyst-ktor-engine-jetty")
include(":katalyst-ktor-engine-cio")
