import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test

plugins {
    // The ONLY plugin a Katalyst consumer applies. It pulls in Kotlin JVM, kotlinx.serialization
    // and the application plugin. Resolving it here proves the published plugin marker works.
    id("io.github.darkryh.katalyst") version "1.0.0-alpha04"
}

group = "com.example"
version = "0.0.1"

application { mainClass = "com.example.smoke.SmokeAppKt" }

// Pick the engine with -PkatalystEngine=netty|jetty|cio (default netty). Exactly ONE engine
// starter and its matching engine source directory are wired in, so swapping the engine is a
// single-coordinate change and the other two engines never reach the classpath.
val katalystEngine = (findProperty("katalystEngine") as String? ?: "netty").lowercase()
require(katalystEngine in setOf("netty", "jetty", "cio")) { "Unknown engine: $katalystEngine" }

// Add the selected engine's source dir without needing the Kotlin Gradle plugin types on the
// buildscript classpath (the consumer never declares them) — access the Kotlin source set generically.
sourceSets.named("main").configure {
    val kotlinSources = (this as ExtensionAware).extensions.getByName("kotlin") as SourceDirectorySet
    kotlinSources.srcDir("src/engine-$katalystEngine/kotlin")
}

// CRITICAL: not a single Ktor or Exposed coordinate appears here. Both arrive transitively through
// the katalyst-* starters — that is the property this harness exists to prove.
dependencies {
    val katalyst = "1.0.0-alpha04"
    implementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
    implementation("io.github.darkryh.katalyst:katalyst-starter-web")
    implementation("io.github.darkryh.katalyst:katalyst-starter-persistence")
    implementation("io.github.darkryh.katalyst:katalyst-starter-engine-$katalystEngine")

    testImplementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
    testImplementation("io.github.darkryh.katalyst:katalyst-starter-test")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

// Per-engine runtime proof: the selected engine's Ktor jar is on the runtime classpath, the other
// two are absent, and Ktor + Exposed are present transitively (though never declared).
val engineJars = mapOf(
    "netty" to "ktor-server-netty-jvm",
    "jetty" to "ktor-server-jetty-jakarta-jvm",
    "cio" to "ktor-server-cio-jvm",
)
val verifyEngineClasspath = tasks.register("verifyEngineClasspath") {
    val runtimeClasspath = configurations.named("runtimeClasspath")
    inputs.files(runtimeClasspath)
    val selected = katalystEngine
    doLast {
        val names = inputs.files.files.map { it.name }
        fun present(prefix: String) = names.any { it.startsWith("$prefix-") }

        check(present(engineJars.getValue(selected))) {
            "selected engine jar ${engineJars.getValue(selected)} missing from runtime classpath"
        }
        (engineJars - selected).forEach { (other, jar) ->
            check(!present(jar)) { "engine '$other' leaked into the '$selected' build ($jar present)" }
        }
        check(present("ktor-server-core-jvm")) { "ktor-server-core absent — starter transitive deps broken" }
        check(present("exposed-core")) { "exposed-core absent — persistence starter transitive deps broken" }

        logger.lifecycle("verifyEngineClasspath OK for engine='$selected' — Ktor+Exposed transitive, no foreign engine, none declared.")
    }
}
tasks.named("check") { dependsOn(verifyEngineClasspath) }
