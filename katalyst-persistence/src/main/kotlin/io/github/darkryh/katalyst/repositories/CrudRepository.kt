package io.github.darkryh.katalyst.repositories

import io.github.darkryh.katalyst.repositories.model.PageInfo
import io.github.darkryh.katalyst.repositories.model.QueryFilter
import io.github.darkryh.katalyst.repositories.model.SortOrder
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import io.github.darkryh.katalyst.core.persistence.Table as KatalystTable
import org.jetbrains.exposed.v1.core.SortOrder as ExposedSortOder

// ============= Core Repository Interface =============

/**
 * Generic repository interface for CRUD operations on Exposed [IdTable] metadata.
 *
 * Provides common data access patterns including query building, pagination, and
 * entity mapping. Implementers supply a table reference that also implements
 * [io.github.darkryh.katalyst.core.persistence.Table]. The table exposes explicit mappers
 * for translating between Exposed [ResultRow] objects and DOMAIN entities, as well
 * as a binder used for inserts/updates. This keeps persistence logic centralized
 * with the schema definition and avoids reflection-based inference.
 *
 * **Usage Example:**
 * ```kotlin
 * object UsersTable : LongIdTable("users"), Table<UserEntity> {
 *     val name = varchar("name", 100)
 *     val email = varchar("email", 150)
 *     val createdAtMillis = long("created_at_millis")
 *
 *     override fun mapRow(row: ResultRow) = UserEntity(
 *         id = row[id].value,
 *         name = row[name],
 *         email = row[email],
 *         createdAtMillis = row[createdAtMillis]
 *     )
 *
 *     override fun assignEntity(statement: UpdateBuilder<*>, entity: UserEntity, skipIdColumn: Boolean) {
 *         if (!skipIdColumn && entity.id != null) {
 *             statement[id] = EntityID(entity.id, this)
 *         }
 *         statement[name] = entity.name
 *         statement[email] = entity.email
 *         statement[createdAtMillis] = entity.createdAtMillis
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
interface CrudRepository<Id : Comparable<Id>, IdentifiableEntityId : Identifiable<Id>> {
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
        table.asKatalystTable<Id, IdentifiableEntityId>().mapRow(row)


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
            if (updated > 0) id else insertEntity(entity, skipIdColumn = false)
        } else {
            insertEntity(entity, skipIdColumn = true)
        }

        return findById(persistedId)
            ?: error("Entity with id=$persistedId could not be loaded after persistence")
    }



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

    private fun resolveSortColumn(name: String?): Column<*>? {
        if (name.isNullOrBlank()) return null
        return table.columns.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }
    }

    private fun insertEntity(entity: IdentifiableEntityId,skipIdColumn : Boolean = true): Id {
        val katalystTable = table.asKatalystTable<Id, IdentifiableEntityId>()
        val generatedId = table.insertAndGetId { insertStatement ->
            katalystTable.assignEntity(insertStatement, entity, skipIdColumn = skipIdColumn)
        }.value
        return generatedId
    }

    private fun updateEntity(id: Id, entity: IdentifiableEntityId, skipIdColumn: Boolean = true): Int {
        val katalystTable = table.asKatalystTable<Id, IdentifiableEntityId>()
        return table.update({ table.id eq entityId(id) }) { updateStatement ->
            katalystTable.assignEntity(updateStatement, entity, skipIdColumn = skipIdColumn)
        }
    }

    private fun SortOrder.toExposed(): ExposedSortOder =
        when (this) {
            SortOrder.ASCENDING -> ExposedSortOder.ASC
            SortOrder.DESCENDING -> ExposedSortOder.DESC
        }
}

@Suppress("UNCHECKED_CAST")
private fun <Id : Comparable<Id>, Entity : Identifiable<Id>> IdTable<Id>.asKatalystTable(): KatalystTable<Id, Entity> =
    this as? KatalystTable<Id, Entity>
        ?: error(
            "Table ${this.tableName} must implement io.github.darkryh.katalyst.core.persistence.Table<Id, Entity> " +
                "where Entity implements Identifiable<Id>, and provide explicit mapping helpers"
        )
