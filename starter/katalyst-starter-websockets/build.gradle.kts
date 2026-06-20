plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

dependencies {
    api(projects.katalystStarterWeb)
    api(projects.katalystWebsockets)
    api(libs.ktor.server.web.sockets)
}
