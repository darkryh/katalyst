# Persistence & Repository Patterns

Katalyst builds on Exposed + HikariCP + JDBC to offer strongly typed repositories with automatic schema discovery. Define your tables/repositories under the scanned packages and the persistence module makes them available via DI.

## Tables

Extend Exposed’s `IdTable` and implement Katalyst’s `Table<Id, Entity>` interface so the scanner knows how to map rows and assign entities.

```kotlin
object AuthAccountsTable : LongIdTable("auth_accounts"), Table<Long, AuthAccountEntity> {
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAtMillis = long("created_at_millis")
    val lastLoginAtMillis = long("last_login_at_millis").nullable()
    val status = varchar("status", 32).default("active")

    override fun mapRow(row: ResultRow) = AuthAccountEntity(
        id = row[id].value,
        email = row[email],
        passwordHash = row[passwordHash],
        createdAtMillis = row[createdAtMillis],
        lastLoginAtMillis = row[lastLoginAtMillis],
        status = row[status]
    )

    override fun assignEntity(statement: UpdateBuilder<*>, entity: AuthAccountEntity, skipIdColumn: Boolean) {
        if (!skipIdColumn && entity.id != null) {
            statement[id] = EntityID(entity.id, this) // REQUIRED - this must be implemented  so the CRUD operations in the repository works as expected
        }
        statement[email] = entity.email
        statement[passwordHash] = entity.passwordHash
        statement[createdAtMillis] = entity.createdAtMillis
        statement[lastLoginAtMillis] = entity.lastLoginAtMillis
        statement[status] = entity.status
    }
}
```

## Repositories

Implement `CrudRepository<Id, Entity>` to inherit the standard CRUD helpers. Custom queries use Exposed DSL inside the repository.

```kotlin
class AuthAccountRepository : CrudRepository<Long, AuthAccountEntity> {
    override val table = AuthAccountsTable

    fun findByEmail(email: String): AuthAccountEntity? =
        AuthAccountsTable
            .selectAll().where { AuthAccountsTable.email eq email }
            .limit(1)
            .firstOrNull()
            ?.let(::map)
}
```

Once defined, repositories are injected into services automatically (no manual registration) because the scanner picks them up under the configured packages.

## Services & Transactions

Wrap repository operations in `transactionManager.transaction { … }` inside Services to guarantee atomic persistence + event consistency.

```kotlin
class UserProfileService(
    private val repository: UserProfileRepository
) : Service {
    suspend fun createProfileForAccount(accountId: Long, displayName: String): UserProfile =
        transactionManager.transaction {
            repository.findByAccountId(accountId)?.toDomain()
                ?: repository.save(UserProfileEntity(accountId = accountId, displayName = displayName)).toDomain()
        }
}
```

`DatabaseTransactionManager` (from `katalyst-transactions`) ensures events queued during the transaction are only published after commit.

## Custom Queries

Use Exposed DSL for complex filters, joins, or manual SQL.

```kotlin
fun findDormantAccounts(): List<AuthAccountEntity> =
    AuthAccountsTable
        .selectAll()
        .where { (AuthAccountsTable.status eq "pending") and
                 (AuthAccountsTable.lastLoginAtMillis.isNull()) }
        .map(::map)
```

## Testing Repositories

`katalystTestEnvironment` wires repositories the same way as production. Seed data using repositories or Exposed DSL inside a transaction from the environment’s `DatabaseTransactionManager`.

```kotlin
private suspend fun seedAccount(env: KatalystTestEnvironment): Long {
    val repo = env.get<AuthAccountRepository>()
    val hasher = env.get<PasswordHasher>()
    val tx = env.get<DatabaseTransactionManager>()
    return tx.transaction {
        repo.save(AuthAccountEntity(
            email = "seed-${'$'}{System.currentTimeMillis()}@example.com",
            passwordHash = hasher.hash("Sup3rSecure!"),
            createdAtMillis = System.currentTimeMillis(),
            status = "active"
        )).id!!
    }
}
```

## Tooling Recap

- **JDBC driver + HikariCP** (`katalyst-persistence`, `katalyst-database`) manage the connection pool.
- **Exposed** handles SQL generation/mapping.
- **Schema creation** is automatic via `SchemaUtils.createMissingTablesAndColumns` during bootstrap.
- **Repositories** are injected automatically—no manual modules required.
