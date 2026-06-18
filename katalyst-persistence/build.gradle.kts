plugins {
    id("io.github.darkryh.katalyst.conventions.persistence")
}

dependencies {
    implementation(kotlin("reflect"))

    // Internal modules
    implementation(projects.katalystCore)
    implementation(projects.katalystEvents)
    implementation(projects.katalystTransactions)
    implementation(libs.exposed.java.time)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(projects.katalystTestingCore)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Per-module coverage floor (ratchet — raise as coverage grows; never lower). See TESTING_STRATEGY.md.
kover {
    reports {
        verify {
            rule {
                minBound(80) // baseline ~84% line coverage
            }
        }
    }
}
