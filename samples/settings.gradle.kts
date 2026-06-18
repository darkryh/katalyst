rootProject.name = "samples"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    if (gradle.parent == null) {
        includeBuild("../../katalyst")
    }
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}


include("katalyst-example")
