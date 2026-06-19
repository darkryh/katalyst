# Persistence reference

Exposed 1.3.x (JDBC, v1 API) + HikariCP. Three layers: entity (`Identifiable`), table
(`Table` + `mapping`), repository (`CrudRepository`). All Exposed imports use
`org.jetbrains.exposed.v1.*`.

## The Identifiable contract (do not skip)

Entities used with `Table`/`CrudRepository` MUST implement `Identifiable<Id>`:

```kotlin
import io.github.darkryh.katalyst.repositories.Identifiable

data class Bookmark(
    override val id: Long? = null,     // nullable; null before insert
    val url: String,
    val createdAtMillis: Long
) : Identifiable<Long>
```

`Identifiable<Id> where Id : Any, Id : Comparable<Id>` declares `val id: Id?`. `Table<Id, Entity>`
and `CrudRepository<Id, Entity>` both bound `Entity : Identifiable<Id>`. Forgetting this is the
most common persistence compile error.

## Table

An Exposed `IdTable` (commonly `LongIdTable`) that also implements `Table<Id, Entity>` and
provides a `mapping`:

```kotlin
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object BookmarksTable : LongIdTable("bookmarks"), Table<Long, Bookmark> {
    val url = varchar("url", 2048)
    val createdAtMillis = long("created_at_millis")

    override val mapping = mapping<Long, Bookmark> {
        generatedId(id, Bookmark::id)
        field(url, Bookmark::url)
        field(createdAtMillis, Bookmark::createdAtMillis)
        construct {
            Bookmark(id = this[id], url = this[url], createdAtMillis = this[createdAtMillis])
        }
    }
}
```

### The mapping DSL (complete)

`mapping<Id, Entity> { ... }` builds an `EntityMapping`. Builder functions:

| Function | Use |
|----------|-----|
| `generatedId(column, property)` | Auto-generated primary key (DB assigns it). |
| `assignedId(column, property)` | Primary key you assign yourself. |
| `field(column, property)` | Map a column ↔ property both ways. |
| `reference(column, property)` | Foreign-key reference column (non-null). |
| `nullableReference(column, property)` | Nullable foreign-key reference. |
| `enumName(column, property)` | Enum stored by name. |
| `nullableEnumName(column, property)` | Nullable enum by name. |
| `instant(column, property)` | `java.time.Instant` column. |
| `nullableInstant(column, property)` | Nullable `Instant`. |
| `custom(column, property, encode, decode)` | Arbitrary column ↔ value conversion. |
| `writeOnly(column, value)` | A column written but not read into the entity. |
| `construct { ... }` | Build the entity from a `MappedRow` via `this[column]`. |

Inside `construct { }`, `this[column]` reads a value; for ID/reference columns it returns the
raw `Id` (the layer unwraps `EntityID` for you).

### Foreign keys

```kotlin
import org.jetbrains.exposed.v1.core.ReferenceOption

object UserProfilesTable : LongIdTable("user_profiles"), Table<Long, UserProfile> {
    val accountId = reference("account_id", AuthAccountsTable, onDelete = ReferenceOption.CASCADE)
    val displayName = varchar("display_name", 120)
    override val mapping = mapping<Long, UserProfile> {
        generatedId(id, UserProfile::id)
        reference(accountId, UserProfile::accountId)   // store raw Long in the entity
        field(displayName, UserProfile::displayName)
        construct {
            UserProfile(id = this[id], accountId = this[accountId], displayName = this[displayName])
        }
    }
}
```

`ReferenceOption` values: `CASCADE`, `SET_NULL`, `RESTRICT`, `NO_ACTION`, `SET_DEFAULT`.

## CrudRepository

```kotlin
interface CrudRepository<Id, Entity : Identifiable<Id>> where Id : Any, Id : Comparable<Id> {
    val table: IdTable<Id>
    fun map(row: ResultRow): Entity            // provided via the table mapping
    fun save(entity: Entity): Entity           // insert if id == null, else update
    fun findById(id: Id): Entity?
    fun findAll(): List<Entity>
    fun deleteById(id: Id): Boolean
    // plus count/exists-style helpers
}
```

Implement it by pointing `table` at your table:

```kotlin
import io.github.darkryh.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

class BookmarkRepository : CrudRepository<Long, Bookmark> {
    override val table: LongIdTable = BookmarksTable
}
```

`save` inserts when `entity.id == null` and updates otherwise. Repositories are discovered and
injected by type — never instantiate them yourself.

### Custom queries

Use the Exposed DSL and `map(row)` to convert results. Required imports:

