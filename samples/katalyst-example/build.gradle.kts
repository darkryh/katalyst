plugins {
    // The single Katalyst plugin applies Kotlin JVM, kotlinx.serialization and the application
    // plugin. No version: it is resolved from the composite build (see settings.gradle.kts).
    // A published consumer would write: id("io.github.darkryh.katalyst") version "1.0.0-alpha04"
    id("io.github.darkryh.katalyst") version "1.0.0-alpha04"
}

group = "io.github.darkryh"
version = "0.0.1"

application { mainClass = "io.github.darkryh.katalyst.example.ApplicationKt" }

// Only katalyst-* artifacts — Ktor, Exposed, Koin, serialization, etc. all arrive transitively
// through the starters. Pick the server engine by adding exactly one katalyst-starter-engine-*.
dependencies {
    val katalyst = "1.0.0-alpha04"
    implementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
    implementation("io.github.darkryh.katalyst:katalyst-starter-web")
    implementation("io.github.darkryh.katalyst:katalyst-starter-engine-netty")
    implementation("io.github.darkryh.katalyst:katalyst-starter-persistence")
    implementation("io.github.darkryh.katalyst:katalyst-starter-migrations")
    implementation("io.github.darkryh.katalyst:katalyst-starter-scheduler")
    implementation("io.github.darkryh.katalyst:katalyst-starter-websockets")
    // Zero-config observability in one line: bounded in-process telemetry + the embedded Dispatch
    // TUI inspector. Real terminal -> becomes the default console; no TTY (services, IDE run
    // windows) -> falls back to plain logs. Opt out with -Dkatalyst.telemetry.enabled=false /
    // -Dkatalyst.tui.enabled=false.
    implementation("io.github.darkryh.katalyst:katalyst-starter-observability")
    testImplementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
    testImplementation("io.github.darkryh.katalyst:katalyst-starter-test")
}

tasks.test {
    useJUnitPlatform()
}

// Thin jar with a Class-Path manifest so `java -jar` works standalone (run from build/install/
// katalyst-example/lib, where installDist already copies every dependency jar by plain filename).
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "io.github.darkryh.katalyst.example.ApplicationKt",
            "Class-Path" to provider {
                configurations.runtimeClasspath.get().files.joinToString(" ") { it.name }
            }
        )
    }
}

// IntelliJ/Tooling may request this task as a project-scoped path.
// Keep a local alias so :katalyst-example:prepareKotlinBuildScriptModel resolves.
tasks.register("prepareKotlinBuildScriptModel") {
    dependsOn(":prepareKotlinBuildScriptModel")
}
