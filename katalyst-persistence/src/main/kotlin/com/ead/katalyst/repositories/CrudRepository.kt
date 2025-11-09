package com.ead.katalyst.repositories

import com.ead.katalyst.repositories.model.PageInfo
import com.ead.katalyst.repositories.model.QueryFilter
import com.ead.katalyst.repositories.model.SortOrder
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.SortOrder as ExposedSortOder
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

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
 * entity mapping. Implementers supply a table reference and a mapper that converts
 * Exposed [ResultRow] objects into DOMAIN entities. All standard CRUD functions are
 * implemented automatically using those hints.
 *
 * **CRITICAL NAMING CONVENTION REQUIREMENT:**
 *
 * Column names in your table MUST exactly match entity property names when normalized.
 * The repository uses Kotlin reflection to automatically bind entity properties to
 * table columns by converting column names from any format (snake_case, kebab-case)
 * to camelCase and matching against property names.
 *
 * ✗ WRONG: Column "user_name" + Property "userName" but abbreviating as "user_nm"
 * ✓ CORRECT: Column "user_name" + Property "userName" (full property name)
 *
 * Supported naming conventions:
 * - snake_case: "created_at_millis" ↔ Property "createdAtMillis"
 * - kebab-case: "created-at-millis" ↔ Property "createdAtMillis"
 * - camelCase: "createdAtMillis" ↔ Property "createdAtMillis"
 *
 * **Usage Example:**
 * ```kotlin
 * object UsersTable : LongIdTable("users") {
 *     val name = varchar("name", 100)
 *     val email = varchar("email", 150)
 *     val createdAtMillis = long("created_at_millis")  // Full property name, not abbreviations
 * }
 *
 * data class UserEntity(
 *     override val id: Long? = null,
 *     val name: String,
 *     val email: String,
 *     val createdAtMillis: Long  // Must match "created_at_millis" when converted
 * ) : Identifiable<Long>
 *
 * class UserRepository : Repository<Long, UserEntity> {
 *     override val table = UsersTable
 *
 *     override fun mapper(row: ResultRow): UserEntity =
 *         UserEntity(
 *             id = row[table.id].value,
 *             name = row[table.name],
 *             email = row[table.email],
 *             createdAtMillis = row[table.createdAtMillis]
 *         )
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
     * Maps a database [ResultRow] to a domain [IdentifiableEntityId].
     */
    fun map(row: ResultRow): IdentifiableEntityId


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

    private fun UpdateBuilder<*>.bindEntityColumns(entity: IdentifiableEntityId, skipIdColumn: Boolean) {
        val propertiesByName = entity::class.memberProperties.associateBy { it.name }

        table.columns.forEach { column ->
            if (skipIdColumn && column == table.id) return@forEach

            // Try to find property matching the column name using multiple strategies:
            // 1. Direct name match (e.g., "email" -> "email")
            // 2. Column name in snake_case -> Property name in camelCase (e.g., "password_hash" -> "passwordHash")
            // 3. Column name in kebab-case -> Property name in camelCase (e.g., "password-hash" -> "passwordHash")
            val property = propertiesByName[column.name]
                ?: findPropertyByConvertedName(entity, column.name)
                ?: return@forEach

            property.isAccessible = true
            val value = property.getter.call(entity)

            if (value == null && !column.columnType.nullable) {
                return@forEach
            }

            // Handle EntityID reference columns: automatically wrap raw ID values in EntityID
            val valueToSet = if (value != null && column.columnType is EntityIDColumnType<*>) {
                createEntityIDForReferenceColumn(value, column.columnType as EntityIDColumnType<*>)
            } else {
                value
            }

            @Suppress("UNCHECKED_CAST")
            this[column as Column<Any?>] = valueToSet
        }
    }

    private fun findPropertyByConvertedName(entity: IdentifiableEntityId, columnName: String): kotlin.reflect.KProperty1<*, *>? {
        val convertedName = columnName.toPropertyName()
        return entity::class.memberProperties.firstOrNull { it.name == convertedName }
    }

    /**
     * Creates an EntityID instance for a reference column using Java reflection.
     * This bypasses Kotlin's type system to work around generic type constraints.
     */
    private fun createEntityIDForReferenceColumn(value: Any, columnType: EntityIDColumnType<*>): Any {
        return try {
            // Use reflection to access the idColumn field from EntityIDColumnType
            val idColumnField = columnType.javaClass.getDeclaredField("idColumn")
            idColumnField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val idColumn = idColumnField.get(columnType) as Column<*>

            // Get the referenced table from idColumn
            @Suppress("UNCHECKED_CAST")
            val referencedTable = idColumn.table as IdTable<Comparable<*>>

            // Use Java reflection to call the public constructor: EntityID(id: T, table: IdTable<T>)
            // This bypasses Kotlin's compile-time type checking
            val constructor = EntityID::class.java.getDeclaredConstructor(
                Comparable::class.java,
                IdTable::class.java
            )
            constructor.isAccessible = true

            val entityId = constructor.newInstance(value as Comparable<*>, referencedTable)
            entityId
        } catch (e: Exception) {
            e.printStackTrace()
            // Fall back to raw value (will likely cause a database error, but at least we logged it)
            value
        }
    }

    /**
     * Converts column names (snake_case, kebab-case) to camelCase for property matching.
     * Examples:
     * - "user_name" -> "userName"
     * - "user-name" -> "userName"
     * - "userName" -> "userName"
     */
    private fun String.toPropertyName(): String {
        return this
            .replace("-", "_")  // Normalize kebab-case to snake_case
            .split("_")
            .mapIndexed { index, part ->
                if (index == 0) part.lowercase() else part.replaceFirstChar { it.uppercase() }
            }
            .joinToString("")
    }

    /**
     * Ensures a string is in camelCase format.
     */
    private fun String.toCamelCase(): String {
        return this.toPropertyName()
    }

    private fun SortOrder.toExposed(): ExposedSortOder =
        when (this) {
            SortOrder.ASCENDING -> ExposedSortOder.ASC
            SortOrder.DESCENDING -> ExposedSortOder.DESC
        }
}
