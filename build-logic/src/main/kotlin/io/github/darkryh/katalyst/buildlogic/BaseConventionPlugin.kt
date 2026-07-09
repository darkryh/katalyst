package io.github.darkryh.katalyst.buildlogic

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

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

            // Keep the coverage gate fast and deterministic: the benchmark/load suites must not be
            // instrumented or pulled in by `koverVerify` (otherwise a flaky benchmark could fail the
            // coverage gate, defeating the point of tagging them out of the default `test` task).
            extensions.configure<KoverProjectExtension> {
                currentProject {
                    instrumentation {
                        disabledForTestTasks.addAll("benchmarkTest", "loadTest")
                    }
                }
            }

            // Tag-based test categories. Slow/non-deterministic suites are tagged so the
            // default `test` task (the CI gate) stays fast and deterministic:
            //   @Tag("benchmark") -> timing/throughput microbenchmarks (flaky on shared CI)
            //   @Tag("load")      -> stress / load / soak suites (long-running, resource heavy)
            // Run them explicitly with `./gradlew benchmarkTest` or `./gradlew loadTest`.
            val excludedTestTags = arrayOf("benchmark", "load")
            val dedicatedTagTasks = setOf("benchmarkTest", "loadTest")

            tasks.withType<Test>().configureEach {
                testLogging {
                    events("passed", "skipped", "failed")
                }
                // The dedicated benchmarkTest/loadTest tasks include those tags explicitly, so
                // they must NOT also inherit the global exclusion (exclude would win otherwise).
                if (name !in dedicatedTagTasks) {
                    useJUnitPlatform {
                        excludeTags(*excludedTestTags)
                    }
                }
            }

            tasks.register<Test>("benchmarkTest") {
                group = "verification"
                description = "Runs @Tag(\"benchmark\") microbenchmarks (excluded from the default test gate)."
                testClassesDirs = files(tasks.named("test").map { (it as Test).testClassesDirs })
                classpath = files(tasks.named("test").map { (it as Test).classpath })
                useJUnitPlatform { includeTags("benchmark") }
            }

            tasks.register<Test>("loadTest") {
                group = "verification"
                description = "Runs @Tag(\"load\") stress/load/soak suites (excluded from the default test gate)."
                testClassesDirs = files(tasks.named("test").map { (it as Test).testClassesDirs })
                classpath = files(tasks.named("test").map { (it as Test).classpath })
                useJUnitPlatform { includeTags("load") }
            }

            val moduleName = target.name

            extensions.configure<MavenPublishBaseExtension> {
                publishToMavenCentral(automaticRelease = true)
                signAllPublications()

                coordinates(
                    groupId = group.toString(),
                    artifactId = moduleName,
                    version = version.toString()
                )

                pom {
                    name.set("Katalyst - $moduleName")
                    description.set("Katalyst module: $moduleName")
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
