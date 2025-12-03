plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    implementation(kotlin("reflect"))

    // Event modules
    implementation(projects.katalystEvents)
    implementation(projects.katalystEventsBus)
    implementation(projects.katalystEventsTransport)
    implementation(projects.katalystDi)

    // Messaging contracts
    implementation(projects.katalystMessaging)

    // Dependency injection
    implementation(libs.koin.core)
    implementation(libs.koin.core.jvm)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
}
