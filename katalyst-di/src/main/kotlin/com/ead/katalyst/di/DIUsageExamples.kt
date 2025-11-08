package com.ead.katalyst.di

import com.ead.katalyst.database.DatabaseConfig
import io.ktor.server.application.Application
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

/**
 * Dependency Injection System Usage Guide.
 *
 * This module demonstrates the patterns for accessing components from the DI container.
 * For specific examples with services, repositories, and validators, see the examples module.
 */

// ============= Basic DI Access Pattern =============

/**
 * Pattern: Access components from the global DI container.
 *
 * **In a Ktor route handler:**
 * ```kotlin
 * fun Application.configureRouting() = katalystRouting {
 *     route("/api/users") {
 *         val koin = GlobalContext.get()
 *         val myService = koin.get<MyService>()
 *
 *         post {
 *             val result = myService.processRequest()
 *             call.respond(result)
 *         }
 *     }
 * }
 * ```
 *
 * **In a Service class:**
 * ```kotlin
 * class MyService(
 *     private val myRepository: MyRepository,
 *     private val anotherService: AnotherService
 * ) : Service {
 *     override lateinit var transactionManager: DatabaseTransactionManager
 *
 *     suspend fun processRequest(): String {
 *         return "result"
 *     }
 * }
 * ```
 */
object DIAccessPatterns {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun demonstrateBasicUsage() {
        logger.info("Demonstrating basic DI access pattern")

        val options = KatalystDIOptions(
            databaseConfig = DatabaseConfig(
                url = "jdbc:h2:mem:katalyst;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                username = "sa",
                password = ""
            )
        )
        initializeKoinStandalone(options)

        try {
            val koin = org.koin.core.context.GlobalContext.get()
            logger.info("DI Container initialized - components can now be accessed via GlobalContext.get()")

        } finally {
            stopKoinStandalone()
        }
    }
}

// ============= Framework Initialization Patterns =============

/**
 * Pattern: Initialize DI for a Ktor application.
 *
 * **Basic setup:**
 * ```kotlin
 * fun Application.module() {
 *     // Initialize DI with database configuration
 *     initializeKatalystDI(
 *         databaseConfig = DatabaseConfig(
 *             url = "jdbc:postgresql://localhost/mydb",
 *             driver = "org.postgresql.Driver",
 *             username = "user",
 *             password = "pass"
 *         ),
 *         features = listOf(/* optional features installed by their modules */)
 *     )
 *
 *     // Now services, repositories, validators are automatically available
 *     configureRouting()
 *     configureMiddleware()
 *     exceptionHandler()
 * }
 * ```
 */
object KtorApplicationPattern {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun Application.setupDI() {
        logger.info("Setting up DI for Ktor application")
        // Developer provides their own database config
        // and optionally installs additional features (migrations, scheduler, etc.)
    }
}

// ============= Component Registration Patterns =============

/**
 * Pattern: Register custom services, repositories, and validators.
 *
 * Developers implement these interfaces:
 * - `Service` - for business logic
 * - `Repository<E, D>` - for data access
 * - `Validator<T>` - for input validation
 *
 * The scanner automatically discovers and registers these.
 *
 * **Example:**
 * ```kotlin
 * // Implement Service
 * class UserService(
 *     private val userRepository: UserRepository,
 *     private val userValidator: UserValidator
 * ) : Service {
 *     override lateinit var transactionManager: DatabaseTransactionManager
 *
 *     suspend fun createUser(dto: CreateUserDTO): UserDTO {
 *         userValidator.validate(dto)
 *         return transactionManager.transaction {
 *             userRepository.save(dto.toEntity())
 *         }
 *     }
 * }
 *
 * // Implement Repository
 * object UsersTable : LongIdTable("users") {
 *     val name = varchar("name", 100)
 *     val email = varchar("email", 150)
 * }
 *
 * class UserRepository : Repository<Long, User> {
 *     override val table = UsersTable
 *
 *     override fun mapper(row: ResultRow): User =
 *         User(
 *             id = row[table.id].value,
 *             name = row[table.name],
 *             email = row[table.email]
 *         )
 *
 *     // Optional custom queries can still be added
 * }
 *
 * // Implement Validator
 * class UserValidator : Validator<User> {
 *     override suspend fun validate(entity: User): ValidationResult = TODO()
 * }
 * ```
 *
 * The framework automatically discovers these via the scanner module
 * and registers them in the Koin DI container.
 */
object ComponentRegistrationPattern {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun demonstratePattern() {
        logger.info("Framework automatically scans and registers:")
        logger.info("  - Services (implement Service interface)")
        logger.info("  - Repositories (implement Repository<Id, Entity> interface)")
        logger.info("  - Validators (implement Validator<T> interface)")
        logger.info("  - Event handlers (implement EventHandler interface)")
        logger.info("  - HTTP handlers (implement HttpHandler interface)")
    }
}

// ============= Transaction Management Pattern =============

/**
 * Pattern: Use automatic transaction management in services.
 *
 * Services automatically get `transactionManager` injected under the hood.
 *
 * **Example:**
 * ```kotlin
 * class UserService(
 *     private val userRepository: UserRepository,
 *     private val userValidator: UserValidator
 * ) : Service {
 *     override lateinit var transactionManager: DatabaseTransactionManager
 *
 *     suspend fun createUser(dto: CreateUserDTO): User {
 *         // Validate input
 *         userValidator.validate(dto)
 *
 *         // All repository operations run in a transaction
 *         return transactionManager.transaction {
 *             val user = userRepository.save(dto.toEntity())
 *             logger.info("User created: ${user.id}")
 *             user
 *         }
 *         // Transaction is committed here, or rolled back if exception occurs
 *     }
 * }
 * ```
 */
object TransactionManagementPattern {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun demonstratePattern() {
        logger.info("Transaction Pattern:")
        logger.info("1. Services wrap repository calls in transactionManager.transaction { }")
        logger.info("2. Changes are committed on success")
        logger.info("3. Changes are rolled back on exception")
        logger.info("4. No manual transaction code needed")
    }
}

// ============= Scheduler Pattern =============

/**
 * Pattern: Use the injected scheduler in services.
 *
 * Services can opt into scheduling via the `requireScheduler()` / `schedulerOrNull` helpers.
 *
 * **Example:**
 * ```kotlin
 * class CleanupService(
 *     private val userRepository: UserRepository
 * ) : Service {
 *     private val scheduler = requireScheduler()
 *
 *     init {
 *         scheduler.scheduleCron(
 *             taskName = "cleanup",
 *             cronExpression = CronExpression("0 2 * * *"),
 *             initialDelay = Duration.ZERO
 *         ) {
 *             transactionManager.transaction {
 *                 userRepository.findAllInactive()
 *                     .forEach { userRepository.delete(it.id) }
 *             }
 *         }
 *     }
 * }
 * ```
 */
object SchedulerPattern {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun demonstratePattern() {
        logger.info("Scheduler Pattern:")
        logger.info("1. Services register cron/fixed schedule jobs via the scheduler extension helpers")
        logger.info("2. Use requireScheduler() to fail fast, or schedulerOrNull when the module is optional")
        logger.info("3. Scheduler runs tasks in a separate thread")
        logger.info("4. Wrap repository work in transactionManager.transaction { ... } inside jobs")
        logger.info("5. Call scheduler.stop() during shutdown if you manage lifecycle manually")
    }
}
