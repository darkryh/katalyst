plugins {
    kotlin("jvm") version "2.4.0" apply false
    id("io.ktor.plugin") version "3.5.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    // Loaded here with `apply false` so the vanniktech plugin's classes (and its shared
    // MavenCentralBuildService) resolve in the root classloader scope. The base convention plugin
    // applies it per-module; without this, each sibling module loads the build service in its own
    // scope, causing "Cannot set the value of task property 'buildService'" on Gradle 9.x.
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
    // Public/binary API tracking for the published library (Phase 6). `apiDump` snapshots the API
    // into committed `<module>/api/*.api` files; CI runs `apiCheck` to fail any unintended change.
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.3"
}

apiValidation {
    // Exclude non-published / test-helper / sample modules from the public API surface.
    ignoredProjects += listOf(
        "memory-validation",
        "katalyst-testing-core",
        "katalyst-testing-ktor",
    )
}

tasks.register("memoryBaseline") {
    group = "verification"
    description = "Runs the canonical full-backend memory validation workload"
    dependsOn(":memory-validation:memoryBaseline")
}

// ---------------------------------------------------------------------------
// Aggregated code coverage (Kover)
//
// The root project aggregates coverage across every published library module so
// `./gradlew koverXmlReport` / `koverHtmlReport` produce a single repo-wide report and
// `./gradlew koverVerify` enforces a minimum floor in CI.
//
// The floor is a RATCHET: raise `coverageFloorPercent` whenever coverage climbs so it
// can never regress. It starts intentionally low to reflect today's baseline — see
// TESTING_STRATEGY.md (Phase 0) for the plan to grow per-module floors on the critical
// modules (di, transactions, persistence, scheduler).
// ---------------------------------------------------------------------------
val coverageFloorPercent = 50

dependencies {
    // Aggregate coverage from the core library modules (test-only/helper modules excluded).
    kover(project(":katalyst-core"))
    kover(project(":katalyst-di"))
    kover(project(":katalyst-koin-bean"))
    kover(project(":katalyst-scanner"))
    kover(project(":katalyst-persistence"))
    kover(project(":katalyst-transactions"))
    kover(project(":katalyst-migrations"))
    kover(project(":katalyst-events"))
    kover(project(":katalyst-events-bus"))
    kover(project(":katalyst-scheduler"))
    kover(project(":katalyst-websockets"))
    kover(project(":katalyst-ktor"))
    kover(project(":katalyst-config-provider"))
    kover(project(":katalyst-config-yaml"))
    kover(project(":katalyst-config-spi"))
}

kover {
    reports {
        total {
            verify {
                rule {
                    minBound(coverageFloorPercent)
                }
            }
        }
    }
}
