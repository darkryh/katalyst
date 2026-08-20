plugins {
    id("io.github.darkryh.katalyst.conventions.ktor-server")
}

dependencies {
    // Katalyst modules
    implementation(projects.katalystCore)
    implementation(projects.katalystScanner)

    // Ktor WebSocket stack
    api(libs.ktor.server.web.sockets)

    // Kotlin
    implementation(kotlin("reflect"))

    // Testing
    testImplementation(libs.ktor.client.websockets)
}
