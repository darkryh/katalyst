@file:Suppress("unused")

package io.github.darkryh.katalyst.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class KtorServerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(CommonConventionPlugin::class.java)

            dependencies {
                implementation(libs.library("ktor-server-core"))
                implementation(libs.library("ktor-server-content-negotiation"))
                implementation(libs.library("ktor-serialization-kotlinx-json"))
                implementation(libs.library("ktor-server-status-pages"))
                implementation(libs.library("koin-ktor"))
                implementation(libs.library("koin-core"))

                testImplementation(libs.library("ktor-server-test-host"))
                testImplementation(libs.library("koin-test"))
                testImplementation(libs.library("koin-test-junit5"))
                testImplementation(libs.library("kotlin-test-junit5"))
                testImplementation(libs.library("junit-platform-launcher"))
            }
        }
    }
}
