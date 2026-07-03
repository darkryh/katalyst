plugins {
    id("io.github.darkryh.katalyst.conventions.common")
}

dependencies {
    // The shared wire contract.
    implementation(projects.katalystTelemetryModel)

    // Serialization runtime for the transport (model serializers are generated in -model).
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    // Framework modules whose already-computed state the capturers tap (union of all capturer
    // moduleDeps), plus katalyst-di for the KatalystFeature contract.
    implementation(projects.katalystCore)
    implementation(projects.katalystDi)
    implementation(projects.katalystPersistence)
    implementation(projects.katalystTransactions)
    implementation(projects.katalystMigrations)
    implementation(projects.katalystEvents)
    implementation(projects.katalystEventsBus)
    implementation(projects.katalystKtor)
    implementation(projects.katalystScheduler)

    // Transport: loopback CIO server with WebSockets. ktor-server-core is also required by the
    // HttpCapturer (di declares ktor via implementation, not api, so it is not transitive).
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.web.sockets)

    testImplementation(libs.kotlinx.coroutines.test)
}

// Per-module coverage floor (ratchet — raise as coverage grows; never lower). See TESTING_STRATEGY.md.
kover {
    reports {
        verify {
            rule {
                minBound(0)
            }
        }
    }
}
