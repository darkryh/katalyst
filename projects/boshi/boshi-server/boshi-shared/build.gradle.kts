plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(platform(libs.katalyst.bom))
    implementation(libs.katalyst.starter.web)
    implementation(libs.katalyst.starter.persistence)
    implementation(libs.katalyst.starter.migrations)

    // Testing
    testImplementation(platform(libs.katalyst.bom))
    testImplementation(libs.katalyst.starter.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation("org.testcontainers:testcontainers")
}
