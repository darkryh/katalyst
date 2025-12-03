plugins {
    id("io.github.darkryh.katalyst.conventions.ktor-server")
}

dependencies {
    // Share routing + DI infrastructure from katalyst-ktor
    implementation(projects.katalystKtor)
    implementation(projects.katalystDi)

    // Ktor WebSocket stack
    implementation(libs.ktor.server.web.sockets)

    // Testing
    testImplementation(libs.koin.test)
    testImplementation(libs.ktor.client.websockets)
}
