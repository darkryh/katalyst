plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Katalyst core - provides ConfigProvider, ConfigValidator interfaces
    implementation(projects.katalystCore)

    // Reflection support - for ConfigMetadata discovery
    implementation(projects.katalystScanner)
    implementation(kotlin("reflect"))

    // Reflections library - for bytecode scanning of AutomaticServiceConfigLoader
    implementation(libs.reflections)
    implementation(libs.slf4j.api)
}
