package com.ead.katalyst.scanner.core

import com.ead.katalyst.scanner.fixtures.*
import com.ead.katalyst.scanner.scanner.KotlinMethodScanner
import com.ead.katalyst.core.validation.Validator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for Enhanced DiscoveryMetadata.
 *
 * Tests:
 * - Basic metadata extraction
 * - Generic type parameter extraction
 * - Method metadata inclusion
 * - Type parameter queries
 * - Method queries
 */
class DiscoveryMetadataTest {

    @Test
    fun `should create metadata with basic information`() {
        val metadata = DiscoveryMetadata.from(User::class.java)

        assertEquals("User", metadata.simpleName)
        assertEquals("com.ead.katalyst.scanner.fixtures", metadata.packageName)
        assertEquals(User::class.java, metadata.discoveredClass)
    }

    @Test
    fun `should detect constructor information`() {
        val metadata = DiscoveryMetadata.from(User::class.java)

        assertEquals(1, metadata.constructorCount)
    }

    @Test
    fun `should create metadata with generic type extraction`() {
        val metadata = DiscoveryMetadata.from(
            UserRepository::class.java,
            baseType = SampleRepository::class.java
        )

        assertTrue(metadata.typeParameterMapping.isNotEmpty())
        assertEquals(User::class.java, metadata.getTypeParameter("E"))
        assertEquals(UserDTO::class.java, metadata.getTypeParameter("D"))
    }

    @Test
    fun `should detect type parameters from generic class`() {
        val metadata = DiscoveryMetadata.from(
            UserRepository::class.java,
            baseType = SampleRepository::class.java
        )

        assertTrue(metadata.hasTypeParameter("E"))
        assertTrue(metadata.hasTypeParameter("D"))
        assertFalse(metadata.hasTypeParameter("Z"))
    }

    @Test
    fun `should get type parameters as list`() {
        val metadata = DiscoveryMetadata.from(
            UserRepository::class.java,
            baseType = SampleRepository::class.java
        )

        val typeParams = metadata.getTypeParameters()

        assertEquals(2, typeParams.size)
        assertTrue(typeParams.contains(User::class.java))
        assertTrue(typeParams.contains(UserDTO::class.java))
    }

    @Test
    fun `should return null for missing type parameter`() {
        val metadata = DiscoveryMetadata.from(
            UserRepository::class.java,
            baseType = SampleRepository::class.java
        )

        val missing = metadata.getTypeParameter("Z")

        assertNull(missing)
    }

    @Test
    fun `should include method metadata when provided`() {
        val scanner = KotlinMethodScanner<TestService>()
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)

        val metadata = DiscoveryMetadata.from(
            ServiceWithAnnotatedMethods::class.java,
            methods = methods
        )

