rootProject.name = "katalyst"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

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
