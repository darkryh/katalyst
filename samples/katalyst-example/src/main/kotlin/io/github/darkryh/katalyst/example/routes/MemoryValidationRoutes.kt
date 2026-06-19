package io.github.darkryh.katalyst.example.routes

import io.github.darkryh.katalyst.database.DatabaseFactory
import io.github.darkryh.katalyst.example.api.dto.MemoryValidationTelemetry
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.github.darkryh.katalyst.ktor.extension.ktInject
import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.lang.management.ManagementFactory

fun Route.memoryValidationRoutes() = katalystRouting {
    get("/internal/memory-validation/telemetry") {
        if (System.getenv("KATALYST_MEMORY_VALIDATION") != "true") {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val databaseFactory by call.ktInject<DatabaseFactory>()
        val transactionManager by call.ktInject<DatabaseTransactionManager>()
        val runtime = Runtime.getRuntime()
        val pool = databaseFactory.poolSnapshot()
        call.respond(
            MemoryValidationTelemetry(
                heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
                heapCommittedBytes = runtime.totalMemory(),
                threadCount = ManagementFactory.getThreadMXBean().threadCount,
                poolActive = pool.active,
                poolIdle = pool.idle,
                poolPending = pool.pending,
                poolTotal = pool.total,
                transactionAdapters = transactionManager.getAdapterCount(),
            )
        )
    }
}
