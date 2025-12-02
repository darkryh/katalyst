package com.ead.katalyst.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class PersistenceConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(CommonConventionPlugin::class.java)

            dependencies {
                implementation(libs.library("hikari"))
                implementation(libs.library("exposed-core"))
                implementation(libs.library("exposed-dao"))
                implementation(libs.library("exposed-jdbc"))
                implementation(libs.library("koin-core"))

                testImplementation(libs.library("h2"))
                testImplementation(libs.library("kotlin-test-junit5"))
                testImplementation(libs.library("junit-platform-launcher"))
            }
        }
    }
}
