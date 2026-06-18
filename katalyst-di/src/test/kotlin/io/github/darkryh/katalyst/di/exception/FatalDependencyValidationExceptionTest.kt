package io.github.darkryh.katalyst.di.exception

import io.github.darkryh.katalyst.di.error.MissingDependencyError
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FatalDependencyValidationExceptionTest {
    @Test
    fun `render report returns summary without writing to stdout`() {
        val exception = FatalDependencyValidationException(
            validationErrors = listOf(
                MissingDependencyError(
                    component = NeedsMissingDependency::class,
                    requiredType = MissingDependency::class,
                    parameterName = "missingDependency",
                    isInKoin = false,
                    isDiscoverable = false
                )
            ),
            discoveredTypes = mapOf("services" to setOf(NeedsMissingDependency::class))
        )

        val capturedStdout = ByteArrayOutputStream()
        val originalStdout = System.out
        val report = try {
            System.setOut(PrintStream(capturedStdout))
            exception.renderReport()
        } finally {
            System.setOut(originalStdout)
        }

        assertTrue(report.contains("FATAL DEPENDENCY INJECTION VALIDATION ERROR"))
        assertTrue(report.contains("Missing dependency"))
        assertEquals("", capturedStdout.toString())
    }

    @Test
    fun `summary report references verbose mode without duplicating detailed body`() {
        val exception = FatalDependencyValidationException(
            validationErrors = listOf(
                MissingDependencyError(
                    component = NeedsMissingDependency::class,
                    requiredType = MissingDependency::class,
                    parameterName = "missingDependency",
                    isInKoin = false,
                    isDiscoverable = false
                )
            ),
            discoveredTypes = emptyMap()
        )

        val report = exception.renderReport(verbose = false)

        assertTrue(report.contains("Set KATALYST_DI_VERBOSE=true"))
        assertFalse(report.contains("DISCOVERED COMPONENTS:"))
    }
}

private class NeedsMissingDependency
private class MissingDependency
