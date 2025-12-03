package io.github.darkryh.katalyst.di.lifecycle

import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import io.github.darkryh.katalyst.di.lifecycle.StartupWarningsAggregator.WarningSeverity
import io.github.darkryh.katalyst.di.lifecycle.StartupWarnings

/**
 * Consolidates component discovery output into structured summary tables.
 *
 * **Purpose**: Display discovered components (repositories, services, components, etc.)
 * in organized visual tables instead of scattered throughout logs.
 *
 * **Display Format**:
 * ```
 * ╔═══════════════════════════════════════════════════════════╗
 * ║ COMPONENT DISCOVERY SUMMARY (total: 42)                   ║
 * ╚═══════════════════════════════════════════════════════════╝
 *
 * ┌─ REPOSITORIES (12) ────────────────────────────────────────┐
 * │ 1. UserRepository              | @Repository              │
 * │ 2. ProductRepository           | @Repository              │
 * └──────────────────────────────────────────────────────────────┘
 *
 * ┌─ SERVICES (15) ────────────────────────────────────────────┐
 * │ 1. UserService                 | @Service                 │
 * │ 2. ProductService              | @Service                 │
 * └──────────────────────────────────────────────────────────────┘
 * ```
 */
class DiscoverySummaryLogger {
    private val logger = LoggerFactory.getLogger("DiscoverySummaryLogger")

    private val repositories = mutableListOf<ComponentInfo>()
    private val services = mutableListOf<ComponentInfo>()
    private val components = mutableListOf<ComponentInfo>()
    private val validators = mutableListOf<ComponentInfo>()
    private val databaseTables = mutableListOf<String>()
    private val ktorModules = mutableListOf<ComponentInfo>()

    data class ComponentInfo(
        val name: String,
        val type: String,
        val annotation: String? = null,
        val metadata: String? = null
    )

    /**
     * Add a discovered repository.
     */
    fun addRepository(name: String, annotation: String? = null, metadata: String? = null) {
        repositories.add(ComponentInfo(name, "Repository", annotation, metadata))
    }

    /**
     * Add multiple repositories at once.
     */
    fun addRepositories(items: List<ComponentInfo>) {
        repositories.addAll(items)
    }

    /**
     * Add a discovered service.
     */
    fun addService(name: String, annotation: String? = null, metadata: String? = null) {
        services.add(ComponentInfo(name, "Service", annotation, metadata))
    }

    /**
     * Add multiple services at once.
     */
    fun addServices(items: List<ComponentInfo>) {
        services.addAll(items)
    }

    /**
     * Add a discovered component.
     */
    fun addComponent(name: String, type: String, annotation: String? = null, metadata: String? = null) {
        components.add(ComponentInfo(name, type, annotation, metadata))
    }

    /**
     * Add multiple components at once.
     */
    fun addComponents(items: List<ComponentInfo>) {
        components.addAll(items)
    }

    /**
     * Add a discovered validator.
     */
    fun addValidator(name: String, annotation: String? = null, metadata: String? = null) {
        validators.add(ComponentInfo(name, "Validator", annotation, metadata))
    }

    /**
     * Add multiple validators at once.
     */
    fun addValidators(items: List<ComponentInfo>) {
        validators.addAll(items)
    }

    /**
     * Add a discovered database table.
     */
    fun addDatabaseTable(tableName: String) {
        databaseTables.add(tableName)
    }

    /**
     * Add multiple database tables at once.
     */
    fun addDatabaseTables(tableNames: List<String>) {
        databaseTables.addAll(tableNames)
    }

    /**
     * Add a discovered Ktor module.
     */
    fun addKtorModule(name: String, annotation: String? = null, metadata: String? = null) {
        ktorModules.add(ComponentInfo(name, "KtorModule", annotation, metadata))
    }

    /**
     * Add multiple Ktor modules at once.
     */
    fun addKtorModules(items: List<ComponentInfo>) {
        ktorModules.addAll(items)
    }

    /**
     * Display the discovery summary with all component tables.
     */
    fun display() {
        val total = repositories.size + services.size + components.size + validators.size + ktorModules.size

        if (total == 0) {
            logger.warn("⚠ No components discovered during component scan. Check scan packages and annotations.")
            reportEmptySections()
            return
        }

        logger.info("")
        logger.info("╔════════════════════════════════════════════════════╗")
        logger.info("║ PHASE 3: COMPONENT DISCOVERY SUMMARY ({} total)  ║", String.format("%3d", total))
        logger.info("╚════════════════════════════════════════════════════╝")
        logger.info("")

        // Display Repositories
        if (repositories.isNotEmpty()) {
            displayComponentSection("REPOSITORIES", repositories)
        }

        // Display Services
        if (services.isNotEmpty()) {
            displayComponentSection("SERVICES", services)
        }

        // Display Components
        if (components.isNotEmpty()) {
            displayComponentSection("COMPONENTS", components)
        }

        // Display Validators
        if (validators.isNotEmpty()) {
            displayComponentSection("VALIDATORS", validators)
        }

        // Display Ktor Modules
        if (ktorModules.isNotEmpty()) {
            displayComponentSection("KTOR MODULES", ktorModules)
        }

        // Display Database Tables
        if (databaseTables.isNotEmpty()) {
            displayDatabaseTables()
        }

        logger.info("")
        logger.info("✓ Component discovery complete - {} items registered", total)
        logger.info("")

        reportEmptySections()
    }

