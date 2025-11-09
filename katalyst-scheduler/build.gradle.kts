plugins {
    kotlin("jvm")
}

dependencies {
    // Language/runtime
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Core modules
    implementation(projects.katalystCore)
    implementation(projects.katalystDi)

    // Dependency injection & concurrency
    implementation(libs.koin.core)
    implementation(libs.koin.core.jvm)
    implementation(libs.kotlinx.coroutines.core)

    // Bytecode analysis for scheduler method discovery
    implementation(libs.asm)

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
