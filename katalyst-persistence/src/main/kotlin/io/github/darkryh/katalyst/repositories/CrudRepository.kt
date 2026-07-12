package io.github.darkryh.katalyst.repositories

import io.github.darkryh.katalyst.repositories.model.PageInfo
import io.github.darkryh.katalyst.repositories.model.QueryFilter
import io.github.darkryh.katalyst.repositories.model.SortOrder
import io.github.darkryh.katalyst.core.persistence.WritableEntityMapping
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.concurrent.ConcurrentHashMap
import io.github.darkryh.katalyst.core.persistence.Table as KatalystTable
import org.jetbrains.exposed.v1.core.SortOrder as ExposedSortOder

// ============= Core Repository Interface =============

/**
 * Generic repository interface for CRUD operations on Exposed [IdTable] metadata.
 *
 * Provides common data access patterns including query building, pagination, and
 * entity mapping. Implementers supply a table reference that also implements
 * [io.github.darkryh.katalyst.core.persistence.Table]. The table exposes an explicit
 * mapping DSL for translating between Exposed [ResultRow] objects and domain entities,
 * as well as insert/update bindings. This keeps persistence logic centralized with
 * the schema definition and avoids reflection-based inference.
 *
 * **Usage Example:**
 * ```kotlin
 * object UsersTable : LongIdTable("users"), Table<UserEntity> {
 *     val name = varchar("name", 100)
 *     val email = varchar("email", 150)
 *     val createdAtMillis = long("created_at_millis")
 *
 *     override val mapping = mapping<Long, UserEntity> {
 *         generatedId(id, UserEntity::id)
 *         field(name, UserEntity::name)
 *         field(email, UserEntity::email)
 *         field(createdAtMillis, UserEntity::createdAtMillis)
 *
 *         construct {
 *             UserEntity(
 *                 id = this[id],
 *                 name = this[name],
 *                 email = this[email],
 *                 createdAtMillis = this[createdAtMillis]
 *             )
 *         }
 *     }
 * }
 *
 * data class UserEntity(
 *     override val id: Long? = null,
 *     val name: String,
 *     val email: String,
 *     val createdAtMillis: Long
 * ) : Identifiable<Long>
 *
 * class UserRepository : Repository<Long, UserEntity> {
 *     override val table = UsersTable
 * }
 * ```
 *
 * @param Id Primary key type managed by the table
 * @param IdentifiableEntityId Domain model returned by repository methods
 */
interface CrudRepository<Id, IdentifiableEntityId : Identifiable<Id>> where Id : Any, Id : Comparable<Id> {
    /**
     * Reference to the Exposed table that backs this repository.
     */
    val table: IdTable<Id>

    /**
     * Maps a database [ResultRow] to a domain [IdentifiableEntityId] using the
     * mapper provided by the associated Katalyst [io.github.darkryh.katalyst.core.persistence.Table].
     *
     * **Mapping Success Indicators:**
     * - Non-null return value indicates successful row → entity transformation
     * - All required fields are populated from the ResultRow
     * - ID is correctly extracted and set on the entity
     */
    fun map(row: ResultRow): IdentifiableEntityId =
        table.asKatalystTable<Id, IdentifiableEntityId>().mapping.read(row)


    /**
     * Saves or updates an entity.
     *
     * When the entity has a null identifier it is inserted, otherwise it is updated.
     * The entity is reloaded from the database and the mapped version is returned
     * to ensure fresh values (including generated columns).
     */
    fun save(entity: IdentifiableEntityId): IdentifiableEntityId {
        val id = entity.id
        val persistedId = if (id != null) {
            val updated = updateEntity(id, entity) // returns rows updated
            if (updated > 0) id else insertEntity(entity)
        } else {
            insertEntity(entity)
        }

        return findById(persistedId)
            ?: error("Entity with id=$persistedId could not be loaded after persistence")
    }

    /**
     * Persists every entity in [entities] with [save] semantics (create-or-update
     * by primary key), returning the reloaded entities in the same order.
     */
    fun saveAll(entities: List<IdentifiableEntityId>): List<IdentifiableEntityId> =
        entities.map { save(it) }

    /**
     * Finds an entity by ID.
     */
    fun findById(id: Id): IdentifiableEntityId? =
        table
            .selectAll().where { table.id eq entityId(id) }
            .limit(1)
            .firstOrNull()
            ?.let { map(it) }

