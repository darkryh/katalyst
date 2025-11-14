plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // Internal modules leveraged by the scanner for integration tests
    implementation(projects.katalystCore)
    implementation(projects.katalystPersistence)

    // Ktor + Koin surface area used by discovery utilities
    implementation(libs.ktor.server.core)
    implementation(libs.koin.ktor)

    // Concurrency & reflection utilities
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.reflections)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(projects.katalystEvents)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
