package io.github.darkryh.katalyst.di.internal

import io.github.darkryh.katalyst.di.analysis.KnownPlatformTypes
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The registrar's well-known property injection and the dependency analyzer must agree on which
 * class `SchedulerService` is. When they disagree, the analyzer reports a `var scheduler:
 * SchedulerService` property as satisfied and the registrar never assigns it, so the property
 * throws `UninitializedPropertyAccessException` on first use.
 */
class AutoBindingRegistrarSchedulerContractTest {

    @Test
    fun `registrar resolves the same SchedulerService contract as the dependency analyzer`() {
        val analyzerContract = KnownPlatformTypes.schedulerServiceKClassOrNull()
        assertNotNull(
            analyzerContract,
            "katalyst-scheduler must be on this test runtime classpath for the check to mean anything"
        )

        assertEquals(
            analyzerContract,
            registrarSchedulerContract(),
            "AutoBindingRegistrar must resolve SchedulerService through KnownPlatformTypes"
        )
    }

    private fun registrarSchedulerContract(): KClass<*>? {
        val facade = Class.forName("io.github.darkryh.katalyst.di.internal.AutoBindingRegistrarKt")
        val field = facade.getDeclaredField("schedulerServiceKClass")
        field.isAccessible = true
        return field.get(null) as KClass<*>?
    }
}
