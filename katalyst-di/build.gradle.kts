plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Katalyst modules orchestrated by DI
    implementation(projects.katalystConventions)
    implementation(projects.katalystCore)
    implementation(projects.katalystTransactions)
    implementation(projects.katalystPersistence)
    implementation(projects.katalystScanner)
    implementation(projects.katalystKtor)
    implementation(projects.katalystConfigProvider)
    implementation(projects.katalystConfigSpi)

    // Event system modules
    implementation(projects.katalystEvents)
    implementation(projects.katalystEventsBus)

    // Persistence discovery support
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.migration.jdbc)

    // Ktor integration
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)

    // Language/runtime utilities
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    // Diagnostics & reflection aids
    implementation(libs.jansi)
    implementation(libs.reflections)
    implementation(libs.asm)

    // Testing
    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.dao)
    testImplementation(libs.exposed.jdbc)
    testImplementation(libs.h2)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(testFixtures(projects.katalystTestingCore))
    testRuntimeOnly(projects.katalystKoinBean)
    testImplementation(libs.logback)
}

// Per-module coverage floor (ratchet — raise as coverage grows; never lower). See TESTING_STRATEGY.md.
kover {
    reports {
        verify {
            rule {
                minBound(50) // baseline ~55% line coverage
            }
        }
    }
}
