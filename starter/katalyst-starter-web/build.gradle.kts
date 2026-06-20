plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

dependencies {
    api(projects.katalystStarterCore)
    api(projects.katalystKtor)
    api(projects.katalystKtorEngineNetty)
    api(libs.ktor.server.core)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.call.id)
    api(libs.ktor.server.rate.limit)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.server.host.common)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.netty)
    api(libs.ktor.server.config.yaml)
}
