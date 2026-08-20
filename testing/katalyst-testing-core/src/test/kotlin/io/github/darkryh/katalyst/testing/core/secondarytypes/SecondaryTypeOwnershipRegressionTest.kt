package io.github.darkryh.katalyst.testing.core.secondarytypes

import io.github.darkryh.katalyst.core.exception.DependencyInjectionException
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression coverage for secondary-type ownership leaking across bootstraps.
 *
 * The collision check that rejects two components claiming one interface used to record its
 * owners in a process-global map that no shutdown ever cleared. A second bootstrap in the
 * same JVM was therefore validated against the *first* application's bindings: booting a
 * different application — the normal case for a test suite, and for anything that restarts
 * in place — failed with a collision between two types that never coexisted in a container.
 */
class SecondaryTypeOwnershipRegressionTest {

    private fun bootAndClose(vararg scanPackages: String) {
        katalystTestEnvironment {
            scan(*scanPackages)
            disableScheduler()
        }.close()
    }

    private fun collisionIn(error: Throwable): DependencyInjectionException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<DependencyInjectionException>()
            .firstOrNull { it.message?.contains("Secondary type binding collision") == true }

    @Test
    fun `a second application boots in a JVM that already booted a first one`() {
        bootAndClose(APP_A)

        val failure = runCatching { bootAndClose(APP_B) }.exceptionOrNull()

        assertTrue(
            failure == null,
            "the second bootstrap was rejected because of state left behind by the first: " +
                "${collisionIn(failure ?: Throwable())?.message ?: failure}"
        )
    }

    @Test
    fun `the second application's own binding is the one that wins`() {
        bootAndClose(APP_A)

        katalystTestEnvironment {
            scan(APP_B)
            disableScheduler()
        }.use { environment ->
            assertEquals(
                "paypal",
                environment.container.get(PaymentGateway::class).provider,
                "the interface resolved to a component the second bootstrap never discovered"
            )
        }
    }

    @Test
    fun `alternating applications boot repeatedly`() {
        val order = listOf(APP_A, APP_B, APP_A, APP_B)
        order.forEachIndexed { index, scanPackage ->
            val failure = runCatching { bootAndClose(scanPackage) }.exceptionOrNull()
            assertTrue(
                failure == null,
                "bootstrap ${index + 1} of ${order.size} ($scanPackage) failed: " +
                    "${collisionIn(failure ?: Throwable())?.message ?: failure}"
            )
        }
    }

    /**
     * The other half of the contract: clearing ownership per bootstrap must not turn the
     * collision check off. Two owners of one interface *within a single container* is still
     * an ambiguous binding and still has to fail.
     */
    @Test
    fun `one bootstrap still rejects two components claiming the same secondary type`() {
        val failure = assertFails { bootAndClose(APP_A, APP_B) }

        assertNotNull(
            collisionIn(failure),
            "scanning both applications at once must still fail with a collision, got: $failure"
        )
    }

    private companion object {
        const val APP_A = "io.github.darkryh.katalyst.testing.core.secondarytypes.appa"
        const val APP_B = "io.github.darkryh.katalyst.testing.core.secondarytypes.appb"
    }
}
