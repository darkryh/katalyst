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

    // An SLF4J binding on the test classpath: the near-miss tests assert that a set-but-unmatched
    // configuration key is reported, which needs a real appender to capture.
    testImplementation(libs.logback)
}
