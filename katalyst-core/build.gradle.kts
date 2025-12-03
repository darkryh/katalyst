plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Kotlin runtime & DI infrastructure
    implementation(projects.katalystEvents)
    implementation(projects.katalystTransactions)
    implementation(libs.koin.core)

    // Persistence (Exposed)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    testImplementation(libs.kotlinx.coroutines.test)
}
