# Exposed Database Framework Integration

Katalyst uses **Exposed 1.3.0** with JDBC transaction support for type-safe database operations. This guide details the imports, transaction patterns, and best practices for working with Exposed in Katalyst.

## Version Information

- **Exposed Version**: 1.3.0
- **Transaction Driver**: JDBC (via `org.jetbrains.exposed.v1.jdbc.transactions.transaction`)
- **Primary API**: JDBC DSL (not DAO/Entity classes)
- **Table Inheritance**: `LongIdTable` for entity ID management

## Core Imports Reference

### Table Definition Imports

When defining tables that implement `Table<Id, Entity>`, use these imports:

```kotlin
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
```

**Key Classes:**
- `LongIdTable` - Base table class for auto-incremented Long IDs
- `mapping { ... }` - Katalyst DSL for row construction and insert/update bindings

### Repository Query Imports

For custom queries in repositories:

```kotlin
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
```

**Key Functions:**
- `selectAll()` - Creates SELECT * query
- `eq` - Equality operator for WHERE clauses
- `LongIdTable` - Type annotation for table properties

### Transaction Imports

For explicit transaction control (less common—prefer `transactionManager.transaction { }`):

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
```

**Usage Pattern:**
```kotlin
transaction(databaseFactory.database) {
    exec("UPDATE table SET column = value")
}
```

### Advanced Query Imports

For complex WHERE clauses, joins, or aggregations:

```kotlin
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.eq
```

**Key Operators:**
- `and` - Combines multiple WHERE conditions with AND logic
- `ReferenceOption` - Foreign key cascade/delete options (CASCADE, SET_NULL, RESTRICT, etc.)

## Complete Example: Table with Foreign Key

```kotlin
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object UserProfilesTable : LongIdTable("user_profiles"), Table<Long, UserProfileEntity> {
    val accountId = reference(
        name = "account_id",
        foreign = AuthAccountsTable,
        onDelete = ReferenceOption.CASCADE
    )
    val displayName = varchar("display_name", 120)
    val bio = text("bio").nullable()
    val createdAtMillis = long("created_at_millis")

    override val mapping = mapping<Long, UserProfileEntity> {
        generatedId(id, UserProfileEntity::id)
        reference(accountId, UserProfileEntity::accountId)
        field(displayName, UserProfileEntity::displayName)
        field(bio, UserProfileEntity::bio)
        field(createdAtMillis, UserProfileEntity::createdAtMillis)

        construct {
            UserProfileEntity(
                id = this[id],
                accountId = this[accountId],
                displayName = this[displayName],
                bio = this[bio],
                createdAtMillis = this[createdAtMillis]
            )
        }
    }
}
```

## Complete Example: Repository with Custom Queries

```kotlin
import io.github.darkryh.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class UserProfileRepository : CrudRepository<Long, UserProfileEntity> {
    override val table: LongIdTable = UserProfilesTable

    fun findByAccountId(accountId: Long): UserProfileEntity? =
        UserProfilesTable
            .selectAll()
            .where { UserProfilesTable.accountId eq EntityID(accountId, AuthAccountsTable) }
            .limit(1)
            .firstOrNull()
            ?.let(::map)

    fun findActiveProfiles(): List<UserProfileEntity> =
        UserProfilesTable
            .selectAll()
            .where { 
                (UserProfilesTable.displayName.isNotNull()) and
                (UserProfilesTable.createdAtMillis lessEq System.currentTimeMillis())
            }
            .map(::map)
}
```

## Transaction Patterns in Exposed

### Pattern 1: Using Katalyst's transactionManager (Recommended)

Katalyst's `DatabaseTransactionManager` wraps Exposed transactions and ensures event consistency:

```kotlin
import io.github.darkryh.katalyst.core.component.Service

class UserService(
    private val repository: UserProfileRepository
) : Service {
    suspend fun updateProfile(id: Long, displayName: String): UserProfile =
        transactionManager.transaction {
            val profile = repository.findById(id)
            repository.save(profile.copy(displayName = displayName))
        }
}
```

**Benefits:**
- Events published inside the transaction are deferred until commit
- Automatic rollback on exception discards all changes
- Suspendable for async/await compatibility

### Pattern 2: Direct Exposed Transaction (For Migrations)

For one-time operations like migrations, use Exposed's `transaction` directly:

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class MyMigration(private val databaseFactory: DatabaseFactory) : KatalystMigration {
    override fun up() {
        transaction(databaseFactory.database) {
            exec("""
                UPDATE auth_accounts
                SET status = 'active'
                WHERE status IS NULL
            """.trimIndent())
        }
    }
}
```

