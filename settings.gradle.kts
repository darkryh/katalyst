plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "katalyst"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        // The Dispatch terminal-UI framework (io.github.darkryh:dispatch-*) is consumed by
        // katalyst-tui from the local Maven repo where it is published.
        mavenLocal()
        // Dispatch runs on the Compose runtime, which pulls androidx.compose.*/androidx.savedstate.*
        // transitive artifacts hosted on Google's Maven repository.
        google()
    }
}

includeBuild("build-logic")

// The samples are NOT part of this build. They are standalone consumer projects (under
// samples/) that depend on the PUBLISHED katalyst-* artifacts + the io.github.darkryh.katalyst
// plugin from a Maven repo — exactly like a real external app, and the same approach JetBrains
// uses for Exposed's samples. Validate them against local changes with `samples/validate-samples.sh`
// (publishToMavenLocal + build). Keeping them out of this build is what makes a single IDE sync of
// the library clean (no nested composite, no "Missing ExternalProject").

// The IntelliJ plugin is a separate composite build: it applies the (heavy,
// version-sensitive) IntelliJ Platform Gradle plugin and is not published as a Maven
// library, so it must not be pulled into the main library build by default. Opt in with
// -PincludeIntellijPluginComposite=true (e.g. when developing the plugin).
if (providers.gradleProperty("includeIntellijPluginComposite").orNull == "true") {
    includeBuild("tools/katalyst-intellij-plugin")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ---------------------------------------------------------------------------------------------
// Module layout
//
// Gradle project NAMES and their DIRECTORIES are independent, and this build relies on that:
// every project keeps its flat name (`:katalyst-core`), while its sources live in a directory
// that groups it with its peers. Because `BaseConventionPlugin` derives the published
// coordinate from the project NAME (`artifactId = target.name`), moving a module between
// directories changes nothing a consumer can observe — the artifactId, the `projects.*`
// accessor, the committed `api/*.api` dump and every `:module:task` path all stay put.
//
// So: to regroup a module, change ONLY its directory below. Never rename the project.
// ---------------------------------------------------------------------------------------------

/** Declares [modules] as flat root-level projects whose sources live under `dir/`. */
fun includeIn(dir: String, vararg modules: String) {
    modules.forEach { name ->
        include(":$name")
        project(":$name").projectDir = file("$dir/$name")
    }
}

// ── Framework core — the modules an application boots on (kept at the repository root) ────────
// Discovery contracts, the bootstrap/DI engine, classpath scanning, the Koin adapter, the
// zero-dependency convention contract shared with tooling, events, and scheduling.
include(":katalyst-core")
include(":katalyst-di")
include(":katalyst-scanner")
include(":katalyst-koin-bean")
include(":katalyst-conventions")
include(":katalyst-events")
include(":katalyst-events-bus")
include(":katalyst-scheduler")

// ── HTTP: the Ktor integration, the three swappable engines, and WebSockets ───────────────────
includeIn(
    "http",
    "katalyst-ktor",
    "katalyst-ktor-engine-netty",
    "katalyst-ktor-engine-jetty",
    "katalyst-ktor-engine-cio",
    "katalyst-websockets",
)

// ── Data: Exposed/HikariCP persistence, transaction management, schema migrations ─────────────
includeIn(
    "data",
    "katalyst-persistence",
    "katalyst-transactions",
    "katalyst-migrations",
)

// ── Configuration: the loader SPI, typed binding, and the YAML loader ─────────────────────────
includeIn(
    "config",
    "katalyst-config-spi",
    "katalyst-config-provider",
    "katalyst-config-yaml",
)

// ── Observability: bounded in-process telemetry capture plus the terminal-UI inspector that
// reads it. -model is the zero-dependency wire contract shared by the backend feature and the TUI.
includeIn(
    "observability",
    "katalyst-telemetry-model",
    "katalyst-telemetry",
    "katalyst-tui",
)

// ── Test-time libraries consumers depend on from `testImplementation` ─────────────────────────
includeIn(
    "testing",
    "katalyst-testing-core",
    "katalyst-testing-ktor",
)

// ── Starters: dependency bundles that give an application a capability in one coordinate ──────
includeIn(
    "starter",
    "katalyst-starter-core",
    "katalyst-starter-web",
    "katalyst-starter-persistence",
    "katalyst-starter-migrations",
    "katalyst-starter-scheduler",
    "katalyst-starter-websockets",
    "katalyst-starter-test",
    "katalyst-starter-observability",
    "katalyst-starter-engine-netty",
    "katalyst-starter-engine-jetty",
    "katalyst-starter-engine-cio",
)

// ── Packaging: what a consumer build applies, never on an application's runtime classpath.
// The BOM aligns every katalyst-* version; the Gradle plugin (id "io.github.darkryh.katalyst")
// applies kotlin.jvm + serialization + application so consumer builds wire no third-party plugins.
includeIn(
    "platform",
    "katalyst-bom",
    "katalyst-gradle-plugin",
)

// ── Tooling: the semantic analysis layer consumed by Gradle tasks, CLIs, tests and the IDE
// plugin. (The IntelliJ plugin itself is the separate composite build included above.)
includeIn(
    "tools",
    "katalyst-analysis",
)

// ── Harnesses: not part of the `check` gate. They stand up a REAL running application and
// verify Katalyst from the outside. `memory-profile` drives a full backend under load and
// samples RSS/heap/threads (`./gradlew memoryBaseline`); its sibling `harness/consumer-smoke`
// is a standalone build and is deliberately NOT a project of this one.
//
// Note it is intentionally NOT named `katalyst-*`: `platform/katalyst-bom` builds its constraint
// list by that name prefix, so an unprefixed name keeps a never-published module out of the BOM
// by construction.
includeIn(
    "harness",
    "memory-profile",
)
