plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Language/runtime
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Internal modules
    implementation(projects.katalystEvents)

    // Persistence stack (Exposed)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    // Dependency injection & concurrency
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}