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
