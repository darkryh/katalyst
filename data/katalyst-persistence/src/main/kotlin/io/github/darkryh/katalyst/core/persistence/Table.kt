package io.github.darkryh.katalyst.core.persistence

import io.github.darkryh.katalyst.repositories.Identifiable

/**
 * Katalyst table definition interface for Exposed-based tables.
 *
 * Katalyst keeps persistence mapping explicit without exposing Exposed's
 * low-level statement assignment API to application tables.
 *
 * @param Id Primary key type (must be Comparable for sorting/queries)
 * @param Entity Domain entity type that MUST implement [Identifiable]
 *
 * **Contract:**
 * - [mapping] MUST define how rows become entities
 * - [mapping] MUST define how entities are written for insert/update
 * - Mapping operations are deterministic and must not throw unexpected exceptions
 * - Implementations should be stateless (thread-safe)
 */
interface Table<Id, Entity : Identifiable<Id>> where Id : Any, Id : Comparable<Id> {
    val mapping: EntityMapping<Id, Entity>
}
