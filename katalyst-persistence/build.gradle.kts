plugins {
    kotlin("jvm")
}

dependencies {
    // Language/runtime
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Internal modules
    implementation(projects.katalystCore)
    implementation(projects.katalystEvents)
    implementation(projects.katalystTransactions)  // NEW: For DatabaseTransactionManager

    // Persistence stack
    implementation(libs.hikari)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    // Dependency injection & concurrency
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback)
}

kotlin {
    jvmToolchain(21)
}
