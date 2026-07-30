package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.feature.KatalystBeanEngine
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import io.github.darkryh.katalyst.migrations.KatalystMigration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.koin.core.context.stopKoin

/**
 * Pins the in-memory test bean engine to the production Koin engine.
 *
 * Katalyst integration tests boot [TestKatalystBeanEngine], but applications boot
 * [KoinBeanEngine]. If the two disagree about how instances are looked up, a test suite can
 * be entirely green while production is broken — or the reverse. Issue #16 turned on exactly
 * such a lookup path, so the two engines are held to one contract here.
 *
 * The contract that matters for extension points: an instance registered under a secondary
 * type must be reachable through `getAll` for that type, and several instances may share one
 * unqualified secondary type.
 */
class BeanEngineContractTest {

    class MigrationA : KatalystMigration {
        override val id: String = "20240101_a"
        override fun up() = Unit
    }

    class MigrationB : KatalystMigration {
        override val id: String = "20240102_b"
        override fun up() = Unit
    }

    private fun engines(): List<Pair<String, () -> KatalystBeanEngine>> = listOf(
        "TestKatalystBeanEngine" to { TestKatalystBeanEngine() },
        "KoinBeanEngine" to { KoinBeanEngine },
    )

    @AfterTest
    fun tearDown() {
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    private fun withEachEngine(block: (String, KatalystBeanEngine) -> Unit) {
        engines().forEach { (name, factory) ->
            KatalystContainerProvider.reset()
            runCatching { stopKoin() }
            val engine = factory()
            try {
                block(name, engine)
            } finally {
                runCatching { engine.stop() }
                KatalystContainerProvider.reset()
                runCatching { stopKoin() }
            }
        }
    }

    @Test
    fun `instance registered under a secondary type is reachable via getAll`() {
        withEachEngine { name, engine ->
            val container = engine.start(emptyList(), allowOverrides = true)
            val migration = MigrationA()

            engine.registerInstance(migration, MigrationA::class, listOf(KatalystMigration::class), null)

            assertEquals(
                1,
                container.getAll(KatalystMigration::class).size,
                "$name: secondary type must be reachable via getAll"
            )
            assertNotNull(
                container.getOrNull(MigrationA::class, null),
                "$name: concrete type must stay resolvable"
            )
        }
    }

    @Test
    fun `several instances may share one unqualified secondary type`() {
        withEachEngine { name, engine ->
            val container = engine.start(emptyList(), allowOverrides = true)

            engine.registerInstance(MigrationA(), MigrationA::class, listOf(KatalystMigration::class), null)
            engine.registerInstance(MigrationB(), MigrationB::class, listOf(KatalystMigration::class), null)

            val all = container.getAll(KatalystMigration::class)
            assertEquals(2, all.size, "$name: both instances must be returned")
            assertEquals(
                listOf("20240101_a", "20240102_b"),
                all.map { it.id }.sorted(),
                "$name: getAll must return every instance sharing the secondary type"
            )
        }
    }

    @Test
    fun `a type with no registration returns an empty getAll`() {
        withEachEngine { name, engine ->
            val container = engine.start(emptyList(), allowOverrides = true)

            assertEquals(
                emptyList(),
                container.getAll(KatalystMigration::class),
                "$name: unregistered type must yield an empty list, not an error"
            )
        }
    }
}
