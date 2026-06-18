package io.github.darkryh.katalyst.core.di

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KatalystContainerFactoriesTest {
    @Test
    fun `factory lookup fails clearly when no adapter supports native container`() {
        val error = assertFailsWith<IllegalStateException> {
            KatalystContainerFactories.create(UnsupportedNativeContainer)
        }

        assertTrue(error.message.orEmpty().contains("No Katalyst DI adapter is installed"))
        assertTrue(error.message.orEmpty().contains("katalyst-koin-bean"))
    }

    private object UnsupportedNativeContainer
}
