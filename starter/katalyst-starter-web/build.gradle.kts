plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

// Engine-agnostic web starter. It intentionally does NOT bundle a Ktor engine — consumers add
// exactly one engine starter (katalyst-starter-engine-netty | -jetty | -cio) so the engine is an
// explicit, swappable choice instead of a forced Netty bundle.
dependencies {
    api(projects.katalystStarterCore)
    api(projects.katalystKtor)
    api(libs.ktor.server.core)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.call.id)
    api(libs.ktor.server.rate.limit)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.server.host.common)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.config.yaml)
}
