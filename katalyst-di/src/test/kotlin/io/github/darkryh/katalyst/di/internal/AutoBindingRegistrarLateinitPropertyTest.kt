package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.transactions.manager.DatabaseTransactionManager
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Well-known property injection has to work for `lateinit var`, not only for nullable `var`.
 *
 * Reading an unassigned `lateinit` property through reflection can surface the failure wrapped in
 * an `InvocationTargetException`; when that escapes the "is it already initialised?" probe it
 * aborts component registration - and therefore the whole boot - instead of injecting.
 */
class AutoBindingRegistrarLateinitPropertyTest {

    private lateinit var engine: TestBeanEngine

    @BeforeTest
    fun setUp() {
        engine = TestBeanEngine()
    }

    @AfterTest
    fun tearDown() {
        engine.stop()
    }

    @Test
    fun `a lateinit well-known property is assigned during registration`() {
        val transactionManager = DatabaseTransactionManager(
            Database.connect(
                url = "jdbc:h2:mem:lateinit-well-known;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver"
            )
        )
        engine.registerInstance(
            instance = transactionManager,
            primaryType = DatabaseTransactionManager::class,
            secondaryTypes = emptyList(),
            qualifier = null
        )

        val registrar = AutoBindingRegistrar(
            container = engine.container,
            beanEngine = engine,
            scanPackages = emptyArray()
        )
        val component = LateinitTransactionComponent()

        registrar.injectWellKnownProperties(component)

        assertSame(
            transactionManager,
            component.transactionManagerOrNull(),
            "a lateinit well-known property must be assigned, not skipped"
        )
    }
}

private class LateinitTransactionComponent : Component {
    lateinit var transactionManager: DatabaseTransactionManager

    fun transactionManagerOrNull(): DatabaseTransactionManager? =
        if (::transactionManager.isInitialized) transactionManager else null
}
