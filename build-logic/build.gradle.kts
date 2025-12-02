import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

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
            id = "com.ead.katalyst.conventions.base"
            implementationClass = "com.ead.katalyst.buildlogic.BaseConventionPlugin"
        }
        register("katalystCommon") {
            id = "com.ead.katalyst.conventions.common"
            implementationClass = "com.ead.katalyst.buildlogic.CommonConventionPlugin"
        }
        register("katalystKtorServer") {
            id = "com.ead.katalyst.conventions.ktor-server"
            implementationClass = "com.ead.katalyst.buildlogic.KtorServerConventionPlugin"
        }
        register("katalystPersistence") {
            id = "com.ead.katalyst.conventions.persistence"
            implementationClass = "com.ead.katalyst.buildlogic.PersistenceConventionPlugin"
        }
        register("katalystScheduler") {
            id = "com.ead.katalyst.conventions.scheduler"
            implementationClass = "com.ead.katalyst.buildlogic.SchedulerConventionPlugin"
        }
        register("katalystTesting") {
            id = "com.ead.katalyst.conventions.testing"
            implementationClass = "com.ead.katalyst.buildlogic.TestingConventionPlugin"
        }
    }
}
