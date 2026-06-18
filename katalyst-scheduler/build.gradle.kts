plugins {
    id("io.github.darkryh.katalyst.conventions.scheduler")
    // Mutation testing (Phase 6): proves the tests actually *catch* bugs, not just execute code.
    // 1.19.0 is the first release compatible with Gradle 9 (1.15.0 referenced the removed
    // `reporting.baseDir`). Run: `./gradlew :katalyst-scheduler:pitest --no-configuration-cache`.
    id("info.solidsoft.pitest") version "1.19.0"
}

pitest {
    junit5PluginVersion.set("1.2.1")
    // Mutate the cron engine (pure, well-tested logic); widen `targetClasses` over time.
    targetClasses.set(listOf("io.github.darkryh.katalyst.scheduler.cron.*"))
    // IMPORTANT: the cron tests live in a different package than the source (services.cron vs
    // scheduler.cron). Without this, Pitest defaults targetTests to the targetClasses package and
    // silently runs only the few tests in scheduler.cron — undercounting the real kill rate.
    targetTests.set(
        listOf(
            "io.github.darkryh.katalyst.services.cron.*",
            "io.github.darkryh.katalyst.scheduler.cron.*",
        )
    )
    threads.set(2)
    timestampedReports.set(false)
    // Ratchet floor below the measured baseline (cron suite kills ~73% of mutants, 95% line cov,
    // ~1700 tests). 70 leaves headroom for minor run-to-run variance from timeout-sensitive mutants;
    // raise it as the suite strengthens. See TESTING_STRATEGY.md.
    mutationThreshold.set(70)
}

dependencies {
    // Core modules
    implementation(projects.katalystCore)
    implementation(projects.katalystDi)

    // Testing
    testImplementation(libs.kotlinx.coroutines.test)
}

// Per-module coverage floor (ratchet — raise as coverage grows; never lower). See TESTING_STRATEGY.md.
kover {
    reports {
        verify {
            rule {
                minBound(80) // baseline ~87% line coverage
            }
        }
    }
}
