package com.ead.katalyst.core.persistence

import com.ead.katalyst.repositories.Identifiable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/**
 * Katalyst table definition interface for Exposed-based tables with bi-directional
 * mapping between database rows and domain entities.
 *
 * Unlike generic ORM frameworks, Katalyst provides explicit mapping control:
 * - Developers write custom [mapRow] logic (no reflection-based inference)
 * - Developers write custom [assignEntity] logic for insert/update statements
 * - This ensures type-safety, performance, and explicit persistence semantics
 *
 * @param Id Primary key type (must be Comparable for sorting/queries)
 * @param Entity Domain entity type that MUST implement [Identifiable]
 *
 * **Contract:**
 * - [mapRow] MUST return an [Entity] instance
 * - [assignEntity] MUST populate all mutable entity fields into the statement
 * - Both operations are deterministic and must not throw unexpected exceptions
 * - Implementations should be stateless (thread-safe)
 */
interface Table<Id : Comparable<Id>, Entity : Identifiable<Id>> {

    /**
     * Maps the given [ResultRow] from Exposed into a domain [Entity].
     *
     * This method is the bridge from persistence layer to domain layer.
     * It constructs a fully-populated entity with all data from the row.
     *
     * **Mapping Success Indicators:**
     * - Non-null return value (entity construction succeeded)
     * - All required fields populated from ResultRow
     * - ID field extracted and set correctly
     *
     * @param row The ResultRow from Exposed query/fetch
     * @return Mapped entity instance, never null
     * @throws IllegalArgumentException if row data is malformed
     * @throws IllegalStateException if entity construction fails
     */
    fun mapRow(row: ResultRow): Entity

    /**
     * Populates the provided [UpdateBuilder] statement with values from [entity].
     *
     * This method is used for both INSERT and UPDATE operations. The [skipIdColumn]
     * flag allows the persistence layer to control whether the primary key is written:
     * - true: Skip ID (used in INSERT where PK is auto-generated or implicit)
     * - false: Include ID (used in UPDATE to ensure correct target row)
     *
     * **Persistence Success Indicators:**
     * - All entity fields written to statement without errors
     * - Statement ready for execution without modification
     * - ID handling respects the [skipIdColumn] flag
     *
     * @param statement The Exposed UpdateBuilder to populate
     * @param entity The domain entity whose values should be written
     * @param skipIdColumn When true, the primary key column is not written
     * @throws IllegalArgumentException if entity state is invalid
     * @throws IllegalStateException if statement cannot be populated
     */
    fun assignEntity(
        statement: UpdateBuilder<*>,
        entity: Entity,
        skipIdColumn: Boolean = true
    )
}
