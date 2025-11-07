plugins {
    kotlin("jvm")
}

dependencies {
    // Language/runtime
    implementation(kotlin("stdlib"))

    // Core modules
    implementation(projects.katalystCore)

    // Dependency injection & concurrency
    implementation(libs.koin.core)
    implementation(libs.koin.core.jvm)
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
