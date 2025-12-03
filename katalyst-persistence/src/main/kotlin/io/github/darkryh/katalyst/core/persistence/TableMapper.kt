package io.github.darkryh.katalyst.core.persistence

import io.github.darkryh.katalyst.repositories.Identifiable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/**
 * Bi-directional mapper between domain entities and database persistence.
 *
 * This interface makes mapping operations explicit and discoverable in the code.
 * It provides a clearer semantic API for row-to-entity and entity-to-statement transformations.
 *
 * While [Table] is required for repository operations, [TableMapper] is an optional
 * enhancement for code clarity when you want to emphasize the mapping concerns explicitly.
 *
 * **Mapping Success Indicators:**
 * - [rowToEntity] returns a non-null entity with all required fields populated
 * - [entityToStatement] successfully populates the statement without errors
 * - Both operations are deterministic and thread-safe
 *
 * @param Id Primary key type (must be Comparable)
 * @param Entity The domain entity type that implements [Identifiable]
 *
 * **Usage Example:**
 * ```kotlin
 * object UserMapper : TableMapper<Long, UserEntity> {
 *     override fun rowToEntity(row: ResultRow): UserEntity {
 *         return UserEntity(
 *             id = row[UsersTable.id].value,
 *             email = row[UsersTable.email],
 *             name = row[UsersTable.name]
 *         )
 *     }
 *
 *     override fun entityToStatement(statement: UpdateBuilder<*>, entity: UserEntity, skipId: Boolean) {
 *         if (!skipId && entity.id != null) {
 *             statement[UsersTable.id] = EntityID(entity.id, UsersTable)
 *         }
 *         statement[UsersTable.email] = entity.email
 *         statement[UsersTable.name] = entity.name
 *     }
 * }
 * ```
 */
interface TableMapper<Id : Comparable<Id>, Entity : Identifiable<Id>> {

    /**
     * Maps a database row to a domain entity.
     *
     * This is the bridge from the persistence layer (raw SQL results) to the domain layer
     * (business logic entities). It should construct a fully-populated entity instance from
     * the values in the result row.
     *
     * **Mapping Success:**
     * - Returns a non-null Entity instance
     * - All required fields are extracted from the row
     * - ID field is correctly set (entity.id matches row[id])
     *
     * **Failure Cases:**
     * - Missing column in row → throws IllegalStateException or IllegalArgumentException
     * - Incompatible data types → throws ClassCastException (caught, should be handled)
     * - Null values when non-null expected → throws IllegalStateException
     *
     * @param row The ResultRow from an Exposed query
     * @return A fully-populated Entity instance, never null
     * @throws IllegalArgumentException if row data is malformed
     * @throws IllegalStateException if entity construction fails
     */
    fun rowToEntity(row: ResultRow): Entity

    /**
     * Maps a domain entity to a database persistence statement.
     *
     * This is the bridge from the domain layer (business logic entities) to the
     * persistence layer (INSERT/UPDATE statements). It should populate the statement
     * with all mutable entity fields according to the [skipId] flag.
     *
     * **Persistence Success:**
     * - All entity fields written to statement without errors
     * - Statement is ready for execution
     * - ID handling respects the [skipId] parameter
     *
     * **Parameter Details:**
     * - [skipId] = true: Used in INSERT operations, skip the primary key
     * - [skipId] = false: Used in UPDATE operations, include the primary key for targeting
     *
     * @param statement The UpdateBuilder from Exposed (can be insert or update)
     * @param entity The domain entity whose values should be written
     * @param skipId When true, the primary key column is not written (default: true for inserts)
     * @throws IllegalArgumentException if entity state is invalid
     * @throws IllegalStateException if statement cannot be populated
     */
    fun entityToStatement(
        statement: UpdateBuilder<*>,
        entity: Entity,
        skipId: Boolean = true
    )
}
