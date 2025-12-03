import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("katalystBase") {
            id = "io.github.darkryh.katalyst.conventions.base"
            implementationClass = "io.github.darkryh.katalyst.buildlogic.BaseConventionPlugin"
        }
        register("katalystCommon") {
            id = "io.github.darkryh.katalyst.conventions.common"
            implementationClass = "io.github.darkryh.katalyst.buildlogic.CommonConventionPlugin"
        }
        register("katalystKtorServer") {
            id = "io.github.darkryh.katalyst.conventions.ktor-server"
            implementationClass = "io.github.darkryh.katalyst.buildlogic.KtorServerConventionPlugin"
        }
        register("katalystPersistence") {
            id = "io.github.darkryh.katalyst.conventions.persistence"
            implementationClass = "io.github.darkryh.katalyst.buildlogic.PersistenceConventionPlugin"
        }
        register("katalystScheduler") {
            id = "io.github.darkryh.katalyst.conventions.scheduler"
            implementationClass = "io.github.darkryh.katalyst.buildlogic.SchedulerConventionPlugin"
        }
        register("katalystTesting") {
            id = "io.github.darkryh.katalyst.conventions.testing"
            implementationClass = "io.github.darkryh.katalyst.buildlogic.TestingConventionPlugin"
        }
    }
}
