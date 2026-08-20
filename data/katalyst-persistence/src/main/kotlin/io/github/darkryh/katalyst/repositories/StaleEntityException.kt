package io.github.darkryh.katalyst.repositories

/**
 * Raised by [CrudRepository.save] when an entity that already carries a primary key
 * cannot be updated because no row with that key exists.
 *
 * `save` resolves an entity with a non-null [Identifiable.id] to an `UPDATE`. When that
 * statement matches zero rows the row is gone: it was deleted (possibly by a concurrent
 * transaction that raced this save) or the identifier was never persisted at all.
 * Katalyst refuses to re-create the row under its old primary key, because doing so
 * would silently undo the delete and hand the caller a result indistinguishable from a
 * successful update.
 *
 * Tables that own their identifiers - mappings declared with
 * [io.github.darkryh.katalyst.core.persistence.EntityMappingBuilder.assignedId] - never
 * raise this: for them a non-null id carries no claim that the row already exists, so
 * `save` inserts instead. It is only raised for
 * [io.github.darkryh.katalyst.core.persistence.EntityMappingBuilder.generatedId]
 * mappings, where a non-null id can only have come from a row that the database had
 * already created.
 *
 * @property tableName Name of the table whose row could not be updated.
 * @property id Primary key that matched no row.
 */
class StaleEntityException(
    val tableName: String,
    val id: Any
) : RuntimeException(
    "Cannot save entity with id=$id: table '$tableName' has no row with that id, so the UPDATE " +
        "matched 0 rows. The row was deleted - possibly by a concurrent transaction - or the id was " +
        "never persisted. Katalyst will not re-create it under the same primary key, because that " +
        "would silently undo the delete. Re-read the entity before saving it, save it with a null id " +
        "to insert a new row, or declare assignedId(...) in the table mapping if this table owns its " +
        "primary keys and save() should upsert."
)
