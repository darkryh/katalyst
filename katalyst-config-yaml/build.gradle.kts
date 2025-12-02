plugins {
    id("com.ead.katalyst.conventions.common")
}

dependencies {
    // Katalyst core - provides ConfigProvider interface
    implementation(projects.katalystCore)

    // Config SPI for pluggable loaders
    api(projects.katalystConfigSpi)

    // Katalyst config provider - provides ServiceConfigLoader, ConfigLoaders, etc.
    implementation(projects.katalystConfigProvider)

    // Katalyst DI - provides KatalystFeature interface for auto-discovery
    implementation(projects.katalystDi)

    // Koin for DI
    implementation(libs.koin.core)

    // YAML parsing
    implementation(libs.snakeyaml)
    implementation(libs.ktor.server.config.yaml)

    // Testing
    testImplementation(libs.kotlinx.coroutines.test)
}
