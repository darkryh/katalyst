plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // Core events module
    implementation(projects.katalystEvents)

    // Messaging contracts
    implementation(projects.katalystMessaging)

    // Dependency injection
    implementation(libs.koin.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
