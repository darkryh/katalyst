package io.github.darkryh.katalyst.di.invocation

import io.github.darkryh.katalyst.core.di.KatalystContainerProvider
import io.github.darkryh.katalyst.di.test.TestBeanEngine
import io.github.darkryh.katalyst.koin.KoinBeanEngine
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.github.darkryh.katalyst.core.exception.BeanNotFoundException

/**
 * The exception a container raises for a missing bean must be classified as a *missing binding*,
 * not as a binding that blew up.
 *
 * [isMissingBindingFailure] is the switch between "this optional dependency simply is not
 * registered, supply null" and "this dependency exists and failed to build, abort the boot". It
 * matches on exception name or message rather than on a type, because `katalyst-di` must not depend
 * on a concrete bean engine — which means the coupling is invisible to the compiler and one reworded
 * message silently turns every unsatisfied optional dependency into a fatal boot error.
 *
 * So both engines' missing-bean exceptions are fed through it here, for real, from a real container.
 */
class MissingBindingClassificationTest {

    private class Unregistered

    @AfterTest
    fun tearDown() {
        runCatching { KoinBeanEngine.stop() }
        KatalystContainerProvider.reset()
        runCatching { stopKoin() }
    }

    @Test
    fun `the Koin container's missing-bean exception is classified as a missing binding`() {
        runCatching { stopKoin() }
        val container = KoinBeanEngine.start(emptyList(), allowOverrides = true)

        val error = assertFailsWith<BeanNotFoundException> { container.get(Unregistered::class, null) }

        assertTrue(
            error.isMissingBindingFailure(),
            "a missing bean must read as a missing binding, not as a failed one: " +
                "${error::class.simpleName}: ${error.message}",
        )
    }

    @Test
    fun `the in-memory container's missing-bean exception is classified as a missing binding`() {
        val engine = TestBeanEngine()
        val container = engine.start(emptyList(), allowOverrides = true)

        val error = assertFailsWith<BeanNotFoundException> { container.get(Unregistered::class, null) }

        assertTrue(
            error.isMissingBindingFailure(),
            "a missing bean must read as a missing binding, not as a failed one: " +
                "${error::class.simpleName}: ${error.message}",
        )
    }

    @Test
    fun `a binding that exists and throws is not classified as a missing binding`() {
        // The other half: downgrading this one is how a broken bean becomes a silent null.
        val error = IllegalStateException("Could not create instance for 'PaymentService'")

        assertTrue(
            !error.isMissingBindingFailure(),
            "a binding that failed while producing its instance must stay a failure",
        )
    }
}
