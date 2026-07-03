package com.example.smoke

import io.github.darkryh.katalyst.config.yaml.enableYamlConfiguration
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import io.github.darkryh.katalyst.di.feature.enableServerTuning
import io.github.darkryh.katalyst.di.katalystApplication
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.ktor.builder.katalystRouting
import io.github.darkryh.katalyst.ktor.extension.ktInject
import io.github.darkryh.katalyst.ktor.middleware.katalystMiddleware
import io.github.darkryh.katalyst.repositories.CrudRepository
import io.github.darkryh.katalyst.repositories.Identifiable
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

// A complete Katalyst vertical slice built entirely from transitive starter dependencies:
// @Serializable DTO (kotlinx.serialization, applied by the Katalyst plugin), an Exposed table +
// CrudRepository, a Service with transactions, and a route — none of which required declaring Ktor,
// Exposed or kotlinx.serialization in build.gradle.kts.

@Serializable
data class WidgetResponse(val id: Long, val name: String)

data class Widget(
    override val id: Long? = null,
    val name: String,
) : Identifiable<Long>

object WidgetsTable : LongIdTable("widgets"), Table<Long, Widget> {
    val name = varchar("name", 120)

    override val mapping = mapping<Long, Widget> {
        generatedId(id, Widget::id)
        field(name, Widget::name)
        construct { Widget(id = this[id], name = this[name]) }
    }
}

class WidgetRepository : CrudRepository<Long, Widget> {
    override val table: LongIdTable = WidgetsTable
}

class WidgetService(private val repository: WidgetRepository) : Service {
    suspend fun create(name: String): Widget = transactionManager.transaction {
        repository.save(Widget(name = name))
    }

    suspend fun all(): List<Widget> = transactionManager.transaction { repository.findAll() }
}

@Suppress("unused")
fun Application.smokeHttpConfig() = katalystMiddleware {
    install(ContentNegotiation) { json() }
}

@Suppress("unused")
fun Route.widgetRoutes() = katalystRouting {
    get("/smoke") {
        val service by call.ktInject<WidgetService>()
        service.create("smoke-widget")
        call.respond(service.all().map { WidgetResponse(it.id!!, it.name) })
    }
}

fun main(args: Array<String>) = katalystApplication(args) {
    // `smokeEngine` is provided by the engine-specific source set selected at build time
    // (src/engine-<netty|jetty|cio>/kotlin) — this is the only place the chosen engine is named.
    engine(smokeEngine)
    beanEngine(KoinBeanEngine)
    features {
        enableYamlConfiguration()
        enableServerTuning()
    }
    database { fromConfiguration() }
    scanPackages("com.example.smoke")
    schema { createMissing() }
}
