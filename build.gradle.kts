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
    // Declarations annotated with this marker are framework-internal infrastructure that must be
    // `public` for cross-module visibility but are excluded from the committed public API surface.
    nonPublicMarkers += "io.github.darkryh.katalyst.core.annotation.KatalystInternalApi"

    // Exclude non-published / test-helper / sample modules from the public API surface.
    ignoredProjects += listOf(
        "memory-validation",
        // The terminal-UI inspector is a runnable application, not a published library.
        "katalyst-tui",
        "katalyst-testing-core",
        "katalyst-testing-ktor",
        "katalyst-starter-test",
        "katalyst-bom",
        "katalyst-starter-core",
        "katalyst-starter-web",
        "katalyst-starter-persistence",
        "katalyst-starter-migrations",
        "katalyst-starter-scheduler",
        "katalyst-starter-websockets",
        "katalyst-starter-engine-netty",
        "katalyst-starter-engine-jetty",
        "katalyst-starter-engine-cio",
        "katalyst-gradle-plugin",
    )
}

tasks.register("memoryBaseline") {
    group = "verification"
    description = "Runs the canonical full-backend memory validation workload"
    dependsOn(":memory-validation:memoryBaseline")
}

val starterBoundaries = mapOf(
    ":katalyst-starter-core" to setOf("katalyst-migrations", "katalyst-scheduler", "katalyst-websockets"),
    ":katalyst-starter-web" to setOf("katalyst-migrations", "katalyst-scheduler", "katalyst-websockets"),
    ":katalyst-starter-persistence" to setOf("katalyst-migrations", "katalyst-scheduler", "katalyst-websockets"),
    ":katalyst-starter-migrations" to setOf("katalyst-scheduler", "katalyst-websockets"),
    ":katalyst-starter-scheduler" to setOf("katalyst-migrations", "katalyst-websockets"),
    ":katalyst-starter-websockets" to setOf("katalyst-migrations", "katalyst-scheduler"),
    // Engine starters must carry exactly one engine — neither the other two Ktor engine
    // artifacts nor any optional feature module may leak in.
    ":katalyst-starter-engine-netty" to setOf(
        "ktor-server-jetty-jakarta-jvm", "ktor-server-cio-jvm",
        "katalyst-migrations", "katalyst-scheduler", "katalyst-websockets",
    ),
    ":katalyst-starter-engine-jetty" to setOf(
        "ktor-server-netty-jvm", "ktor-server-cio-jvm",
        "katalyst-migrations", "katalyst-scheduler", "katalyst-websockets",
    ),
    ":katalyst-starter-engine-cio" to setOf(
        "ktor-server-netty-jvm", "ktor-server-jetty-jakarta-jvm",
        "katalyst-migrations", "katalyst-scheduler", "katalyst-websockets",
    ),
)

val starterBoundaryChecks = starterBoundaries.map { (projectPath, forbiddenModules) ->
    val starterName = projectPath.removePrefix(":")
    evaluationDependsOn(projectPath)
    val runtimeClasspath = project(projectPath).configurations.named("runtimeClasspath")

    tasks.register("validate${starterName.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }}Boundary") {
        group = "verification"
        description = "Verifies optional feature modules do not leak into $starterName"
        inputs.files(runtimeClasspath)
            .withPropertyName("runtimeClasspath")
            .withNormalizer(ClasspathNormalizer::class.java)

        doLast {
            val leakedModules = forbiddenModules.filter { module ->
                inputs.files.files.any { artifact ->
                    artifact.name == "$module.jar" || artifact.name.startsWith("$module-")
                }
            }
            check(leakedModules.isEmpty()) {
                "$projectPath leaks optional modules: ${leakedModules.sorted().joinToString()}"
            }
        }
    }
}

tasks.register("validateStarterBoundaries") {
    group = "verification"
    description = "Verifies optional feature modules do not leak into unrelated starters"
    dependsOn(starterBoundaryChecks)
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
