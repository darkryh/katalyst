plugins {
    id("io.github.darkryh.katalyst.conventions.base")
}

// CIO engine starter. Add this alongside katalyst-starter-web to run on CIO:
//   implementation("io.github.darkryh.katalyst:katalyst-starter-engine-cio")
// Exposes the CIO engine module (provides CioServer) plus the Ktor CIO engine artifact.
dependencies {
    api(projects.katalystKtorEngineCio)
    api(libs.ktor.server.cio)
}
