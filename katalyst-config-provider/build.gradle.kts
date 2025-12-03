plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Katalyst core - provides ConfigProvider, ConfigValidator interfaces
    implementation(projects.katalystCore)

    // Dependency Injection - for DI container access during bootstrap
    implementation(libs.koin.core)

    // Reflection support - for ConfigMetadata discovery
    implementation(projects.katalystScanner)
    implementation(kotlin("reflect"))

    // Reflections library - for bytecode scanning of AutomaticServiceConfigLoader
    implementation(libs.reflections)

    // Testing
    testImplementation(libs.koin.test)
}
