plugins {
    id("com.ead.katalyst.conventions.ktor-server")
}

dependencies {
    // Katalyst modules
    implementation(projects.katalystCore)
    implementation(projects.katalystScanner)

    // Kotlin
    implementation(kotlin("reflect"))

    // Testing
    testImplementation(libs.koin.test)
}
