pluginManagement {
    repositories {
        // The Katalyst Gradle plugin (io.github.darkryh.katalyst) is resolved the way a real
        // consumer resolves it: from a Maven repository. mavenLocal() picks up unreleased builds
        // published via `./gradlew publishToMavenLocal`; mavenCentral() serves released versions.
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "samples"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        // Consumer-style resolution: katalyst-* artifacts come from mavenLocal (unreleased) or
        // mavenCentral (released) — NOT from a composite includeBuild. This makes the sample a real
        // consumer of the published library (the same approach JetBrains uses for Exposed's samples)
        // and fully decouples it from the library's own Gradle build.
        // google() serves the androidx.compose.* transitives pulled in by katalyst-tui/dispatch.
        mavenLocal()
        mavenCentral()
        google()
    }
}

include("katalyst-example")
