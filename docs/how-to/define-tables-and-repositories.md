# Define tables and repositories

Katalyst persists data with Exposed and HikariCP. You define a table, a repository, and
optionally custom queries; Katalyst discovers them, manages the connection pool, and injects
the repository wherever you need it. This guide assumes you have a bootstrap with a
`database { … }` block — see [Configure with YAML](configure-yaml.md).

## Define a table

A table is an Exposed `LongIdTable` (or another `IdTable`) that also implements
`Table<Id, Entity>`. The `mapping { … }` block defines how rows are read and written.

An entity is a plain data class that implements `Identifiable<Id>` (with `id` nullable and
defaulting to `null`) — both `Table` and `CrudRepository` require it.

```kotlin
import io.github.darkryh.katalyst.core.persistence.Table
import io.github.darkryh.katalyst.core.persistence.mapping
import io.github.darkryh.katalyst.repositories.Identifiable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

data class AuthAccount(
    override val id: Long? = null,
    val email: String,
    val passwordHash: String,
    val createdAtMillis: Long,
    val status: String = "active"
) : Identifiable<Long>

object AuthAccountsTable : LongIdTable("auth_accounts"), Table<Long, AuthAccount> {
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAtMillis = long("created_at_millis")
    val status = varchar("status", 32).default("active")

    override val mapping = mapping<Long, AuthAccount> {
        generatedId(id, AuthAccount::id)
        field(email, AuthAccount::email)
        field(passwordHash, AuthAccount::passwordHash)
        field(createdAtMillis, AuthAccount::createdAtMillis)
        field(status, AuthAccount::status)

        construct {
            AuthAccount(
                id = this[id],
                email = this[email],
                passwordHash = this[passwordHash],
                createdAtMillis = this[createdAtMillis],
                status = this[status]
            )
        }
    }
}
```

- `generatedId` maps an auto-increment primary key.
- `field` maps a column to a property in both directions.
- `construct` builds an entity from a mapped row.

Mapping uses raw ID values — you do not wrap inserts in `EntityID`. Use the
`org.jetbrains.exposed.v1.*` import set throughout.

## Add a foreign key

Use Exposed's `reference` for the column and Katalyst's `reference` mapping for the property:

```kotlin
import io.github.darkryh.katalyst.repositories.Identifiable
import org.jetbrains.exposed.v1.core.ReferenceOption

data class UserProfile(
    override val id: Long? = null,
    val accountId: Long,
    val displayName: String
) : Identifiable<Long>

object UserProfilesTable : LongIdTable("user_profiles"), Table<Long, UserProfile> {
    val accountId = reference("account_id", AuthAccountsTable, onDelete = ReferenceOption.CASCADE)
    val displayName = varchar("display_name", 120)

    override val mapping = mapping<Long, UserProfile> {
        generatedId(id, UserProfile::id)
        reference(accountId, UserProfile::accountId)
        field(displayName, UserProfile::displayName)
        construct {
            UserProfile(id = this[id], accountId = this[accountId], displayName = this[displayName])
        }
    }
}
```

## Define a repository

Implement `CrudRepository<Id, Entity>` and point it at the table. You inherit `save`,
`findById`, `findAll`, `deleteById`, and more.

```kotlin
import io.github.darkryh.katalyst.repositories.CrudRepository
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

class AuthAccountRepository : CrudRepository<Long, AuthAccount> {
    override val table: LongIdTable = AuthAccountsTable
}
```

Because the class lives under a scanned package, it is registered automatically and injected
by type into services. You never register it by hand.

## Add custom queries

For anything beyond CRUD, use the Exposed DSL inside the repository and `map(...)` to turn
rows into entities:

```kotlin
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class AuthAccountRepository : CrudRepository<Long, AuthAccount> {
    override val table: LongIdTable = AuthAccountsTable

    fun findByEmail(email: String): AuthAccount? =
        AuthAccountsTable
            .selectAll().where { AuthAccountsTable.email eq email }
            .limit(1)
            .firstOrNull()
            ?.let(::map)

    fun findDormant(): List<AuthAccount> =
        AuthAccountsTable
            .selectAll()
            .where { (AuthAccountsTable.status eq "pending") and (AuthAccountsTable.createdAtMillis less cutoff()) }
            .map(::map)
}
```

The operator reference (`eq`, `less`, `inList`, `like`, and so on) is in the
[persistence reference](../reference/persistence.md#query-operators).

## Use the repository in a service

Wrap repository calls in `transactionManager.transaction { … }` so writes — and any events
you publish — commit atomically:

```kotlin
class AuthService(private val repository: AuthAccountRepository) : Service {
    suspend fun findOrReject(email: String): AuthAccount = transactionManager.transaction {
        repository.findByEmail(email) ?: error("No account for $email")
    }
}
```

See [Transactions](../reference/transactions.md) for isolation levels, retry, and timeouts.

## Run raw SQL when you must

For bootstrap preconditions or operations Exposed does not cover, inject `SqlExecutor`:

```kotlin
class SchemaBootstrap(private val sqlExecutor: SqlExecutor) : Service {
    suspend fun ensurePreconditions() {
        sqlExecutor.executeBatch(
            listOf(
                "CREATE TABLE IF NOT EXISTS feature_flags (key VARCHAR(80) PRIMARY KEY, enabled BOOLEAN)",
                "INSERT INTO feature_flags (key, enabled) VALUES ('new_ui', FALSE)"
            )
        )
    }
}
```

`SqlExecutor` reuses the active transaction's connection when called inside one, and uses a
pooled connection otherwise. Its full surface (`executeUpdate`, `query`, `queryOne`,
`executeBatch`) is in the [persistence reference](../reference/persistence.md#sqlexecutor).

## Related

- [Persistence reference](../reference/persistence.md) — `Table`, `mapping`, `CrudRepository`,
  `SqlExecutor`, `DatabaseConfig`.
- [Run database migrations](run-migrations.md) — manage schema changes over time.
- [Transactions](../reference/transactions.md) — transactional guarantees.

