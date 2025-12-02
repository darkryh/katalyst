plugins {
    id("com.ead.katalyst.conventions.common")
}

dependencies {
    // Internal modules leveraged by the scanner for integration tests
    implementation(projects.katalystCore)
    implementation(projects.katalystPersistence)

    // Ktor + Koin surface area used by discovery utilities
    implementation(libs.ktor.server.core)
    implementation(libs.koin.ktor)

    // Concurrency & reflection utilities
    implementation(libs.reflections)

    // Testing
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(projects.katalystEvents)
}
