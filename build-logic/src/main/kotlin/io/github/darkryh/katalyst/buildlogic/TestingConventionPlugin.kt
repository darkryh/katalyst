@file:Suppress("unused")

package io.github.darkryh.katalyst.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(CommonConventionPlugin::class.java)
            pluginManager.apply("java-test-fixtures")

            dependencies {
                add("testImplementation", kotlin("test"))
                add("testFixturesImplementation", kotlin("test"))
                testFixturesImplementation(libs.library("kotlinx-coroutines-core"))
                testFixturesImplementation(libs.library("ktor-server-core"))
            }
        }
    }
}
