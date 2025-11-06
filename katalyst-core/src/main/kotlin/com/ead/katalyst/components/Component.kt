package com.ead.katalyst.components

/**
 * Marker interface for framework-managed components in Katalyst.
 *
 * Like Spring Boot's `@Service` and `@Component` annotations, this interface
 * marks classes as framework-managed business logic components.
 *
 * **When to use Component:**
 * - Services that contain business logic
 * - Validation helpers or other lightweight utilities
 * - Custom application components
 * - NOT for Repositories (they're data access, not components)
 *
 * **Why Component exists (Spring Boot pattern):**
 * - `@Service` marks business logic components (Spring Boot)
 * - `JpaRepository` marks data access (NOT a component)
 *
 * In Katalyst:
 * - `Service extends Component` = business logic component
 * - `Repository` standalone = data access interface (like JpaRepository)
 *
 * **Usage:**
 * ```kotlin
 * // ✅ Service = Component (business logic)
 * class UserService(
 *     private val userRepository: UserRepository,
 *     private val userValidator: UserValidator
 * ) : Service {
 *     override lateinit var transactionManager: DatabaseTransactionManager
 *     // ... implement service methods
 * }
 *
 * // ✅ Repository = Data Access (NOT a component)
 * class UserRepository : Repository<Long, UserEntity> {
 *     override val table = UsersTable
 *     override fun mapper(row: ResultRow): UserEntity = TODO()
 *     // ... optional custom repository methods
 * }
 *
 * // ✅ Validator = Component-backed helper
 * class UserValidator : Validator<User> {
 *     // ... implement validator methods
 * }
 * ```
 *
 * **Automatic Discovery:**
 * When the application starts:
 * 1. Scanner finds all classes implementing Component or Repository
 * 2. Framework validates the class structure
 * 3. Koin DI container registers them automatically
 * 4. No manual module or registration code needed
 *
 * **Important:**
 * - Do NOT create custom Koin modules
 * - Do NOT manually register components
 * - Just inherit from Component or Repository and the framework handles everything
 */
interface Component