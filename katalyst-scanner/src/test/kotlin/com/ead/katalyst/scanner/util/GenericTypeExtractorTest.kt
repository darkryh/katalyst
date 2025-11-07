package com.ead.katalyst.scanner.util

import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.scanner.fixtures.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenericTypeExtractorTest {

    @Test
    fun `extractTypeArguments returns both generic parameters`() {
        val types = GenericTypeExtractor.extractTypeArguments(
            UserRepository::class.java,
            SampleRepository::class.java
        )

        assertEquals(listOf(User::class.java, UserDTO::class.java), types)
    }

    @Test
    fun `extractTypeArgumentsAsMap preserves parameter names`() {
        val typeMap = GenericTypeExtractor.extractTypeArgumentsAsMap(
            UserRepository::class.java,
            SampleRepository::class.java
        )

        assertEquals(User::class.java, typeMap["E"])
        assertEquals(UserDTO::class.java, typeMap["D"])
    }

    @Test
    fun `extractTypeArgument returns expected parameter`() {
        val eventType = GenericTypeExtractor.extractTypeArgument(
            UserCreatedEventHandler::class.java,
            EventHandler::class.java,
            position = 0
        )

        assertEquals(UserCreatedEvent::class.java, eventType)
    }

    @Test
    fun `extractTypeArgument throws when index out of bounds`() {
        assertFailsWith<IndexOutOfBoundsException> {
            GenericTypeExtractor.extractTypeArgument(
                UserRepository::class.java,
                SampleRepository::class.java,
                position = 5
            )
        }
    }

    @Test
    fun `extractTypeArguments works for different repositories`() {
        val productTypes = GenericTypeExtractor.extractTypeArguments(
            ProductRepository::class.java,
            SampleRepository::class.java
        )

        assertEquals(listOf(Product::class.java, ProductDTO::class.java), productTypes)
    }

    @Test
    fun `extractTypeArgumentsAsKClass returns KClass list`() {
        val kClasses = GenericTypeExtractor.extractTypeArgumentsAsKClass(
            UserRepository::class.java,
            SampleRepository::class.java
        )

        assertEquals(listOf(User::class, UserDTO::class), kClasses)
    }

    @Test
    fun `hasTypeArguments detects generics`() {
        assertTrue(
            GenericTypeExtractor.hasTypeArguments(
                UserRepository::class.java,
                SampleRepository::class.java
            )
        )
        assertFalse(
            GenericTypeExtractor.hasTypeArguments(
                PlainEntity::class.java,
                SampleRepository::class.java
            )
        )
    }

    @Test
    fun `getTypeDescription formats readable output`() {
        val description = GenericTypeExtractor.getTypeDescription(
            UserRepository::class.java,
            SampleRepository::class.java
        )

        assertEquals("SampleRepository<User, UserDTO>", description)
    }

    @Test
    fun `extractTypeArguments handles nested hierarchies`() {
        val types = GenericTypeExtractor.extractTypeArguments(
            ConcreteRepository::class.java,
            SampleRepository::class.java
        )

        assertEquals(listOf(User::class.java, UserDTO::class.java), types)
    }

    @Test
    fun `extractTypeArguments works with additional interfaces`() {
        val types = GenericTypeExtractor.extractTypeArguments(
            NestedRepository::class.java,
            SampleRepository::class.java
        )

        assertEquals(listOf(User::class.java, UserDTO::class.java), types)
    }
}
