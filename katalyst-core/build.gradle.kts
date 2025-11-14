plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Kotlin runtime & DI infrastructure
    implementation(kotlin("stdlib"))
    implementation(projects.katalystEvents)
    implementation(projects.katalystTransactions)  // NEW: For transaction compatibility layer
    implementation(libs.koin.core)

    // Concurrency primitives
    implementation(libs.kotlinx.coroutines.core)

    // Persistence (Exposed)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    // Logging
    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}
