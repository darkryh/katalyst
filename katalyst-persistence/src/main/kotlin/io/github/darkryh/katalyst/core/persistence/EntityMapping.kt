package io.github.darkryh.katalyst.core.persistence

import io.github.darkryh.katalyst.core.dsl.KatalystDslMarker
import io.github.darkryh.katalyst.repositories.Identifiable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import java.time.Instant
import kotlin.jvm.JvmName
import kotlin.reflect.KProperty1
import org.jetbrains.exposed.v1.core.Table as ExposedTable

/**
 * Bidirectional persistence mapping for a Katalyst table.
 *
 * The mapping owns row construction and statement assignment while keeping
 * Exposed's low-level [UpdateBuilder] API out of user table definitions.
 */
interface EntityMapping<Id, Entity : Identifiable<Id>> where Id : Any, Id : Comparable<Id> {
    fun read(row: ResultRow): Entity

    fun validate(table: ExposedTable)
}

internal interface WritableEntityMapping<Id, Entity : Identifiable<Id>> : EntityMapping<Id, Entity>
    where Id : Any, Id : Comparable<Id> {
    fun writeInsert(
        statement: UpdateBuilder<*>,
        entity: Entity
    )

    fun writeUpdate(
        statement: UpdateBuilder<*>,
        entity: Entity
    )
}

/**
 * Typed row facade used by mapping constructors.
 *
 * Reference and id columns return their raw ID values instead of Exposed's
 * [EntityID] wrapper.
 */
@KatalystDslMarker
class MappedRow internal constructor(
    private val row: ResultRow
) {
    @JvmName("getEntityIdValue")
    operator fun <Id : Any> get(column: Column<EntityID<Id>>): Id =
        row[column].value

    @JvmName("getNullableEntityIdValue")
    operator fun <Id : Any> get(column: Column<EntityID<Id>?>): Id? =
        row[column]?.value

    @JvmName("getColumnValue")
    operator fun <Value> get(column: Column<Value>): Value =
        row[column]

    fun instant(column: Column<Instant>): Instant =
        row[column]

    fun nullableInstant(column: Column<Instant?>): Instant? =
        row[column]
}

/**
 * Creates an [EntityMapping] using Katalyst's explicit mapping DSL.
 */
fun <Id, Entity : Identifiable<Id>> mapping(
    block: EntityMappingBuilder<Id, Entity>.() -> Unit
): EntityMapping<Id, Entity> where Id : Any, Id : Comparable<Id> =
    EntityMappingBuilder<Id, Entity>().apply(block).build()

@KatalystDslMarker
class EntityMappingBuilder<Id, Entity : Identifiable<Id>> where Id : Any, Id : Comparable<Id> {
    private val bindings = mutableListOf<WriteBinding<Entity>>()
    private var constructor: (MappedRow.() -> Entity)? = null
    private var idConfigured: Boolean = false

    fun generatedId(
        column: Column<EntityID<Id>>,
        property: KProperty1<Entity, Id?>
    ) {
        idConfigured = true
        bindings += GeneratedIdBinding(column, property)
    }

    fun assignedId(
        column: Column<EntityID<Id>>,
        property: KProperty1<Entity, Id>
    ) {
        idConfigured = true
        bindings += AssignedIdBinding(column, property)
    }

    fun <Value> field(
        column: Column<Value>,
        property: KProperty1<Entity, Value>
    ) {
        bindings += FieldBinding(column, property)
    }

    fun <EnumValue> enumName(
        column: Column<String>,
        property: KProperty1<Entity, EnumValue>
    ) where EnumValue : Enum<EnumValue> {
        bindings += CustomBinding(column, property) { it.name }
    }

    fun <EnumValue> nullableEnumName(
        column: Column<String?>,
        property: KProperty1<Entity, EnumValue?>
    ) where EnumValue : Enum<EnumValue> {
        bindings += CustomBinding(column, property) { it?.name }
    }

    fun instant(
        column: Column<Instant>,
        property: KProperty1<Entity, Instant>
    ) {
        bindings += FieldBinding(column, property)
    }

    fun nullableInstant(
        column: Column<Instant?>,
        property: KProperty1<Entity, Instant?>
    ) {
        bindings += FieldBinding(column, property)
    }

    fun <ReferenceId : Any> reference(
        column: Column<EntityID<ReferenceId>>,
        property: KProperty1<Entity, ReferenceId>
    ) {
        bindings += ReferenceBinding(column, property)
    }

    fun <ReferenceId : Any> nullableReference(
        column: Column<EntityID<ReferenceId>?>,
        property: KProperty1<Entity, ReferenceId?>
    ) {
        bindings += NullableReferenceBinding(column, property)
    }

    fun <ColumnValue, EntityValue> custom(
        column: Column<ColumnValue>,
        property: KProperty1<Entity, EntityValue>,
        write: (EntityValue) -> ColumnValue
    ) {
        bindings += CustomBinding(column, property, write)
    }

    fun <Value> writeOnly(
        column: Column<Value>,
        value: (Entity) -> Value
    ) {
        bindings += WriteOnlyBinding(column, value)
    }

    fun construct(constructor: MappedRow.() -> Entity) {
        this.constructor = constructor
    }