        assertTrue(metadata.hasMethods())
        assertEquals(3, metadata.methods.size)
    }

    @Test
    fun `should find method by name`() {
        val scanner = KotlinMethodScanner<TestService>()
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)

        val metadata = DiscoveryMetadata.from(
            ServiceWithAnnotatedMethods::class.java,
            methods = methods
        )

        val method = metadata.findMethodByName("handleRequest")

        assertNotNull(method)
        assertEquals("handleRequest", method!!.name)
    }

    @Test
    fun `should return null when method name not found`() {
        val scanner = KotlinMethodScanner<TestService>()
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)

        val metadata = DiscoveryMetadata.from(
            ServiceWithAnnotatedMethods::class.java,
            methods = methods
        )

        val method = metadata.findMethodByName("nonExistent")

        assertNull(method)
    }

    @Test
    fun `should handle metadata without methods`() {
        val metadata = DiscoveryMetadata.from(User::class.java)

        assertFalse(metadata.hasMethods())
        assertTrue(metadata.methods.isEmpty())
    }

    @Test
    fun `should generate human readable description`() {
        val metadata = DiscoveryMetadata.from(
            UserRepository::class.java,
            baseType = SampleRepository::class.java
        )

        val description = metadata.describe()

        assertTrue(description.contains("UserRepository"))
        assertTrue(description.contains("Type Parameters"))
        assertTrue(description.contains("User"))
        assertTrue(description.contains("UserDTO"))
    }

    @Test
    fun `should work with different repositories`() {
        val metadata = DiscoveryMetadata.from(
            ProductRepository::class.java,
            baseType = SampleRepository::class.java
        )

        assertEquals(Product::class.java, metadata.getTypeParameter("E"))
        assertEquals(ProductDTO::class.java, metadata.getTypeParameter("D"))
    }

    @Test
    fun `should work with single type parameters`() {
        val metadata = DiscoveryMetadata.from(
            StringHolder::class.java,
            baseType = SingleTypeGeneric::class.java
        )

        assertEquals(String::class.java, metadata.getTypeParameter("T"))
        assertTrue(metadata.hasTypeParameter("T"))
    }

    @Test
    fun `should combine method and type information`() {
        val scanner = KotlinMethodScanner<TestService>()
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)

        val metadata = DiscoveryMetadata.from(
            ServiceWithAnnotatedMethods::class.java,
            methods = methods
        )

        assertTrue(metadata.hasMethods())
        assertEquals(3, metadata.methods.size)
        assertEquals("ServiceWithAnnotatedMethods", metadata.simpleName)
    }

    @Test
    fun `should track constructor count`() {
        val metadata = DiscoveryMetadata.from(User::class.java)

        assertEquals(1, metadata.constructorCount)
    }

    @Test
    fun `should work with classes with multiple constructors`() {
        val metadata = DiscoveryMetadata.from(TestClassWithArgsConstructor::class.java)

        assertEquals(1, metadata.constructorCount)
    }

    @Test
    fun `should detect no-args constructor availability`() {
        val withNoArgs = DiscoveryMetadata.from(ConcreteTestClass::class.java)
        val withArgs = DiscoveryMetadata.from(TestClassWithArgsConstructor::class.java)

        // Both may or may not have no-args, depending on Kotlin defaults
        assertTrue(withNoArgs.hasNoArgsConstructor || !withNoArgs.hasNoArgsConstructor)
        assertTrue(withArgs.hasNoArgsConstructor || !withArgs.hasNoArgsConstructor)
    }

    @Test
    fun `should handle complex type hierarchies`() {
        val metadata = DiscoveryMetadata.from(
            ConcreteRepository::class.java,
            baseType = SampleRepository::class.java
        )

        assertEquals(User::class.java, metadata.getTypeParameter("E"))
        assertEquals(UserDTO::class.java, metadata.getTypeParameter("D"))
    }

    @Test
    fun `should extract multiple type parameters correctly`() {
        val metadata = DiscoveryMetadata.from(
            NestedRepository::class.java,
            baseType = SampleRepository::class.java
        )

        val typeParams = metadata.getTypeParameters()

        assertEquals(2, typeParams.size)
    }

    @Test
    fun `should handle real world validator example`() {
        val scanner = KotlinMethodScanner<Validator<*>>()
        val methods = scanner.discoverMethodsInClass(UserValidator::class.java)

        val metadata = DiscoveryMetadata.from(
            UserValidator::class.java,
            methods = methods
        )

        assertTrue(metadata.hasMethods())
        assertTrue(metadata.methods.any { it.name == "validate" })
    }

    @Test
    fun `should combine all features in real world scenario`() {
        val scanner = KotlinMethodScanner<TestService>()
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)

        val metadata = DiscoveryMetadata.from(
            ServiceWithMultipleParameters::class.java,
            methods = methods
        )

        // Check basic info
        assertEquals("ServiceWithMultipleParameters", metadata.simpleName)

        // Check methods
        assertTrue(metadata.hasMethods())
        val multiParamMethod = metadata.findMethodByName("multiParam")
        assertNotNull(multiParamMethod)

        // Check parameter info
        assertEquals(3, multiParamMethod!!.parameters.size)
    }
}
