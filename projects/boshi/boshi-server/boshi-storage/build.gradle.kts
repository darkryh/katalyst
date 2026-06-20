plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(platform(libs.katalyst.bom))
    implementation(libs.katalyst.starter.persistence)

    // Shared module
    implementation(project(":boshi-server:boshi-shared"))

    // Testing
    testImplementation(platform(libs.katalyst.bom))
    testImplementation(libs.katalyst.starter.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
