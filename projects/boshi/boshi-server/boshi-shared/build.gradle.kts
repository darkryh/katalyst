plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Katalyst modules
    implementation("io.github.darkryh.katalyst:katalyst-core")
    implementation("io.github.darkryh.katalyst:katalyst-events")
    implementation("io.github.darkryh.katalyst:katalyst-events-bus")
    implementation("io.github.darkryh.katalyst:katalyst-persistence")
    implementation("io.github.darkryh.katalyst:katalyst-migrations")
    implementation("io.github.darkryh.katalyst:katalyst-di")
    implementation("io.github.darkryh.katalyst:katalyst-transactions")
    implementation("io.github.darkryh.katalyst:katalyst-ktor")
    implementation("io.github.darkryh.katalyst:katalyst-config-provider")
    implementation("io.github.darkryh.katalyst:katalyst-config-yaml")

    // Database - Exposed ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    // Database connection pooling
    implementation(libs.hikari)

    // PostgreSQL driver
    implementation(libs.postgresql)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation("io.github.darkryh.katalyst:katalyst-testing-core")
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation("org.testcontainers:testcontainers")
    testImplementation(libs.testcontainers.postgresql)
}
