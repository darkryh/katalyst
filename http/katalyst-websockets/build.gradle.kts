plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // WebSocket feature bridge; re-export the Ktor-owned WebSocket API.
    api(projects.katalystKtor)
    implementation(projects.katalystDi)
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(projects.katalystCore)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.server.test.host)
}
