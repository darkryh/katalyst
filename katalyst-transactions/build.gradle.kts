plugins {
    id("io.github.darkryh.katalyst.conventions.persistence")
}

dependencies {
    // Internal modules
    implementation(projects.katalystEvents)

    // Persistence stack (Exposed)
    api(libs.exposed.core)
    api(libs.exposed.jdbc)

    // Testing
    testImplementation(libs.koin.test.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}
