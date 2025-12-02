package com.ead.katalyst.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class BaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            group = findProperty("katalystGroup") as? String ?: "com.ead.katalyst"
            version = findProperty("katalystVersion") as? String ?: "0.0.1"

            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<JavaPluginExtension> {
                toolchain.languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
            }

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(21)
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                testLogging {
                    events("passed", "skipped", "failed")
                }
            }
        }
    }
}
