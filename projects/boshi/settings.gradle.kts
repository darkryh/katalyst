rootProject.name = "boshi"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    includeBuild("../../../katalyst")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":boshi-client:composeApp")

include(":boshi-server:boshi-app")
include(":boshi-server:boshi-shared")
include(":boshi-server:boshi-smtp")
include(":boshi-server:boshi-auth")
include(":boshi-server:boshi-storage")
include(":boshi-server:boshi-retention")
