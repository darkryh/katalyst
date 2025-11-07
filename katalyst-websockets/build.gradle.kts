plugins {
    kotlin("jvm")
}

group = "com.ead.katalyst"
version = "0.0.1"

dependencies {
    // Share routing + DI infrastructure from katalyst-ktor
    implementation(projects.katalystKtor)

    // Ktor WebSocket stack
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.web.sockets)

    // Dependency injection + coroutines
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
}

kotlin {
    jvmToolchain(21)
}
