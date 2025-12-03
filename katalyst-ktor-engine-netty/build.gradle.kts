plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Katalyst modules
    implementation(projects.katalystCore)
    implementation(projects.katalystDi)
    implementation(projects.katalystConfigSpi)

    // Ktor with Netty engine
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)

    // Dependency injection
    implementation(libs.koin.core)

    // Kotlin
    implementation(kotlin("reflect"))
}