```kotlin
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class BookmarkRepository : CrudRepository<Long, Bookmark> {
    override val table: LongIdTable = BookmarksTable

    fun findByUrl(url: String): Bookmark? =
        BookmarksTable.selectAll().where { BookmarksTable.url eq url }
            .limit(1).firstOrNull()?.let(::map)
}
```

Query operators (from `org.jetbrains.exposed.v1.core`): `eq`, `neq`, `less`, `lessEq`,
`greater`, `greaterEq`, `inList`, `notInList`, `like`, `isNull`, `isNotNull`, `between`, `and`,
`or`. `selectAll`, `SortOrder`, `orderBy`, `limit`, `count` from `org.jetbrains.exposed.v1.jdbc`.

When comparing a reference column directly in a custom predicate, wrap the value in `EntityID`:

```kotlin
.where { UserProfilesTable.accountId eq EntityID(accountId, AuthAccountsTable) }
```

(Only in custom predicates — `mapping` and `save` use raw ids.)

## Transactions in services

Wrap repository calls so writes and any published events commit atomically:

```kotlin
import io.github.darkryh.katalyst.core.component.Service   // Service/Component live here

class BookmarkService(private val repository: BookmarkRepository) : Service {
    suspend fun add(url: String) = transactionManager.transaction {
        repository.save(Bookmark(url = url, createdAtMillis = System.currentTimeMillis()))
    }
}
```

For migrations and one-off operations, use Exposed's transaction against the injected
`DatabaseFactory.database`:

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
transaction(databaseFactory.database) { exec("UPDATE ...") }
```

See `references/transactions.md`.

## SqlExecutor — managed raw SQL

For bootstrap DDL or SQL Exposed doesn't cover. Inject `SqlExecutor`
(`io.github.darkryh.katalyst.database.SqlExecutor`):

```kotlin
interface SqlExecutor {
    fun executeUpdate(sql: String, params: List<Any?> = emptyList()): Int
    fun <T> query(sql: String, params: List<Any?> = emptyList(), map: (ResultSet) -> T): List<T>
    fun <T> queryOne(sql: String, params: List<Any?> = emptyList(), map: (ResultSet) -> T): T?
    fun executeBatch(statements: List<String>)
}
```

```kotlin
class SchemaBootstrap(private val sqlExecutor: SqlExecutor) : Service {
    suspend fun prepare() = sqlExecutor.executeBatch(listOf(
        "CREATE TABLE IF NOT EXISTS feature_flags (key VARCHAR(80) PRIMARY KEY, enabled BOOLEAN)"
    ))
}
```

Reuses the active transaction's connection inside one; uses pooled connections with
commit/rollback otherwise. Failures wrap in `SqlExecutionException`.

## DatabaseFactory and DatabaseConfig

`DatabaseFactory` (`io.github.darkryh.katalyst.database.DatabaseFactory`) owns the Exposed
`Database`, the Hikari pool, lifecycle, and schema policy. Injected automatically; `.database`
gives the Exposed handle for migrations. `DatabasePoolSnapshot` exposes pool metrics.

`DatabaseConfig` (`io.github.darkryh.katalyst.config.DatabaseConfig`) fields: `url`, `driver`,
`username`, `password`, `maxPoolSize`, `minIdleConnections`, `connectionTimeout`, `idleTimeout`,
`maxLifetime`, `autoCommit`, `transactionIsolation`.

## Undo strategies (advanced)

`katalyst-persistence` ships a compensation toolkit for reversible operations:
`UndoStrategy`, `UndoStrategyRegistry`, `SimpleUndoEngine`, `EnhancedUndoEngine`, built-in
`InsertUndoStrategy` / `UpdateUndoStrategy` / `DeleteUndoStrategy` / `APICallUndoStrategy`, and
`RetryPolicy`. Use for workflow-style operations that need compensating actions. Most apps do
not need these.

## Common errors

| Error | Fix |
|-------|-----|
| `Cannot resolve symbol 'selectAll'` | `import org.jetbrains.exposed.v1.jdbc.selectAll` |
| Entity rejected as `Table`/`CrudRepository` type arg | Implement `Identifiable<Id>`, add `override val id: Id? = null` |
| "No database initialized" using bare `transaction { }` | Use `transactionManager.transaction { }` (services) or `transaction(databaseFactory.database) { }` (migrations) |
| Reference predicate type mismatch | Wrap with `EntityID(value, ReferencedTable)` in custom queries |
| Table not created | Schema policy is `validateOnStartup()`/`none()` — use `createMissing()` locally or add a migration |
