package com.ead.katalyst.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

class CommonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(BaseConventionPlugin::class.java)

            dependencies {
                implementation(libs.library("kotlin-stdlib"))
                implementation(libs.library("kotlin-reflect"))
                implementation(libs.library("kotlinx-coroutines-core"))
                implementation(libs.library("logback"))

                add("testImplementation", kotlin("test"))
                testImplementation(libs.library("kotlin-test-junit5"))
                testImplementation(libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
