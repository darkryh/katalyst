rootProject.name = "katalyst"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Core infrastructure modules
include(":katalyst-scanner")
include(":katalyst-ktor")

// Ktor engine abstraction and implementations
include(":katalyst-ktor-engine")
include(":katalyst-ktor-engine-netty")
include(":katalyst-ktor-engine-cio")
include(":katalyst-ktor-engine-jetty")

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

include(":katalyst-example")
include(":katalyst-migrations")

// Messaging abstraction and implementations
include(":katalyst-messaging")
include(":katalyst-messaging-amqp")

// New Event System Modules (4-layer architecture)
// Layer 1: Event domain + Bus (local pub/sub)
include(":katalyst-events")
include(":katalyst-events-bus")

// Layer 2: Transport (serialization + routing) + Client (public API)
include(":katalyst-events-transport")
include(":katalyst-events-client")

include("katalyst-transactions")