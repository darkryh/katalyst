package io.github.darkryh.katalyst.di.extension

import io.github.darkryh.katalyst.conventions.KatalystConventions
import io.github.darkryh.katalyst.di.internal.AutoBindingRegistrar
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.migrations.KatalystMigration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural guards on the extension-point catalog.
 *
 * Issue #16 happened because the facts describing an extension point were duplicated across
 * roughly fifteen sites and `KatalystMigration` was wired into only some of them: it landed
 * in the reserved-types filter (excluded from container bindings) without landing in the
 * multibinding set (which is what would have given it a lookup path). Nothing detected the
 * gap, and migrations silently never ran.
 *
 * These tests make that class of omission a build failure rather than a runtime silence.
 */
class ExtensionPointCatalogTest {

    private lateinit var engine: TestBeanEngine

    @BeforeTest
    fun setUp() {
        engine = TestBeanEngine()
    }

    @AfterTest
    fun tearDown() {
        engine.stop()
    }

    // ---------------------------------------------------------------------------------
    // Parity with the static-analysis source of truth
    // ---------------------------------------------------------------------------------

    /**
     * `KatalystConventions` is the dependency-free description of the same rules, consumed by
     * `katalyst-analysis` and the IntelliJ plugin. If the runtime catalog and the conventions
     * disagree, the IDE and the Gradle analysis silently stop matching what actually boots.
     */
    @Test
    fun `every catalog entry is declared in KatalystConventions`() {
        val conventionFqns = setOf(
            KatalystConventions.SERVICE,
            KatalystConventions.COMPONENT,
            KatalystConventions.CRUD_REPOSITORY,
            KatalystConventions.EVENT_HANDLER,
            KatalystConventions.KTOR_MODULE,
            KatalystConventions.KATALYST_MIGRATION,
            KatalystConventions.APPLICATION_INITIALIZER,
            KatalystConventions.APPLICATION_READY_INITIALIZER,
        )

        val catalogFqns = ExtensionPoints.all.mapNotNull { it.type.qualifiedName }.toSet()

        assertEquals(
            conventionFqns.sorted(),
            catalogFqns.sorted(),
            "the runtime catalog and KatalystConventions must describe the same marker types"
        )
    }

    // ---------------------------------------------------------------------------------
    // Internal consistency
    // ---------------------------------------------------------------------------------

    @Test
    fun `catalog ids and types are unique`() {
        assertEquals(
            ExtensionPoints.all.size,
            ExtensionPoints.all.map { it.id }.distinct().size,
            "duplicate extension-point ids would make discovery categories collide"
        )
        assertEquals(
            ExtensionPoints.all.size,
            ExtensionPoints.all.map { it.type }.distinct().size,
            "duplicate extension-point types would make base-class resolution ambiguous"
        )
    }

    @Test
    fun `every marker type is reserved from ordinary secondary bindings`() {
        ExtensionPoints.all.forEach { extensionPoint ->
            assertTrue(
                extensionPoint.type in ExtensionPoints.reservedTypes,
                "${extensionPoint.id} must be reserved so it is never bound as a plain interface"
            )
        }
    }

    @Test
    fun `multiBindingTypes matches the MULTI cardinality entries`() {
        assertEquals(
            ExtensionPoints.all.filter { it.cardinality == Cardinality.MULTI }.map { it.type }.toSet(),
            ExtensionPoints.multiBindingTypes,
            "the derived multibinding set must not drift from the declared cardinalities"
        )
    }

    @Test
    fun `an extension point with a featureId also carries an enable hint`() {
        ExtensionPoints.all.filter { it.featureId != null }.forEach { extensionPoint ->
            assertTrue(
                !extensionPoint.enableHint.isNullOrBlank(),
                "${extensionPoint.id} declares featureId='${extensionPoint.featureId}' but no " +
                    "enableHint, so the 'feature not enabled' warning cannot tell users what to do"
            )
        }
    }

    // ---------------------------------------------------------------------------------
    // Reachability: the actual defect from issue #16
    // ---------------------------------------------------------------------------------

    /**
     * A migration reached through an abstract intermediate. This is the ordinary way to write
     * one (`SqlMigration`), and the shape that regressed: its only *direct* supertype is an
     * abstract class, so any binding computed from declared supertypes misses the marker.
     */
    abstract class AbstractMigrationBase : KatalystMigration

    class MigrationViaAbstractBase : AbstractMigrationBase() {
        override val id: String = "via-abstract-base"
        override fun up() = Unit
    }

    class MigrationViaDirectInterface : KatalystMigration {
        override val id: String = "via-direct-interface"
        override fun up() = Unit
    }

    /** Two levels of abstract intermediate, to prove the lookup is depth-independent. */
    abstract class IntermediateMigrationBase : AbstractMigrationBase()

    class MigrationViaDeepChain : IntermediateMigrationBase() {
        override val id: String = "via-deep-chain"
        override fun up() = Unit
    }

    @Test
    fun `multibinding markers are derived for every implementation shape`() {
        val shapes = listOf(
            "direct interface" to MigrationViaDirectInterface(),
            "abstract intermediate" to MigrationViaAbstractBase(),
            "two-level chain" to MigrationViaDeepChain(),
        )

        shapes.forEach { (shape, instance) ->
            assertTrue(
                KatalystMigration::class in ExtensionPoints.multiBindingTypesOf(instance),
                "a migration declared via $shape must still be bound to KatalystMigration"
            )
        }
    }

    @Test
    fun `registered multibinding instances are reachable through getAll for every shape`() {
        val registrar = AutoBindingRegistrar(engine.container, engine, arrayOf("io.github.darkryh.katalyst"))

        val instances = listOf(
            MigrationViaDirectInterface(),
            MigrationViaAbstractBase(),
            MigrationViaDeepChain(),
        )

        instances.forEach { instance ->
            registrar.registerInstance(
                instance,
                instance::class,
                registrar.computeSecondaryTypes(instance::class, KatalystMigration::class)
            )
        }

        val found = engine.container.getAll(KatalystMigration::class)

        assertEquals(
            listOf("via-abstract-base", "via-deep-chain", "via-direct-interface"),
            found.map { it.id }.sorted(),
            "every registered migration must be reachable via getAll regardless of how deep " +
                "the marker sits in its type hierarchy"
        )
    }
}
