plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

// Intentionally dependency-free.
//
// katalyst-conventions is the single source of truth for the names and contracts
// that define Katalyst discovery (marker interface FQNs, the routing DSL function
// names + their bytecode owners, and the discovery category labels). The runtime
// (katalyst-di), the static analysis layer (katalyst-analysis) and the IntelliJ
// plugin all reference these constants so the three layers cannot drift apart.
//
// It must stay zero-dependency: it only declares strings, so anything may depend on
// it without pulling in Ktor, Exposed, Koin or reflection.
dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}
