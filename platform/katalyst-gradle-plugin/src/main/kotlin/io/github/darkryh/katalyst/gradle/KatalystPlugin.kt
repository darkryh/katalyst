package io.github.darkryh.katalyst.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * The Katalyst convention plugin (`id("io.github.darkryh.katalyst")`).
 *
 * It applies the build plugins a Katalyst application always needs so the consumer's build script
 * stays free of third-party plugins:
 *  - `org.jetbrains.kotlin.jvm` — the project is Kotlin/JVM.
 *  - `org.jetbrains.kotlin.plugin.serialization` — `@Serializable` DTOs used with Ktor content
 *    negotiation (a compiler plugin, which can only be delivered through a Gradle plugin).
 *  - `application` — the `run`/distribution tasks for the app's `main`.
 *
 * It deliberately does NOT add dependencies: the consumer declares the Katalyst BOM plus the
 * katalyst-* starters (which transitively provide Ktor, Exposed, Koin, etc.), so Ktor and Exposed
 * never appear in a consumer build — neither as a plugin nor as an explicit library.
 */
class KatalystPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target.pluginManager) {
            apply("org.jetbrains.kotlin.jvm")
            apply("org.jetbrains.kotlin.plugin.serialization")
            apply("application")
        }

        // Katalyst is built and runs on Java 21; pin the consumer's Kotlin toolchain to match the
        // framework bytecode it links against.
        target.extensions.getByType(KotlinJvmProjectExtension::class.java).jvmToolchain(21)
    }
}
