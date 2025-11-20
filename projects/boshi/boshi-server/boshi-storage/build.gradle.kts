plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Katalyst modules
    implementation("com.ead.katalyst:katalyst-core")
    implementation("com.ead.katalyst:katalyst-di")
    implementation("com.ead.katalyst:katalyst-events")
    implementation("com.ead.katalyst:katalyst-events-bus")
    implementation("com.ead.katalyst:katalyst-transactions")
    implementation("com.ead.katalyst:katalyst-ktor")
    implementation("com.ead.katalyst:katalyst-persistence")

    // Shared module
    implementation(project(":boshi-server:boshi-shared"))

    // Database - Exposed ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    // Database connection pooling
    implementation(libs.hikari)

    // PostgreSQL driver
    implementation(libs.postgresql)

    // Ktor
    implementation(libs.ktor.server.core)

    // Logging
    implementation(libs.logback)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation("com.ead.katalyst:katalyst-testing-core")
    testImplementation("com.ead.katalyst:katalyst-testing-ktor")
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
