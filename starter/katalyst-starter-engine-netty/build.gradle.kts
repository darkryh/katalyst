plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

// Netty engine starter. Add this alongside katalyst-starter-web to run on Netty:
//   implementation("io.github.darkryh.katalyst:katalyst-starter-engine-netty")
// Exposes the Netty engine module (provides NettyServer) plus the Ktor Netty engine artifact.
dependencies {
    api(projects.katalystKtorEngineNetty)
    api(libs.ktor.server.netty)
}
