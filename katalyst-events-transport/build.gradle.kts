plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Language/runtime
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Core events module
    implementation(projects.katalystEvents)

    // Messaging contracts
    implementation(projects.katalystMessaging)

    // Dependency injection
    implementation(libs.koin.core)

    // Coroutines for async serialization
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
