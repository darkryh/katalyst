plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Katalyst modules
    implementation("io.github.darkryh.katalyst:katalyst-core")
    implementation("io.github.darkryh.katalyst:katalyst-di")
    implementation("io.github.darkryh.katalyst:katalyst-persistence")
    implementation("io.github.darkryh.katalyst:katalyst-events")
    implementation("io.github.darkryh.katalyst:katalyst-events-bus")
    implementation("io.github.darkryh.katalyst:katalyst-transactions")
    implementation("io.github.darkryh.katalyst:katalyst-scheduler")
    implementation("io.github.darkryh.katalyst:katalyst-ktor")

    // Boshi modules
    implementation(project(":boshi-server:boshi-shared"))
    implementation(project(":boshi-server:boshi-storage"))
    implementation(project(":boshi-server:boshi-smtp"))

    // Ktor
    implementation(libs.ktor.server.core)

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
