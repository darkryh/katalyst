plugins {
    // The single Katalyst plugin applies Kotlin JVM, kotlinx.serialization and the application
    // plugin. No version: it is resolved from the composite build (see settings.gradle.kts).
    // A published consumer would write: id("io.github.darkryh.katalyst") version "1.0.0-alpha02"
    id("io.github.darkryh.katalyst")
}

group = "io.github.darkryh"
version = "0.0.1"

application { mainClass = "io.github.darkryh.katalyst.example.ApplicationKt" }

// Only katalyst-* artifacts — Ktor, Exposed, Koin, serialization, etc. all arrive transitively
// through the starters. Pick the server engine by adding exactly one katalyst-starter-engine-*.
dependencies {
    val katalyst = "1.0.0-alpha02"
    implementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
    implementation("io.github.darkryh.katalyst:katalyst-starter-web")
    implementation("io.github.darkryh.katalyst:katalyst-starter-engine-netty")
    implementation("io.github.darkryh.katalyst:katalyst-starter-persistence")
    implementation("io.github.darkryh.katalyst:katalyst-starter-migrations")
    implementation("io.github.darkryh.katalyst:katalyst-starter-scheduler")
    implementation("io.github.darkryh.katalyst:katalyst-starter-websockets")
    // Zero-config observability: having katalyst-telemetry on the runtime classpath makes the app
    // auto-attach the bounded in-process telemetry layer (loopback /snapshot + /stream) that the
    // katalyst-tui inspector reads. Resolved to the local project via the composite build. Opt out
    // with -Dkatalyst.telemetry.enabled=false. (Not in the BOM, so no version.)
    runtimeOnly("io.github.darkryh.katalyst:katalyst-telemetry")
    // Embedded inspector: with katalyst-tui on the runtime classpath the Dispatch TUI becomes the
    // default console when the app runs in a real terminal (java -jar, installDist binary, ssh);
    // without a TTY (IDE Run window, services) it logs a one-time how-to warning and falls back to
    // normal logs. Opt out with -Dkatalyst.tui.enabled=false.
    runtimeOnly("io.github.darkryh.katalyst:katalyst-tui")
    testImplementation(platform("io.github.darkryh.katalyst:katalyst-bom:$katalyst"))
    testImplementation("io.github.darkryh.katalyst:katalyst-starter-test")
}

tasks.test {
    useJUnitPlatform()
}

// IntelliJ/Tooling may request this task as a project-scoped path.
// Keep a local alias so :katalyst-example:prepareKotlinBuildScriptModel resolves.
tasks.register("prepareKotlinBuildScriptModel") {
    dependsOn(":prepareKotlinBuildScriptModel")
}
