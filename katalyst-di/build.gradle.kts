plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Katalyst modules orchestrated by DI
    implementation(projects.katalystCore)
    implementation(projects.katalystTransactions)
    implementation(projects.katalystPersistence)
    implementation(projects.katalystScanner)
    implementation(projects.katalystKtor)

    // Event system modules
    implementation(projects.katalystEvents)
    implementation(projects.katalystEventsBus)

    // Persistence discovery support
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.migration.jdbc)

    // Dependency injection
    api(libs.koin.core)
    api(libs.koin.ktor)

    // Ktor integration
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)

    // Language/runtime utilities
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.coroutines.core)

    // Diagnostics & reflection aids
    implementation(libs.logback)
    implementation(libs.jansi)
    implementation(libs.reflections)
    implementation(libs.asm)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.koin.test)
    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.dao)
    testImplementation(libs.exposed.jdbc)
    testImplementation(libs.h2)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(testFixtures(projects.katalystTestingCore))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
