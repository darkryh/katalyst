// Standalone Gradle build for the Katalyst web initializr (Kotlin/Wasm + Compose Multiplatform).
//
// This is deliberately NOT included in the root `settings.gradle.kts`: the initializr targets the
// browser via Compose Multiplatform and pulls in a different plugin/toolchain set, so keeping it as
// its own build means a plain `./gradlew build` on the library never has to resolve any of it.
// Build it explicitly with:  ./gradlew -p initializr wasmJsBrowserDistribution
rootProject.name = "katalyst-initializr"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}