    private fun displayComponentSection(sectionName: String, items: List<ComponentInfo>) {
        logger.info("┌─ {} ({}) {}─┐", sectionName, items.size, "─".repeat(maxOf(0, 30 - sectionName.length - items.size.toString().length)))
        items.forEachIndexed { index, item ->
            val displayName = item.name.padEnd(30, ' ')
            val displayAnnotation = item.annotation?.padEnd(20, ' ') ?: ""
            val metadata = item.metadata?.let { " [$it]" } ?: ""
            logger.info("│ {:>2}. {} | {}{}│", index + 1, displayName, displayAnnotation, metadata)
        }
        logger.info("└──────────────────────────────────────────────────────┘")
        logger.info("")
    }

    private fun displayDatabaseTables() {
        logger.info("┌─ DATABASE TABLES ({}) {}─┐", databaseTables.size, "─".repeat(maxOf(0, 28 - databaseTables.size.toString().length)))
        databaseTables.chunked(3).forEachIndexed { chunkIndex, chunk ->
            val tableNames = chunk.joinToString(" | ") { it.padEnd(16, ' ') }
            logger.info("│ {:>2}. {} │", chunkIndex * 3 + 1, tableNames)
        }
        logger.info("└──────────────────────────────────────────────────────┘")
        logger.info("")
    }

    /**
     * Get all discovered components.
     */
    fun getAllComponents(): List<ComponentInfo> {
        return repositories + services + components + validators + ktorModules
    }

    /**
     * Get count of all discovered components by type.
     */
    fun getCounts(): Map<String, Int> {
        return mapOf(
            "repositories" to repositories.size,
            "services" to services.size,
            "components" to components.size,
            "validators" to validators.size,
            "ktorModules" to ktorModules.size,
            "databaseTables" to databaseTables.size,
            "total" to getAllComponents().size
        )
    }

    /**
     * Clear all discovered components.
     */
    fun clear() {
        repositories.clear()
        services.clear()
        components.clear()
        validators.clear()
        databaseTables.clear()
        ktorModules.clear()
    }

    private fun reportEmptySections() {
        data class SectionStatus(
            val label: String,
            val isEmpty: Boolean,
            val severity: WarningSeverity,
            val hint: String
        )

        val sections = listOf(
            SectionStatus(
                label = "repositories",
                isEmpty = repositories.isEmpty(),
                severity = WarningSeverity.WARNING,
                hint = "Add @Repository implementations or include their package in scanPackages()."
            ),
            SectionStatus(
                label = "services",
                isEmpty = services.isEmpty(),
                severity = WarningSeverity.WARNING,
                hint = "Add @Service implementations or ensure they are on the classpath."
            ),
            SectionStatus(
                label = "components",
                isEmpty = components.isEmpty(),
                severity = WarningSeverity.INFO,
                hint = "Implement io.github.darkryh.katalyst.core.component.Component to register framework utilities."
            ),
            SectionStatus(
                label = "validators",
                isEmpty = validators.isEmpty(),
                severity = WarningSeverity.INFO,
                hint = "Annotate validation classes or extend the validator base types."
            ),
            SectionStatus(
                label = "ktor modules",
                isEmpty = ktorModules.isEmpty(),
                severity = WarningSeverity.INFO,
                hint = "Create KtorModule implementations to customize the HTTP pipeline."
            ),
            SectionStatus(
                label = "database tables",
                isEmpty = databaseTables.isEmpty(),
                severity = WarningSeverity.WARNING,
                hint = "Ensure Table objects are registered so schema initialization can run."
            )
        )

        sections.filter { it.isEmpty }.forEach { section ->
            val message = "No ${section.label} discovered during component scan"
            when (section.severity) {
                WarningSeverity.CRITICAL, WarningSeverity.WARNING -> logger.warn("⚠ {}", message)
                WarningSeverity.INFO -> logger.info("ℹ {}", message)
            }
            StartupWarnings.add(
                category = "Component Discovery",
                message = message,
                severity = section.severity,
                hint = section.hint
            )
        }
    }
}

/**
 * Global instance for discovery summary logging.
 */
object DiscoverySummary {
    private val logger = DiscoverySummaryLogger()

    fun addRepository(name: String, annotation: String? = null, metadata: String? = null) =
        logger.addRepository(name, annotation, metadata)

    fun addRepositories(items: List<DiscoverySummaryLogger.ComponentInfo>) =
        logger.addRepositories(items)

    fun addService(name: String, annotation: String? = null, metadata: String? = null) =
        logger.addService(name, annotation, metadata)

    fun addServices(items: List<DiscoverySummaryLogger.ComponentInfo>) =
        logger.addServices(items)

    fun addComponent(name: String, type: String, annotation: String? = null, metadata: String? = null) =
        logger.addComponent(name, type, annotation, metadata)

    fun addComponents(items: List<DiscoverySummaryLogger.ComponentInfo>) =
        logger.addComponents(items)

    fun addValidator(name: String, annotation: String? = null, metadata: String? = null) =
        logger.addValidator(name, annotation, metadata)

    fun addValidators(items: List<DiscoverySummaryLogger.ComponentInfo>) =
        logger.addValidators(items)

    fun addDatabaseTable(tableName: String) = logger.addDatabaseTable(tableName)

    fun addDatabaseTables(tableNames: List<String>) = logger.addDatabaseTables(tableNames)

    fun addKtorModule(name: String, annotation: String? = null, metadata: String? = null) =
        logger.addKtorModule(name, annotation, metadata)

    fun addKtorModules(items: List<DiscoverySummaryLogger.ComponentInfo>) =
        logger.addKtorModules(items)

    fun display() = logger.display()

    fun getAllComponents(): List<DiscoverySummaryLogger.ComponentInfo> = logger.getAllComponents()

    fun getCounts(): Map<String, Int> = logger.getCounts()

    fun clear() = logger.clear()
}
