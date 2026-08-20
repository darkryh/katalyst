plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Internal modules
    implementation(projects.katalystEvents)

    // Persistence stack (Exposed)
    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.h2)
    testImplementation(libs.logback)
    // Real-DB failure-injection / resilience tests (Phase 2). Docker-gated; skipped when absent.
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
}

// Per-module coverage floor (ratchet — raise as coverage grows; never lower). See TESTING_STRATEGY.md.
kover {
    reports {
        verify {
            rule {
                minBound(40) // baseline ~43% line coverage; grow toward 60%+ (Phase 1 remainder)
            }
        }
    }
}
