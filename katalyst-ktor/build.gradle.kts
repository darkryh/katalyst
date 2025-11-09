plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // Katalyst modules
    implementation(projects.katalystCore)
    implementation(projects.katalystScanner)
    implementation(projects.katalystKtorEngine)

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)

    // Koin
    implementation(libs.koin.ktor)
    implementation(libs.koin.core)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(kotlin("reflect"))

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
