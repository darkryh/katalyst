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

 * In Katalyst:
 * - `Service extends Component` = business logic component
 * - `Repository` standalone = data access interface (like JpaRepository)
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