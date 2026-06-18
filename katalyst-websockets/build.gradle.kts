plugins {
    id("io.github.darkryh.katalyst.conventions.ktor-server")
}

dependencies {
    // WebSocket feature bridge; re-export the Ktor-owned WebSocket API.
    api(projects.katalystKtor)
    implementation(projects.katalystDi)

    // Testing
    testImplementation(projects.katalystCore)
    testImplementation(libs.ktor.client.websockets)
}
