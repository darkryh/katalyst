plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Language/runtime
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Core event and messaging modules
    implementation(projects.katalystEvents)
    implementation(projects.katalystEventsBus)
    implementation(projects.katalystEventsTransport)
    implementation(projects.katalystEventsClient)
    implementation(projects.katalystMessaging)

    // Dependency injection
    implementation(libs.koin.core)

    // Coroutines for async messaging
    implementation(libs.kotlinx.coroutines.core)

    // Kotlin AMQP client (pure Kotlin, coroutine-first design)
    // kourier: Pure Kotlin AMQP client with automatic recovery
    implementation("dev.kourier:amqp-client-robust:0.2.8")

    // JSON serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
