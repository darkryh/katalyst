plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Language/runtime
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Event modules
    implementation(projects.katalystEvents)
    implementation(projects.katalystEventsBus)
    implementation(projects.katalystEventsTransport)

    // Messaging contracts
    implementation(projects.katalystMessaging)

    // Dependency injection
    implementation(libs.koin.core)
    implementation(libs.koin.core.jvm)

    // Coroutines for async operations
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
