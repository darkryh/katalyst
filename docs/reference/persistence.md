# Persistence

Katalyst's persistence layer (`katalyst-persistence`) wraps Exposed 1.3.x and HikariCP. You
define tables and repositories; Katalyst manages the connection pool, discovers your types,
and injects repositories. This page documents the public surface. For a task walkthrough, see
[Define tables and repositories](../how-to/define-tables-and-repositories.md).

Use the `org.jetbrains.exposed.v1.*` import set (the v1 JDBC API) throughout.

## Identifiable

Entities used with `Table` and `CrudRepository` must implement `Identifiable<Id>`. This is the
contract that tells the framework where the primary key lives, so it is part of both type
bounds — not optional.

```kotlin
interface Identifiable<Id> where Id : Any, Id : Comparable<Id> {
    val id: Id?
}
```

```kotlin
import io.github.darkryh.katalyst.repositories.Identifiable

data class AuthAccount(
    override val id: Long? = null,   // null before insert; save() uses this to insert vs update
    val email: String,
    val status: String = "active"
) : Identifiable<Long>
```

## Table

```kotlin
interface Table<Id, Entity : Identifiable<Id>> where Id : Any, Id : Comparable<Id> {
    val mapping: EntityMapping<Id, Entity>
}
```

A table is an Exposed `IdTable` (commonly `LongIdTable`) that also implements
`Table<Id, Entity>`. The `mapping` property tells Katalyst how to read rows and write inserts
and updates.

### mapping DSL

`mapping<Id, Entity> { … }` builds an `EntityMapping`. Inside the block:

| Call | Purpose |
|------|---------|
| `generatedId(column, property)` | Map an auto-generated primary key. |
| `field(column, property)` | Map a column to a property in both directions. |
| `reference(column, property)` | Map a foreign-key reference column. |
| `construct { … }` | Build an entity from a `MappedRow` (`this[column]`). |

```kotlin
object AuthAccountsTable : LongIdTable("auth_accounts"), Table<Long, AuthAccount> {
    val email = varchar("email", 150).uniqueIndex()
    val status = varchar("status", 32).default("active")

    override val mapping = mapping<Long, AuthAccount> {
        generatedId(id, AuthAccount::id)
        field(email, AuthAccount::email)
        field(status, AuthAccount::status)
        construct { AuthAccount(id = this[id], email = this[email], status = this[status]) }
    }
}
```

Mappings use raw ID values; the layer delegates to Exposed's typed setters internally, so you
do not wrap inserts/updates in `EntityID`. Custom query predicates that compare a reference
column directly may still need `EntityID`.

## CrudRepository

```kotlin
interface CrudRepository<Id, Entity : Identifiable<Id>> where Id : Any, Id : Comparable<Id> {
    val table: IdTable<Id>
    // inherited helpers: save (insert if id == null, else update),
    // findById, findAll, deleteById, map, …
}
```

Implement it and point `table` at your table. Repositories are discovered under scanned
packages and injected by type. Add custom queries with the Exposed DSL and `map(row)` to
convert results:

```kotlin
class AuthAccountRepository : CrudRepository<Long, AuthAccount> {
    override val table: LongIdTable = AuthAccountsTable

    fun findByEmail(email: String): AuthAccount? =
        AuthAccountsTable.selectAll().where { AuthAccountsTable.email eq email }
            .limit(1).firstOrNull()?.let(::map)
}
```

Supporting model types: `PageInfo`, `QueryFilter`, `SortOrder` (see [Identifiable](#identifiable)
for the entity contract).

## Query operators

The Exposed DSL operators used in `where { … }` clauses:

| Operator | Meaning | Example |
|----------|---------|---------|
| `eq` | Equals | `name eq "John"` |
| `neq` | Not equals | `status neq "disabled"` |
| `less`, `lessEq` | `<`, `<=` | `age less 18` |
| `greater`, `greaterEq` | `>`, `>=` | `score greater 100` |
| `inList` | `IN` | `status inList listOf("active", "pending")` |
| `notInList` | `NOT IN` | `status notInList listOf("deleted")` |
| `like` | `LIKE` | `email like "%@example.com"` |
| `isNull`, `isNotNull` | Null checks | `deletedAt.isNull()` |
| `between` | `BETWEEN` | `createdAt between (start to end)` |
| `and`, `or` | Combine conditions | `(a eq 1) and (b eq 2)` |

Common imports:

```kotlin
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
```

## SqlExecutor

A managed low-level JDBC API for bootstrap DDL and custom SQL that Exposed does not cover.
Inject it like any dependency.

```kotlin
interface SqlExecutor {
    fun executeUpdate(sql: String, params: List<Any?> = emptyList()): Int
    fun <T> query(sql: String, params: List<Any?> = emptyList(), map: (ResultSet) -> T): List<T>
    fun <T> queryOne(sql: String, params: List<Any?> = emptyList(), map: (ResultSet) -> T): T?
    fun executeBatch(statements: List<String>)
}
```

```kotlin
val affected = sqlExecutor.executeUpdate(
    "UPDATE auth_accounts SET status = ? WHERE id = ?",
    listOf("active", accountId)
)

val emails = sqlExecutor.query(
    "SELECT email FROM auth_accounts WHERE status = ?",
    listOf("active")
) { row -> row.getString("email") }
```

Behavior:

- Uses the Katalyst-managed datasource and pool — no raw `DriverManager`.
- Reuses the active Exposed transaction's connection when called inside one.
- Outside a transaction, runs with pooled connections and commit/rollback handling.
- Wraps low-level failures in `SqlExecutionException`.

## DatabaseFactory

Manages the Exposed `Database` instance, the HikariCP pool, lifecycle, and the schema policy.
Injected automatically; accessible in migrations and advanced scenarios.

```kotlin
class MyMigration(private val databaseFactory: DatabaseFactory) : KatalystMigration {
    override val id = "001_fix"
    override fun up() {
        transaction(databaseFactory.database) { exec("SELECT 1") }
    }
}
```

`DatabasePoolSnapshot` exposes pool metrics for monitoring.

## DatabaseConfig

The programmatic database configuration passed to `database(DatabaseConfig(...))` or built by
`database { fromConfiguration() }`.

| Field | Type | Notes |
|-------|------|-------|
| `url` | `String` | JDBC URL. |
| `driver` | `String` | JDBC driver class. |
| `username` | `String` | |
| `password` | `String` | |
| `maxPoolSize` | `Int` | Hikari max pool size. |
| `minIdleConnections` | `Int` | Hikari minimum idle. |
| `connectionTimeout` | `Long` (ms) | |
| `idleTimeout` | `Long` (ms) | |
| `maxLifetime` | `Long` (ms) | |
| `autoCommit` | `Boolean` | |
| `transactionIsolation` | `String` | Isolation level name. |

## Undo strategies

`katalyst-persistence` also ships an undo/compensation toolkit for reversible operations:
`UndoStrategy`, `UndoStrategyRegistry`, `SimpleUndoEngine`, `EnhancedUndoEngine`, the
built-in `InsertUndoStrategy` / `UpdateUndoStrategy` / `DeleteUndoStrategy` /
`APICallUndoStrategy`, and a `RetryPolicy`. These support building compensating actions for
workflow-style operations.

## See also

- [Define tables and repositories](../how-to/define-tables-and-repositories.md)
- [Transactions](transactions.md) — wrapping repository calls transactionally.
- [Migrations](migrations.md) — evolving the schema over time.

