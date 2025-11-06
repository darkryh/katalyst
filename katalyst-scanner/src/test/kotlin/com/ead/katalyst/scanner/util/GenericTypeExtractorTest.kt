package com.ead.katalyst.scanner.util

import com.ead.katalyst.scanner.fixtures.*
import com.ead.katalyst.repositories.*
import com.ead.katalyst.common.*
import com.ead.katalyst.events.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

/**
 * Comprehensive tests for GenericTypeExtractor.
 *
 * Tests:
 * - Generic type argument extraction
 * - Multiple type parameters
 * - Single type parameters
 * - Type parameter mapping
 * - Edge cases and error handling
 */
class GenericTypeExtractorTest {

    @Test
    fun `should extract multiple type arguments from generic class`() {
        val types = GenericTypeExtractor.extractTypeArguments(
            UserRepository::class.java,
            Repository::class.java
        )

        assertEquals(2, types.size)
        assertEquals(User::class.java, types[0])
        assertEquals(UserDTO::class.java, types[1])
    }

    @Test
    fun `should extract type arguments as map with parameter names`() {
        val typeMap = GenericTypeExtractor.extractTypeArgumentsAsMap(
            UserRepository::class.java,
            Repository::class.java
        )

        assertEquals(2, typeMap.size)
        assertEquals(User::class.java, typeMap["E"])
        assertEquals(UserDTO::class.java, typeMap["D"])
    }

    @Test
    fun `should extract specific type argument by position`() {
        val entityType = GenericTypeExtractor.extractTypeArgument(
            UserRepository::class.java,
            Repository::class.java,
            0
        )

        assertEquals(User::class.java, entityType)
    }

    @Test
    fun `should extract second type argument by position`() {
        val dtoType = GenericTypeExtractor.extractTypeArgument(
            UserRepository::class.java,
            Repository::class.java,
            1
        )

        assertEquals(UserDTO::class.java, dtoType)
    }

    @Test
    fun `should throw IndexOutOfBoundsException for invalid position`() {
        assertFailsWith<IndexOutOfBoundsException> {
            GenericTypeExtractor.extractTypeArgument(
                UserRepository::class.java,
                Repository::class.java,
                10  // Out of bounds
            )
        }
    }

    @Test
    fun `should extract different repositories correctly`() {
        // Product repository
        val productTypes = GenericTypeExtractor.extractTypeArguments(
            ProductRepository::class.java,
            Repository::class.java
        )

        assertEquals(2, productTypes.size)
        assertEquals(Product::class.java, productTypes[0])
        assertEquals(ProductDTO::class.java, productTypes[1])
    }

    @Test
    fun `should extract single type parameter`() {
        val eventType = GenericTypeExtractor.extractTypeArgument(
            UserCreatedEventHandler::class.java,
            EventHandler::class.java,
            0
        )

        assertEquals(UserCreatedEvent::class.java, eventType)
    }

    @Test
    fun `should extract different single type parameters`() {
        val orderType = GenericTypeExtractor.extractTypeArgument(
            OrderPlacedEventHandler::class.java,
            EventHandler::class.java,
            0
        )

        assertEquals(OrderPlacedEvent::class.java, orderType)
    }

    @Test
    fun `should extract type arguments as KClass list`() {
        val kClasses = GenericTypeExtractor.extractTypeArgumentsAsKClass(
            UserRepository::class.java,
            Repository::class.java
        )

        assertEquals(2, kClasses.size)
        assertEquals(User::class, kClasses[0])
        assertEquals(UserDTO::class, kClasses[1])
    }

    @Test
    fun `should return empty list for non-generic class`() {
        val types = GenericTypeExtractor.extractTypeArguments(
            User::class.java,
            Repository::class.java
        )

        assertTrue(types.isEmpty())
    }

    @Test
    fun `should detect when class has type arguments`() {
        assertTrue(
            GenericTypeExtractor.hasTypeArguments(
                UserRepository::class.java,
                Repository::class.java
            )
        )
    }

    @Test
    fun `should detect when class does not have type arguments`() {
        assertFalse(
            GenericTypeExtractor.hasTypeArguments(
                User::class.java,
                Repository::class.java
            )
        )
    }

    @Test
    fun `should generate readable type description`() {
        val description = GenericTypeExtractor.getTypeDescription(
            UserRepository::class.java,
            Repository::class.java
        )

        assertEquals("Repository<User, UserDTO>", description)
    }

    @Test
    fun `should return empty list if base type not assignable`() {
        val types = GenericTypeExtractor.extractTypeArguments(
            User::class.java,
            Repository::class.java  // User doesn't implement Repository
        )

        assertTrue(types.isEmpty())
    }

    @Test
    fun `should work with nested generic hierarchies`() {
        val types = GenericTypeExtractor.extractTypeArguments(
            ConcreteRepository::class.java,
            Class.forName("com.ead.katalyst.repositories.Repository")
        )

        assertEquals(2, types.size)
        assertEquals(User::class.java, types[0])
        assertEquals(UserDTO::class.java, types[1])
    }

    @Test
    fun `should work with complex nested generics`() {
        val types = GenericTypeExtractor.extractTypeArguments(
            NestedRepository::class.java,
            Class.forName("com.ead.katalyst.repositories.Repository")
        )

        assertEquals(2, types.size)
        assertEquals(User::class.java, types[0])
        assertEquals(UserDTO::class.java, types[1])
    }

    @Test
    fun `should extract type parameters from interface implementations`() {
        val stringType = GenericTypeExtractor.extractTypeArgument(
            StringHolder::class.java,
            SingleTypeGeneric::class.java,
            0
        )

        assertEquals(String::class.java, stringType)
    }

    @Test
    fun `should extract different type parameters from different implementations`() {
        val intType = GenericTypeExtractor.extractTypeArgument(
            IntHolder::class.java,
            SingleTypeGeneric::class.java,
            0
        )

        // Java generics always use boxed types, so Int becomes java.lang.Integer
        assertEquals(Integer::class.java, intType)
    }

    @Test
    fun `should extract type parameter map correctly`() {
        val typeMap = GenericTypeExtractor.extractTypeArgumentsAsMap(
            UserRepository::class.java,
            Repository::class.java
        )

        assertEquals(User::class.java, typeMap["E"])
        assertEquals(UserDTO::class.java, typeMap["D"])
        assertEquals(2, typeMap.size)
    }

    @Test
    fun `should handle multiple repositories with different types`() {
        val userTypes = GenericTypeExtractor.extractTypeArguments(
            UserRepository::class.java,
            Repository::class.java
        )
        val productTypes = GenericTypeExtractor.extractTypeArguments(
            ProductRepository::class.java,
            Repository::class.java
        )

        assertEquals(User::class.java, userTypes[0])
        assertEquals(UserDTO::class.java, userTypes[1])
        assertEquals(Product::class.java, productTypes[0])
        assertEquals(ProductDTO::class.java, productTypes[1])
    }
}