    internal fun build(): EntityMapping<Id, Entity> {
        val constructor = requireNotNull(constructor) {
            "Entity mapping must declare construct { ... }"
        }
        return DefaultEntityMapping(
            constructor = constructor,
            bindings = bindings.toList(),
            idConfigured = idConfigured
        )
    }
}

private class DefaultEntityMapping<Id, Entity : Identifiable<Id>>(
    private val constructor: MappedRow.() -> Entity,
    private val bindings: List<WriteBinding<Entity>>,
    private val idConfigured: Boolean
) : WritableEntityMapping<Id, Entity> where Id : Any, Id : Comparable<Id> {
    override fun read(row: ResultRow): Entity =
        MappedRow(row).constructor()

    override fun writeInsert(statement: UpdateBuilder<*>, entity: Entity) {
        bindings.forEach { it.writeInsert(statement, entity) }
    }

    override fun writeUpdate(statement: UpdateBuilder<*>, entity: Entity) {
        bindings.forEach { it.writeUpdate(statement, entity) }
    }

    override fun validate(table: ExposedTable) {
        require(idConfigured) {
            "Table ${table.tableName} mapping must declare generatedId(...) or assignedId(...)"
        }

        val duplicateColumns = bindings
            .groupBy { it.column }
            .filterValues { it.size > 1 }
            .keys
        require(duplicateColumns.isEmpty()) {
            "Table ${table.tableName} mapping has duplicate write bindings for columns: " +
                duplicateColumns.joinToString { it.name }
        }

        val boundColumns = bindings.map { it.column }.toSet()
        val missingRequiredColumns = table.columns.filter { column ->
            column !in boundColumns &&
                !column.columnType.nullable &&
                column.defaultValueFun == null &&
                column.defaultValueInDb() == null &&
                !column.isDatabaseGenerated() &&
                table.autoIncColumn != column
        }

        require(missingRequiredColumns.isEmpty()) {
            "Table ${table.tableName} mapping is missing required write bindings for columns: " +
                missingRequiredColumns.joinToString { it.name }
        }

        bindings.forEach { it.validate(table) }
    }
}

private interface WriteBinding<Entity> {
    val column: Column<*>

    fun writeInsert(statement: UpdateBuilder<*>, entity: Entity)

    fun writeUpdate(statement: UpdateBuilder<*>, entity: Entity)

    fun validate(table: ExposedTable) = Unit
}

private class GeneratedIdBinding<Id : Any, Entity>(
    override val column: Column<EntityID<Id>>,
    private val property: KProperty1<Entity, Id?>
) : WriteBinding<Entity> {
    override fun writeInsert(statement: UpdateBuilder<*>, entity: Entity) {
        property.get(entity)?.let { statement[column] = it }
    }

    override fun writeUpdate(statement: UpdateBuilder<*>, entity: Entity) = Unit
}

private class AssignedIdBinding<Id : Any, Entity>(
    override val column: Column<EntityID<Id>>,
    private val property: KProperty1<Entity, Id>
) : WriteBinding<Entity> {
    override fun writeInsert(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = property.get(entity)
    }

    override fun writeUpdate(statement: UpdateBuilder<*>, entity: Entity) = Unit
}

private class FieldBinding<Value, Entity>(
    override val column: Column<Value>,
    private val property: KProperty1<Entity, Value>
) : WriteBinding<Entity> {
    override fun writeInsert(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = property.get(entity)
    }

    override fun writeUpdate(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = property.get(entity)
    }
}

private class ReferenceBinding<ReferenceId : Any, Entity>(
    override val column: Column<EntityID<ReferenceId>>,
    private val property: KProperty1<Entity, ReferenceId>
) : WriteBinding<Entity> {
    override fun writeInsert(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = property.get(entity)
    }

    override fun writeUpdate(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = property.get(entity)
    }

    override fun validate(table: ExposedTable) {
        require(column.foreignKey?.targetTable is IdTable<*>) {
            "Table ${table.tableName} reference binding for column ${column.name} must target an Exposed IdTable"
        }
    }
}

private class NullableReferenceBinding<ReferenceId : Any, Entity>(
    override val column: Column<EntityID<ReferenceId>?>,
    private val property: KProperty1<Entity, ReferenceId?>
) : WriteBinding<Entity> {
    override fun writeInsert(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = property.get(entity)
    }

    override fun writeUpdate(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = property.get(entity)
    }

    override fun validate(table: ExposedTable) {
        require(column.foreignKey?.targetTable is IdTable<*>) {
            "Table ${table.tableName} nullable reference binding for column ${column.name} must target an Exposed IdTable"
        }
    }
}

private class CustomBinding<ColumnValue, EntityValue, Entity>(
    override val column: Column<ColumnValue>,
    private val property: KProperty1<Entity, EntityValue>,
    private val write: (EntityValue) -> ColumnValue
) : WriteBinding<Entity> {
    override fun writeInsert(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = write(property.get(entity))
    }

    override fun writeUpdate(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = write(property.get(entity))
    }
}

private class WriteOnlyBinding<Value, Entity>(
    override val column: Column<Value>,
    private val value: (Entity) -> Value
) : WriteBinding<Entity> {
    override fun writeInsert(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = value(entity)
    }

    override fun writeUpdate(statement: UpdateBuilder<*>, entity: Entity) {
        statement[column] = value(entity)
    }
}
