plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Katalyst modules
    implementation("io.github.darkryh.katalyst:katalyst-core")
    implementation("io.github.darkryh.katalyst:katalyst-di")
    implementation("io.github.darkryh.katalyst:katalyst-events")
    implementation("io.github.darkryh.katalyst:katalyst-events-bus")
    implementation("io.github.darkryh.katalyst:katalyst-transactions")
    implementation("io.github.darkryh.katalyst:katalyst-ktor")
    implementation("io.github.darkryh.katalyst:katalyst-config-provider")
    implementation("io.github.darkryh.katalyst:katalyst-config-yaml")

    // Shared modules
    implementation(project(":boshi-server:boshi-shared"))

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)

    // Logging
    implementation(libs.logback)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation("io.github.darkryh.katalyst:katalyst-testing-core")
    testImplementation("io.github.darkryh.katalyst:katalyst-testing-ktor")
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
