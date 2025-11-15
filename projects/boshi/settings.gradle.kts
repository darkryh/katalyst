rootProject.name = "boshi"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    includeBuild("../../../katalyst")
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

include("boshi-server")