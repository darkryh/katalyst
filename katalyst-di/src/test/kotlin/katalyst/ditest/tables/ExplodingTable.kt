package katalyst.ditest.tables

import io.github.darkryh.katalyst.core.persistence.EntityMapping
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.repositories.Identifiable
import org.jetbrains.exposed.v1.core.Table as ExposedTable

/**
 * Table fixtures for [io.github.darkryh.katalyst.di.internal.AutoBindingRegistrarTableDiscoveryLoggingTest].
 *
 * They live under the `katalyst.ditest` root, deliberately outside `io.github.darkryh.katalyst`, so
 * no other test's classpath scan can pick them up - [ExplodingTable] throws the moment it is
 * constructed.
 */
class ExplodingTableEntity(override val id: Long?) : Identifiable<Long>

/** A concrete Katalyst table whose constructor fails, exactly like a bad column declaration. */
class ExplodingTable : ExposedTable("exploding_table"), Table<Long, ExplodingTableEntity> {
    init {
        throw IllegalStateException("column 'name' is declared twice")
    }

    override val mapping: EntityMapping<Long, ExplodingTableEntity>
        get() = throw UnsupportedOperationException("never reached")
}

/** A shared abstract base is a normal way to declare tables and must stay routine (DEBUG). */
abstract class AbstractBaseTable : ExposedTable("abstract_base"), Table<Long, ExplodingTableEntity>
