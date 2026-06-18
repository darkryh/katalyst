plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Internal modules leveraged by the scanner for integration tests
    implementation(projects.katalystCore)
    implementation(projects.katalystPersistence)

    // Ktor surface area used by discovery utilities
    implementation(libs.ktor.server.core)

    // Concurrency & reflection utilities
    implementation(libs.reflections)

    // Testing
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(projects.katalystEvents)
}
