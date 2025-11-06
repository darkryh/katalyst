package com.ead.katalyst.repositories

import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

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

// ============= Query Support =============

/**
 * Query builder for filtering and pagination.
 */
data class QueryFilter(
    val limit: Int = 50,
    val offset: Int = 0,
    val sortBy: String? = null,
    val sortOrder: SortOrder = SortOrder.ASCENDING
)

/**
 * Sort order enumeration.
 */
enum class SortOrder {
    ASCENDING, DESCENDING
}

/**
 * Page information for paginated results.
 */
data class PageInfo(
    val limit: Int,
    val offset: Int,
    val total: Int
) {
    val currentPage: Int
        get() = (offset / limit) + 1

    val totalPages: Int
        get() = (total + limit - 1) / limit

    val hasNextPage: Boolean
        get() = currentPage < totalPages
}

/**
 * Marker interface for entities managed by repositories.
 */
interface Identifiable<Id : Comparable<Id>> {
    val id: Id?
}

// ============= Core Repository Interface =============

/**
 * Generic repository interface for CRUD operations on Exposed [IdTable] metadata.
 *
 * Provides common data access patterns including query building, pagination, and
 * entity mapping. Implementers supply a table reference and a mapper that converts
 * Exposed [ResultRow] objects into DOMAIN entities. All standard CRUD functions are
 * implemented automatically using those hints.
 *
 * **Usage Example:**
 * ```kotlin
 * object UsersTable : LongIdTable("users") {
 *     val name = varchar("name", 100)
 *     val email = varchar("email", 150)
 * }
 *
 * data class UserEntity(
 *     override val id: Long? = null,
 *     val name: String,
 *     val email: String
 * ) : Identifiable<Long>
 *
 * class UserRepository : Repository<Long, UserEntity> {
 *     override val table = UsersTable
 *
 *     override fun mapper(row: ResultRow): UserEntity =
 *         UserEntity(
 *             id = row[table.id].value,
 *             name = row[table.name],
 *             email = row[table.email]
 *         )
 * }
 * ```
 *
 * @param Id Primary key type managed by the table
 * @param IdentifiableEntityId Domain model returned by repository methods
 */
interface Repository<Id : Comparable<Id>, IdentifiableEntityId : Identifiable<Id>> {
    /**
     * Reference to the Exposed table that backs this repository.
     */
    val table: IdTable<Id>

    /**
     * Maps a database [ResultRow] to a domain [IdentifiableEntityId].
     */
    fun mapper(row: ResultRow): IdentifiableEntityId

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
            ?.let { mapper(it) }

    /**
     * Finds all entities ordered by primary key descending by default.
     */
    fun findAll(): List<IdentifiableEntityId> =
        table
            .selectAll()
            .orderBy(table.id, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { mapper(it) }

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
            .map { mapper(it) }

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

    private fun insertEntity(entity: IdentifiableEntityId): Id {
        val generatedId = table.insertAndGetId { statement ->
            statement.bindEntityColumns(entity, skipIdColumn = true)
        }.value
        return generatedId
    }

    private fun updateEntity(id: Id, entity: IdentifiableEntityId): Id {
        table.update({ table.id eq entityId(id) }) { statement ->
            statement.bindEntityColumns(entity, skipIdColumn = true)
        }
        return id
    }

    private fun resolveSortColumn(name: String?): Column<*>? {
        if (name.isNullOrBlank()) return null
        return table.columns.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }
    }

    private fun UpdateBuilder<*>.bindEntityColumns(entity: IdentifiableEntityId, skipIdColumn: Boolean) {
        val propertiesByName = entity::class.memberProperties.associateBy { it.name }

        table.columns.forEach { column ->
            if (skipIdColumn && column == table.id) return@forEach

            val property = propertiesByName[column.name] ?: return@forEach
            property.isAccessible = true
            val value = property.getter.call(entity)

            if (value == null && !column.columnType.nullable) {
                return@forEach
            }

            @Suppress("UNCHECKED_CAST")
            this[column as Column<Any?>] = value
        }
    }

    private fun SortOrder.toExposed(): org.jetbrains.exposed.sql.SortOrder =
        when (this) {
            SortOrder.ASCENDING -> org.jetbrains.exposed.sql.SortOrder.ASC
            SortOrder.DESCENDING -> org.jetbrains.exposed.sql.SortOrder.DESC
        }
}
