plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(platform(libs.katalyst.bom))
    implementation(libs.katalyst.starter.web)
    implementation(libs.katalyst.starter.persistence)

    // DNS resolution
    implementation("dnsjava:dnsjava:3.5.2")

    // Shared and storage modules
    implementation(project(":boshi-server:boshi-shared"))
    implementation(project(":boshi-server:boshi-storage"))

    // Testing
    testImplementation(platform(libs.katalyst.bom))
    testImplementation(libs.katalyst.starter.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
