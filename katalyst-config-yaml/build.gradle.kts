plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Katalyst core - provides ConfigProvider interface
    implementation(projects.katalystCore)

    // Katalyst config provider - provides ServiceConfigLoader, ConfigLoaders, etc.
    implementation(projects.katalystConfigProvider)

    // Katalyst DI - provides KatalystFeature interface for auto-discovery
    implementation(projects.katalystDi)

    // Koin for DI
    implementation(libs.koin.core)

    // YAML parsing
    implementation(libs.snakeyaml)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}
