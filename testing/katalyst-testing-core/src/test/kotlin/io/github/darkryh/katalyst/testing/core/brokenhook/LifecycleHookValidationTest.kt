package io.github.darkryh.katalyst.testing.core.brokenhook

import io.github.darkryh.katalyst.di.error.MissingDependencyError
import io.github.darkryh.katalyst.di.exception.FatalDependencyValidationException
import io.github.darkryh.katalyst.testing.core.katalystTestEnvironment
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Lifecycle hooks take part in dependency validation like any other component.
 *
 * A hook with an unresolvable constructor dependency must fail the bootstrap with a
 * diagnostic naming the hook — not be silently skipped, which is what happened before
 * hooks were scanned as their own discovery category.
 */
class LifecycleHookValidationTest {

    @Test
    fun `hook with an unresolvable dependency fails bootstrap and names the hook`() {
        val error = runCatching {
            katalystTestEnvironment {
                scan("io.github.darkryh.katalyst.testing.core.brokenhook")
                disableScheduler()
            }.use { }
        }.exceptionOrNull()

        assertNotNull(error, "bootstrap should fail when a hook's dependency cannot be resolved")

        val validationFailure = generateSequence(error) { it.cause }
            .filterIsInstance<FatalDependencyValidationException>()
            .firstOrNull()

        assertNotNull(
            validationFailure,
            "hook failures should surface as FatalDependencyValidationException, was: " +
                generateSequence(error) { it.cause }.joinToString(" | ") { it::class.simpleName.orEmpty() }
        )

        val missing = validationFailure.validationErrors
            .filterIsInstance<MissingDependencyError>()
            .filter { it.component == UnsatisfiableStartupHook::class }

        assertTrue(
            missing.isNotEmpty(),
            "validation should report the hook's missing dependency. Report:\n" +
                validationFailure.generateSummaryReport()
        )
        assertTrue(
            missing.any { it.requiredType == UnregisteredCollaborator::class },
            "validation should name the unresolvable dependency type. Report:\n" +
                validationFailure.generateSummaryReport()
        )
    }
}
