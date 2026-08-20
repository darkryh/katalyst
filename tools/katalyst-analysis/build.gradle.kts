plugins {
    id("io.github.darkryh.katalyst.conventions.common")
    // Resolved from the root `apply false` declaration; used only for katalyst-graph.json DTOs.
    id("org.jetbrains.kotlin.plugin.serialization")
}

// katalyst-analysis is the stable *semantic model* layer of Katalyst tooling.
//
// It produces a KatalystApplicationGraph: a static, serialisable description of how a
// Katalyst application is assembled (components, services, repositories, tables, routes,
// middleware, websockets, exception handlers, schedulers, event handlers, initializers,
// migrations, config loaders, the DI dependency edges, and diagnostics).
//
// Design rules (see the module README):
//  - It mirrors the runtime discovery rules instead of reinventing them: discovery names
//    come from katalyst-conventions, and the DI dependency graph + validation are produced
//    by reusing katalyst-di's own DependencyAnalyzer / DependencyValidator (driven with a
//    no-op container so nothing is instantiated). This guarantees analysis and runtime
//    cannot disagree.
//  - It is NOT a Gradle plugin and does NOT depend on the IntelliJ Platform. It is plain
//    JVM code reusable from tests, CLIs, Gradle tasks and the IDE plugin.
dependencies {
    // Single source of truth for discovery names/contracts.
    implementation(projects.katalystConventions)

    // KatalystContainer facade (we provide a no-op static implementation).
    implementation(projects.katalystCore)

    // Reuse the runtime's dependency analysis + validation instead of duplicating it:
    // DependencyAnalyzer, DependencyGraph, DependencyValidator, ValidationError, KatalystMigration.
    implementation(projects.katalystDi)

    // Reflection over discovered classes (no instantiation) + classpath scanning + bytecode DSL detection.
    implementation(kotlin("reflect"))
    implementation(libs.reflections)
    implementation(libs.asm)
    implementation(libs.slf4j.api)

    // katalyst-graph.json (opt-in serialization of the same in-memory model).
    implementation(libs.kotlinx.serialization.json)

    // --- Test fixtures: a self-contained mini Katalyst app exercised by the analyzer. ---
    testImplementation(projects.katalystPersistence)
    testImplementation(projects.katalystEvents)
    testImplementation(projects.katalystKtor)
    testImplementation(projects.katalystScheduler)
    testImplementation(projects.katalystConfigProvider)
    testImplementation(libs.exposed.core)
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.logback)
}

// Per-module coverage floor (ratchet — raise as coverage grows; never lower).
kover {
    reports {
        verify {
            rule {
                minBound(40)
            }
        }
    }
}
