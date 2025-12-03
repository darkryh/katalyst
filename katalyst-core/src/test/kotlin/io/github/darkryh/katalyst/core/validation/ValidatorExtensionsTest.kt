package io.github.darkryh.katalyst.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class ValidatorExtensionsTest {

    private class SampleValidator : Validator<String> {
        override suspend fun validate(entity: String): ValidationResult {
            return if (entity.isBlank()) {
                ValidationResult.invalid("blank")
            } else {
                ValidationResult.valid()
            }
        }
    }

    @Test
    fun `getValidationErrors returns errors from validate`() = runBlocking {
        val validator = SampleValidator()

        val errors = validator.getValidationErrors("")

        assertEquals(listOf("blank"), errors)
    }
}
