package com.ead.katalyst.testing.ktor

import com.ead.katalyst.testing.core.KatalystTestEnvironment
import com.ead.katalyst.testing.core.KatalystTestEnvironmentBuilder
import com.ead.katalyst.testing.core.katalystTestEnvironment
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

/**
 * Installs every Ktor module that was discovered during DI bootstrap into the supplied
 * [Application]. This mirrors what happens in the real server so tests exercise
 * the exact same pipeline.
 */
fun Application.installKatalystDiscoveredModules(environment: KatalystTestEnvironment) {
    environment.discoveredKtorModules.forEach { module ->
        module.install(this)
    }
}

/**
 * Boots a full Katalyst DI environment and wires it into Ktor's [testApplication].
 *
 * @param configureEnvironment Builder hook to customize database, scan packages, and overrides.
 * @param autoInstallDiscoveredModules When true (default), installs all auto-discovered
 * Ktor modules before running [applicationConfig].
 * @param applicationConfig Additional application-level configuration (optional).
 * @param testBody Suspended test body with access to the [ApplicationTestBuilder] and
 * the active [KatalystTestEnvironment].
 */
fun katalystTestApplication(
    configureEnvironment: KatalystTestEnvironmentBuilder.() -> Unit = {},
    autoInstallDiscoveredModules: Boolean = true,
    applicationConfig: Application.(KatalystTestEnvironment) -> Unit = {},
    testBody: suspend ApplicationTestBuilder.(KatalystTestEnvironment) -> Unit
) {
    val environment = katalystTestEnvironment(configureEnvironment)
    try {
        testApplication {
            application {
                if (autoInstallDiscoveredModules) {
                    installKatalystDiscoveredModules(environment)
                }
                applicationConfig(environment)
            }
            testBody(environment)
        }
    } finally {
        environment.close()
    }
}
