package io.github.darkryh.katalyst.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

class CommonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(BaseConventionPlugin::class.java)

            dependencies {
                add("testImplementation", kotlin("test"))
                testImplementation(libs.library("kotlin-test-junit5"))
                testImplementation(libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
