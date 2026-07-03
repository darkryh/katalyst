plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

// Jetty engine starter. Add this alongside katalyst-starter-web to run on Jetty:
//   implementation("io.github.darkryh.katalyst:katalyst-starter-engine-jetty")
// Exposes the Jetty engine module (provides JettyServer) plus the Ktor Jetty engine artifact.
dependencies {
    api(projects.katalystKtorEngineJetty)
    api(libs.ktor.server.jetty.jakarta)
}
