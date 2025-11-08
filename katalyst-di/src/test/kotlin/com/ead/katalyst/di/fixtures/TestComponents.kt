package com.ead.katalyst.di.fixtures

import com.ead.katalyst.events.DomainEvent
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.events.EventMetadata
import com.ead.katalyst.repositories.Identifiable
import com.ead.katalyst.repositories.PageInfo
import com.ead.katalyst.repositories.QueryFilter
import com.ead.katalyst.repositories.Repository
import com.ead.katalyst.services.Service
import com.ead.katalyst.tables.Table
import com.ead.katalyst.validators.ValidationResult
import com.ead.katalyst.validators.Validator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class TestEntity(
    override val id: Long,
    val name: String
) : Identifiable<Long>

object TestTable : LongIdTable("di_test_entities"), Table

class TestRepository : Repository<Long, TestEntity> {
    private val data = mutableMapOf<Long, TestEntity>()
    private val lock = ReentrantLock()

    override val table: TestTable = TestTable

    override fun mapper(row: ResultRow): TestEntity =
        error("TestRepository operates on in-memory data and should not rely on mapper()")

    override fun save(entity: TestEntity): TestEntity = lock.withLock {
        data[entity.id] = entity
        entity
    }

    override fun findById(id: Long): TestEntity? = lock.withLock { data[id] }

    override fun findAll(): List<TestEntity> = lock.withLock { data.values.toList() }

    override fun findAll(filter: QueryFilter): Pair<List<TestEntity>, PageInfo> = lock.withLock {
        val all = data.values.toList()
        all to PageInfo(
            limit = filter.limit,
            offset = filter.offset,
            total = all.size
        )
    }

    override suspend fun count(): Long = lock.withLock { data.size.toLong() }

    override suspend fun delete(id: Long) {
        lock.withLock { data.remove(id) }
    }
}

class TestValidator : Validator<TestEntity> {
    override suspend fun validate(entity: TestEntity): ValidationResult {
        return if (entity.name.isBlank()) {
            ValidationResult.invalid("name must not be blank")
        } else {
            ValidationResult.valid()
        }
    }
}

class SampleCreatedEvent(
    val entity: TestEntity,
    private val metadata: EventMetadata = EventMetadata.of("test.sample-created")
) : DomainEvent {
    override fun getMetadata(): EventMetadata = metadata
}

class SampleEventHandler : EventHandler<SampleCreatedEvent> {
    private val mutex = Mutex()
    private val captured = mutableListOf<SampleCreatedEvent>()

    override val eventType = SampleCreatedEvent::class

    override suspend fun handle(event: SampleCreatedEvent) {
        mutex.withLock {
            captured += event
        }
    }

    suspend fun handledEvents(): List<SampleCreatedEvent> = mutex.withLock { captured.toList() }
}

class TestService(
    private val repository: TestRepository,
    private val validator: TestValidator
) : Service {
    suspend fun create(entity: TestEntity): TestEntity {
        validator.validate(entity)
        return transactionManager.transaction {
            repository.save(entity)
        }
    }
}
