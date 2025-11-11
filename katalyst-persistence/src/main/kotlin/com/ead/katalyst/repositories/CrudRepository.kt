package com.ead.katalyst.repositories

import com.ead.katalyst.core.persistence.Table as KatalystTable
import com.ead.katalyst.repositories.model.PageInfo
import com.ead.katalyst.repositories.model.QueryFilter
import com.ead.katalyst.repositories.model.SortOrder
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder as ExposedSortOder
import kotlin.reflect.KClass

/**
 * Repository pattern interface and supporting types tailored for Exposed tables.
 *
 * The Repository pattern provides a data access abstraction with generic
 * type parameters for identifier and domain entity types.
 *
 * **Design Pattern (Like Spring Data JPA):**
 * - Repositories are data access objects, NOT general components
 * - They have a specific responsibility: CRUD operations
 * - Like Spring's `JpaRepository<Entity, ID>`, this interface marks data access classes
 * - Inherit from this interface just like you'd extend `JpaRepository`
 *
 * **Automatic Discovery:**
 * Inherit from Repository and the framework automatically:
 * 1. Discovers your repository class during startup
 * 2. Registers it in the DI container
 * 3. Makes it available for service injection
 *
 * **Default CRUD implementation:**
 * Developers only implement the [table] reference and the [mapper] function.
 * The framework provides insert/update/read/delete behaviour using the table metadata
 * and Kotlin reflection to bind entity properties to Exposed columns.
 *
 * **Exception Handling:**
 * Developers define and throw their own exceptions (e.g., NotFoundException, ConflictException)
 * as needed. The framework doesn't prescribe specific exception types.
 */


// ============= Core Repository Interface =============

/**
 * Generic repository interface for CRUD operations on Exposed [IdTable] metadata.
 *
 * Provides common data access patterns including query building, pagination, and
 * entity mapping. Implementers supply a table reference that also implements
 * [com.ead.katalyst.core.persistence.Table]. The table exposes explicit mappers
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
     * mapper provided by the associated Katalyst [Table].
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
        val identifier = entity.id
        val persistedId = if (identifier == null) {
            insertEntity(entity)
        } else {
            updateEntity(identifier, entity)
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
            .limit(filter.limit, offset = filter.offset.toLong())
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

    private fun insertEntity(entity: IdentifiableEntityId): Id {
        val katalystTable = table.asKatalystTable<Id, IdentifiableEntityId>()
        val generatedId = table.insertAndGetId { statement ->
            katalystTable.assignEntity(statement, entity, skipIdColumn = true)
        }.value
        return generatedId
    }

    private fun updateEntity(id: Id, entity: IdentifiableEntityId): Id {
        val katalystTable = table.asKatalystTable<Id, IdentifiableEntityId>()
        table.update({ table.id eq entityId(id) }) { statement ->
            katalystTable.assignEntity(statement, entity, skipIdColumn = true)
        }
        return id
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
            "Table ${this.tableName} must implement com.ead.katalyst.core.persistence.Table<Id, Entity> " +
                "where Entity implements Identifiable<Id>, and provide explicit mapping helpers"
        )
