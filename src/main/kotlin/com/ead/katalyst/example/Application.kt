package com.ead.katalyst.example

import com.ead.katalyst.di.katalystApplication
import com.ead.katalyst.example.infra.config.DatabaseConfigFactory
import com.ead.katalyst.example.routes.configureHttp
import io.ktor.server.application.Application

/**
 * Main entry point for the Katalyst Ktor application.
 *
 * The `katalystApplication` DSL automatically:
 * 1. Initializes Koin DI with all Katalyst library modules
 * 2. Configures the Ktor application through Application.module()
 * 3. Starts the server engine (Netty by default)
 *
 * All dependency injection and service registration is handled automatically
 * by the library, so developers can focus on custom application logic.
 *
 * **Usage:**
 * ```bash
 * java -jar app.jar                    # Uses Netty engine (default)
 * java -jar app.jar -engine=jetty      # Uses Jetty engine
 * java -jar app.jar -engine=cio        # Uses CIO engine
 * ```
 *
 * **Configuration:**
 * The katalystApplication block can be customized:
 * ```kotlin
 * fun main(args: Array<String>) = katalystApplication(args) {
 *     enableScheduler()
 *     withServerConfig {
 *         netty()
 *         withServerWrapper(ServerEngines.withLogging())
 *     }
 * }
 * ```
 */
fun main(args: Array<String>) = katalystApplication(args) {
    database(DatabaseConfigFactory.fromEnvironment())
    scanPackages("com.ead.katalyst.example")
    enableScheduler()
}

fun Application.module() {
    configureHttp()
}
