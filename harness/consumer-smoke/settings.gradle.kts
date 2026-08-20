// Standalone consumer build — NOT part of the main Katalyst build. It validates the actual
// published-consumption path by resolving everything (the BOM, the starters, and the
// `io.github.darkryh.katalyst` Gradle plugin marker) from the local Maven repository that
// `./gradlew publishToMavenLocal` populated.
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "consumer-smoke"
