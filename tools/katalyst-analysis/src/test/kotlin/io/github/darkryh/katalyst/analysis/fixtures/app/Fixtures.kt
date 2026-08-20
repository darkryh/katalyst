package io.github.darkryh.katalyst.analysis.fixtures.app

import io.github.darkryh.katalyst.config.provider.ConfigBinding
import io.github.darkryh.katalyst.config.provider.ConfigPrefix
import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.core.component.Service
import io.github.darkryh.katalyst.core.config.ConfigProvider
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import io.github.darkryh.katalyst.di.lifecycle.StartupHook
import io.github.darkryh.katalyst.events.DomainEvent
import io.github.darkryh.katalyst.events.EventHandler
import io.github.darkryh.katalyst.migrations.KatalystMigration
import io.github.darkryh.katalyst.repositories.CrudRepository
import io.github.darkryh.katalyst.repositories.Identifiable
import io.github.darkryh.katalyst.scheduler.job.SchedulerJobHandle
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

// A self-contained mini Katalyst application used to exercise KatalystAnalyzer end to end.
// It deliberately covers every discovery mechanism: entity/table/repository, service+scheduler
// method, component, event handler, migration, config loader and an application initializer.
// Function entrypoints (routes/middleware/websockets/exception handlers) live in Routes.kt.

data class Greeting(
    override val id: Long? = null,
    val text: String,
) : Identifiable<Long>

object GreetingsTable : LongIdTable("greetings"), Table<Long, Greeting> {
    val text = varchar("text", 255)

    override val mapping = mapping<Long, Greeting> {
        generatedId(id, Greeting::id)
        field(text, Greeting::text)
        construct { Greeting(id = this[id], text = this[text]) }
    }
}

class GreetingRepository : CrudRepository<Long, Greeting> {
    override val table: LongIdTable = GreetingsTable
}

class GreetingValidator : Component {
    fun validate(text: String): Boolean = text.isNotBlank()
}

class GreetingService(
    private val repository: GreetingRepository,
    private val validator: GreetingValidator,
) : Service {
    fun count(): Int = repository.hashCode() + validator.hashCode()

    // Discovered as a scheduler entrypoint purely by its return type; never invoked by analysis.
    fun scheduleDigest(): SchedulerJobHandle = error("scheduler methods are not invoked during analysis")
}

data class GreetingCreatedEvent(val text: String) : DomainEvent

class GreetingCreatedHandler(
    private val service: GreetingService,
) : EventHandler<GreetingCreatedEvent> {
    override val eventType = GreetingCreatedEvent::class
    override suspend fun handle(event: GreetingCreatedEvent) {
        service.count()
    }
}

class V1AddGreeting : KatalystMigration {
    override val id: String = "1_add_greeting"
    override val version: Long = 1
    override val description: String = "create greetings"
    override fun up() = Unit
}

class GreetingConfig(provider: ConfigProvider) : ConfigBinding {
    val prefix: String = provider.getString("greeting.prefix", "hello")
}

// Annotation-driven config: the common path — a plain data class with no interface, bound
// reflectively by the runtime's ConfigBinder. Discovered via the @ConfigPrefix marker annotation.
@ConfigPrefix("mail")
data class MailConfig(
    val host: String,
    val port: Int,
)

class GreetingInitializer : StartupHook {
    override val id: String = "greeting-initializer"
    override val order: Int = 0
    override suspend fun onStartup() = Unit
}
