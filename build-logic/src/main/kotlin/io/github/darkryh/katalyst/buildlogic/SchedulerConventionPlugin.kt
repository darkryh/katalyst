@file:Suppress("unused")

package io.github.darkryh.katalyst.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class SchedulerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(CommonConventionPlugin::class.java)

            dependencies {
                implementation(libs.library("koin-core"))
                implementation(libs.library("asm"))
                implementation(libs.library("slf4j-api"))
                implementation(libs.library("kotlin-reflect"))
                implementation(libs.library("kotlinx-coroutines-core"))
            }
        }
    }
}
