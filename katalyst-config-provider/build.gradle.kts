plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Katalyst core - provides ConfigProvider, ConfigValidator interfaces
    implementation(projects.katalystCore)

    // Dependency Injection - for DI container access during bootstrap
    implementation(libs.koin.core)

    // Reflection support - for ConfigMetadata discovery
    implementation(projects.katalystScanner)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.koin.test)
}

kotlin {
    jvmToolchain(21)
}
