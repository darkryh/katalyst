package io.github.darkryh.katalyst.buildlogic

import com.vanniktech.maven.publish.MavenPublishBaseExtension
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
            group = findProperty("katalystGroup") as? String ?: "io.github.darkryh.katalyst"
            version = findProperty("katalystVersion") as? String ?: "0.0.1"

            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlinx.kover")
            pluginManager.apply("com.vanniktech.maven.publish")

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

            extensions.configure<MavenPublishBaseExtension> {
                publishToMavenCentral(automaticRelease = true)
                signAllPublications()

                coordinates(
                    groupId = group.toString(),
                    artifactId = name,
                    version = version.toString()
                )

                pom {
                    name.set("Katalyst - $name")
                    description.set("Katalyst module: $name")
                    url.set("https://github.com/darkryh/katalyst")

                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    scm {
                        url.set("https://github.com/darkryh/katalyst")
                        connection.set("scm:git:git://github.com/darkryh/katalyst.git")
                        developerConnection.set("scm:git:ssh://github.com/darkryh/katalyst.git")
                    }

                    developers {
                        developer {
                            id.set("Darkryh")
                            name.set("Xavier Alexander Torres Calderón")
                            email.set("alex_torres-xc@hotmail.com")
                        }
                    }
                }
            }
        }
    }
}
