plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Katalyst core - provides ConfigProvider, ConfigValidator interfaces
    implementation(projects.katalystCore)

    // Katalyst DI - provides KatalystFeature interface for auto-discovery
    implementation(projects.katalystDi)

    // Dependency Injection - for DI container access during bootstrap
    implementation(libs.koin.core)

    // Reflection support - for ConfigMetadata discovery
    implementation(projects.katalystScanner)

    // Reflections library - for bytecode scanning of AutomaticServiceConfigLoader
    implementation(libs.reflections)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.koin.test)
}

kotlin {
    jvmToolchain(21)
}
