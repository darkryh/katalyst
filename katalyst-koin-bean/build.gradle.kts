plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    implementation(projects.katalystCore)
    implementation(projects.katalystDi)
    api(libs.koin.core)
    implementation(kotlin("reflect"))
    // Index-key displacement is reported through the framework's logger, not Koin's: Koin's
    // default logger is the empty one, so a warning routed there would never be seen.
    implementation(libs.slf4j.api)

    testImplementation(libs.koin.test)
}
