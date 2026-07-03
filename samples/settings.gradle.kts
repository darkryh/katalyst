rootProject.name = "samples"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // A regular composite includeBuild contributes BOTH dependency substitutions (the katalyst-*
    // artifacts) AND plugins (the `io.github.darkryh.katalyst` plugin), so the sample needs neither
    // explicit versions nor a pluginManagement entry. Guarded so it is only included when this is
    // the root build — when the main repo includes `samples` as a nested composite, the parent
    // already provides it.
    if (gradle.parent == null) {
        includeBuild("../../katalyst")
    }
    @Suppress("UnstableApiUsage")
    repositories {
        // The embedded TUI (katalyst-tui) pulls io.github.darkryh:dispatch-* from the local Maven
        // repo where it is published, and dispatch's Compose runtime needs androidx artifacts from
        // google(). The consuming build resolves a substituted project's externals, so the sample
        // needs these repos even though the main build already declares them.
        mavenLocal()
        mavenCentral()
        google()
    }
}


include("katalyst-example")
