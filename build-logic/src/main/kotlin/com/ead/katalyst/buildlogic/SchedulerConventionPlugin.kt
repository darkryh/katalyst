@file:Suppress("unused")

package com.ead.katalyst.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class SchedulerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(CommonConventionPlugin::class.java)

            dependencies {
                implementation(libs.library("koin-core"))
                implementation(libs.library("koin-core-jvm"))
                implementation(libs.library("asm"))
            }
        }
    }
}