**When to use:**
- Schema migrations that run at startup
- Batch operations outside request/event contexts
- Data fixes that don't interact with domain events

## SQL DSL Reference

### Common Query Operations

```kotlin
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

// SELECT with WHERE
UserProfilesTable
    .selectAll()
    .where { UserProfilesTable.displayName eq "Alice" }

// SELECT with AND
UserProfilesTable
    .selectAll()
    .where { 
        (UserProfilesTable.createdAtMillis lessEq System.currentTimeMillis()) and
        (UserProfilesTable.bio.isNotNull())
    }

// SELECT with LIMIT/ORDER
UserProfilesTable
    .selectAll()
    .orderBy(UserProfilesTable.createdAtMillis to SortOrder.DESC)
    .limit(10)

// COUNT
UserProfilesTable.selectAll().count()

// Custom SQL (for complex queries)
exec("SELECT * FROM user_profiles WHERE status = 'active'")
```

### Operators Reference

| Operator | Usage | Example |
|---|---|---|
| `eq` | Equals | `name eq "John"` |
| `neq` | Not equals | `status neq "disabled"` |
| `less`, `lessEq` | Less than / less than or equal | `age less 18`, `age lessEq 21` |
| `greater`, `greaterEq` | Greater than / greater than or equal | `score greater 100` |
| `inList` | IN clause | `status inList listOf("active", "pending")` |
| `notInList` | NOT IN clause | `status notInList listOf("deleted")` |
| `like` | LIKE pattern | `email like "%@example.com"` |
| `isNull` | IS NULL | `deletedAtMillis.isNull()` |
| `isNotNull` | IS NOT NULL | `deletedAtMillis.isNotNull()` |
| `between` | BETWEEN range | `createdAtMillis between (start to end)` |
| `and` | AND condition | `(status eq "active") and (age greater 18)` |
| `or` | OR condition | `(status eq "active") or (status eq "pending")` |

## Important Notes on Entity IDs

Katalyst table mappings use raw ID values. The mapping layer delegates to Exposed's typed setters internally, so application table definitions do not create `EntityID` wrappers for inserts or updates.

```kotlin
generatedId(id, UserProfileEntity::id)
reference(accountId, UserProfileEntity::accountId)

construct {
    UserProfileEntity(
        id = this[id],
        accountId = this[accountId],
        displayName = this[displayName]
    )
}
```

Custom Exposed query predicates may still use `EntityID` when comparing a reference column directly.

## Database Factory Integration

Katalyst manages the `Database` instance via `DatabaseFactory`, accessible in migrations and advanced scenarios:

```kotlin
import io.github.darkryh.katalyst.database.DatabaseFactory

class MyMigration(private val databaseFactory: DatabaseFactory) {
    fun execute() {
        transaction(databaseFactory.database) {
            // Transaction code here
        }
    }
}
```

The factory is automatically injected during DI initialization and manages:
- HikariCP connection pool
- Lifecycle management (startup/shutdown)
- Schema lifecycle according to the selected `schema { ... }` policy

## Troubleshooting*
*
### Import Errors

**Problem**: `Cannot resolve symbol 'selectAll'`
```kotlin
// ❌ Wrong import
import org.jetbrains.exposed.core.selectAll

// ✅ Correct import
import org.jetbrains.exposed.v1.jdbc.selectAll
```

**Problem**: `No database initialized`
```kotlin
// ❌ Wrong - no database passed
transaction {
    // Won't work outside transactionManager context
}

// ✅ Correct - via transactionManager
transactionManager.transaction {
    repository.findAll()
}

// ✅ Correct - explicit database in migrations
transaction(databaseFactory.database) {
    exec("SELECT 1")
}
```

**Problem**: `Type mismatch for reference query column`
```kotlin
// ❌ Wrong for a custom Exposed query predicate
UserProfilesTable.accountId eq 123L

// ✅ Correct for a custom Exposed query predicate
UserProfilesTable.accountId eq EntityID(123L, AuthAccountsTable)
```

## Summary

- **Always use `org.jetbrains.exposed.v1.*` imports** (v1 JDBC API)
- **Use `transactionManager.transaction { }` in services** (event consistency)
- **Use direct `transaction(database) { }` in migrations** (one-time operations)
- **Use Katalyst `mapping { ... }` for table row/write mappings**
- **Wrap IDs in `EntityID` only for custom Exposed query predicates that compare reference columns directly**
- **Reference Exposed 1.3.0 documentation** for advanced DSL patterns not covered here
