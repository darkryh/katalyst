plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Core events module
    implementation(projects.katalystEvents)

    // Transaction module for event deferral
    implementation(projects.katalystTransactions)

    // Dependency injection
    implementation(libs.koin.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