    /**
     * Finds all entities ordered by primary key descending by default.
     */
    fun findAll(): List<IdentifiableEntityId> =
        table
            .selectAll()
            .orderBy(table.id, ExposedSortOder.DESC)
            .map { map(it) }

    /**
     * Finds entities with query filter and pagination.
     */
    fun findAll(filter: QueryFilter): Pair<List<IdentifiableEntityId>, PageInfo> {
        val sortColumn = resolveSortColumn(filter.sortBy) ?: table.id
        val sortOrder = filter.sortOrder.toExposed()

        val results = table
            .selectAll()
            .orderBy(sortColumn, sortOrder)
            .limit(filter.limit)
            .offset(filter.offset.toLong())
            .map { map(it) }

        val total = table.selectAll().count().toInt()

        return results to PageInfo(
            limit = filter.limit,
            offset = filter.offset,
            total = total
        )
    }

    /**
     * Counts total entities.
     */
    suspend fun count(): Long = table.selectAll().count()

    /**
     * Deletes an entity by ID.
     */
    suspend fun delete(id: Id) {
        table.deleteWhere { table.id eq entityId(id) }
    }

    // ------ Internal helpers ------

    private fun entityId(id: Id): EntityID<Id> = EntityID(id, table)

    /**
     * Resolves the writable mapping for [table], validating it at most once per table.
     *
     * The mapping shape (duplicate/missing-column checks) never changes for a given
     * table instance, so re-running [io.github.darkryh.katalyst.core.persistence.EntityMapping.validate]
     * on every [save]/[insertEntity]/[updateEntity] call is wasted work. Successful
     * validations are cached process-wide by table identity; a validation failure is
     * NOT cached, so a genuinely bad mapping keeps failing loudly on every call.
     */
    private fun writableMapping(): WritableEntityMapping<Id, IdentifiableEntityId> {
        val mapping = table.asKatalystTable<Id, IdentifiableEntityId>().mapping.asWritable()
        if (!validatedTables.contains(table)) {
            mapping.validate(table)
            validatedTables.add(table)
        }
        return mapping
    }

    private fun resolveSortColumn(name: String?): Column<*>? {
        if (name.isNullOrBlank()) return null
        return table.columns.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }
    }

    private fun insertEntity(entity: IdentifiableEntityId): Id {
        val mapping = writableMapping()
        val generatedId = table.insertAndGetId { insertStatement ->
            mapping.writeInsert(insertStatement, entity)
        }.value
        return generatedId
    }

    private fun updateEntity(id: Id, entity: IdentifiableEntityId): Int {
        val mapping = writableMapping()
        return table.update({ table.id eq entityId(id) }) { updateStatement ->
            mapping.writeUpdate(updateStatement, entity)
        }
    }

    private fun SortOrder.toExposed(): ExposedSortOder =
        when (this) {
            SortOrder.ASCENDING -> ExposedSortOder.ASC
            SortOrder.DESCENDING -> ExposedSortOder.DESC
        }

    private companion object {
        /**
         * Tables whose mapping has already passed
         * [io.github.darkryh.katalyst.core.persistence.EntityMapping.validate].
         *
         * Shared process-wide (keyed by table identity) so validation runs once per
         * table rather than on every [save]/[insertEntity]/[updateEntity] call.
         */
        val validatedTables: MutableSet<IdTable<*>> =
            ConcurrentHashMap.newKeySet()
    }
}

@Suppress("UNCHECKED_CAST")
private fun <Id, Entity : Identifiable<Id>> IdTable<Id>.asKatalystTable(): KatalystTable<Id, Entity>
    where Id : Any, Id : Comparable<Id> =
    this as? KatalystTable<Id, Entity>
        ?: error(
            "Table ${this.tableName} must implement io.github.darkryh.katalyst.core.persistence.Table<Id, Entity> " +
                "where Entity implements Identifiable<Id>, and provide explicit mapping helpers"
        )

private fun <Id, Entity : Identifiable<Id>> io.github.darkryh.katalyst.core.persistence.EntityMapping<Id, Entity>.asWritable(): WritableEntityMapping<Id, Entity>
    where Id : Any, Id : Comparable<Id> =
    this as? WritableEntityMapping<Id, Entity>
        ?: error("Table mapping must be created with io.github.darkryh.katalyst.core.persistence.mapping { ... }")
