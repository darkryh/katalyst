plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Core event and messaging modules
    implementation(projects.katalystEvents)
    implementation(projects.katalystEventsBus)
    implementation(projects.katalystEventsTransport)
    implementation(projects.katalystEventsClient)
    implementation(projects.katalystMessaging)

    // Dependency injection
    implementation(libs.koin.core)

    // Kotlin AMQP client (pure Kotlin, coroutine-first design)
    // kourier: Pure Kotlin AMQP client with automatic recovery
    implementation(libs.kourier.amqp.robust)

    // JSON serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.kotlinx.coroutines.test)
}
