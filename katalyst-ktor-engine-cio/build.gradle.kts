plugins {
    id("com.ead.katalyst.conventions.common")
}

dependencies {
    // Katalyst modules
    implementation(projects.katalystCore)
    implementation(projects.katalystDi)

    // Ktor with CIO engine
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    // Dependency injection
    implementation(libs.koin.core)

    // Kotlin
    implementation(kotlin("reflect"))
}
