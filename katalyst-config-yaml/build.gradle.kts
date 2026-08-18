plugins {
    id("io.github.darkryh.katalyst.conventions.common")
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

    // YAML parsing
    implementation(libs.snakeyaml)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // Regression fixture for the double-substitution defect (see YamlConfigurationSource.init):
    // a secret whose resolved value legitimately contains a literal `${...}` sequence. It has to
    // be a real process environment variable, because the defect is only observable when the very
    // same environment feeds the parse-time pass and the init-time pass.
    environment("KATALYST_TEST_LITERAL_PLACEHOLDER", "abc\${d}ef")
}
