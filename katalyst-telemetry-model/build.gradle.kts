plugins {
    id("io.github.darkryh.katalyst.conventions.common")
    // @Serializable wire types: this is the single source of truth for the on-the-wire contract
    // between the backend telemetry feature and the katalyst-tui inspector.
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    // The only dependency: kotlinx-serialization. This module stays framework-free on purpose so
    // both the backend (katalyst-telemetry) and the standalone UI (katalyst-tui) can depend on it
    // without dragging in Ktor, Exposed, or the DI engine.
    implementation(libs.kotlinx.serialization.json)
}

// Per-module coverage floor (ratchet — raise as coverage grows; never lower). See TESTING_STRATEGY.md.
// Pure data classes; the meaningful floor arrives once round-trip serialization tests land.
kover {
    reports {
        verify {
            rule {
                minBound(0)
            }
        }
    }
}
