package com.ead.katalyst.scanner.scanner

import com.ead.katalyst.scanner.fixtures.*
import com.ead.katalyst.validators.Validator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for KotlinMethodScanner.
 *
 * Tests:
 * - Method discovery in classes
 * - Method parameter extraction
 * - Suspend function detection
 * - Grouping and filtering
 */
class KotlinMethodScannerTest {

    private val scanner = KotlinMethodScanner<TestService>()

    @Test
    fun `should discover all methods in class`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)

        assertEquals(3, methods.size)
    }

    @Test
    fun `should extract method names correctly`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)
        val methodNames = methods.map { it.name }

        assertTrue(methodNames.contains("handleRequest"))
        assertTrue(methodNames.contains("processData"))
        assertTrue(methodNames.contains("unannotatedMethod"))
    }

    @Test
    fun `should detect suspend functions`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithSuspendMethods::class.java)
        val suspendMethods = methods.filter { it.isSuspend }

        assertEquals(2, suspendMethods.size)
    }

    @Test
    fun `should detect non-suspend functions`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithSuspendMethods::class.java)
        val normalMethods = methods.filter { !it.isSuspend }

        assertEquals(1, normalMethods.size)
        assertEquals("normalMethod", normalMethods.first().name)
    }

    @Test
    fun `should extract method parameters`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val multiParamMethod = methods.first { it.name == "multiParam" }

        assertEquals(3, multiParamMethod.parameters.size)
    }

    @Test
    fun `should extract parameter names correctly`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val multiParamMethod = methods.first { it.name == "multiParam" }
        val paramNames = multiParamMethod.parameters.map { it.name }

        assertEquals(listOf("a", "b", "c"), paramNames)
    }

    @Test
    fun `should handle methods with no parameters`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val noParamMethod = methods.first { it.name == "noParams" }

        assertEquals(0, noParamMethod.parameters.size)
        assertFalse(noParamMethod.hasParameters())
    }

    @Test
    fun `should return empty list for class with no methods`() {
        val methods = scanner.discoverMethodsInClass(EmptyService::class.java)

        assertTrue(methods.isEmpty())
    }

    @Test
    fun `should group methods by declaring class`() {
        val classes = setOf(
            ServiceWithAnnotatedMethods::class.java,
            ServiceWithSuspendMethods::class.java
        )

        val grouped = scanner.discoverMethodsGroupedByClass(classes)

        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey(ServiceWithAnnotatedMethods::class.java))
        assertTrue(grouped.containsKey(ServiceWithSuspendMethods::class.java))
    }

    @Test
    fun `should find method by name`() {
        val classes = setOf(ServiceWithAnnotatedMethods::class.java)

        val method = scanner.findMethodByName(classes, "handleRequest")

        assertNotNull(method)
        assertEquals("handleRequest", method!!.name)
    }

    @Test
    fun `should return null when method not found`() {
        val classes = setOf(ServiceWithAnnotatedMethods::class.java)

        val method = scanner.findMethodByName(classes, "nonExistentMethod")

        kotlin.test.assertNull(method)
    }

    @Test
    fun `should generate method signature correctly`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val multiParamMethod = methods.first { it.name == "multiParam" }

        val signature = multiParamMethod.getSignature()

        assertTrue(signature.contains("multiParam"))
        assertTrue(signature.contains("suspend"))
    }

    @Test
    fun `should discover all methods across multiple classes`() {
        val classes = setOf(
            ServiceWithAnnotatedMethods::class.java,
            ServiceWithSuspendMethods::class.java,
            ServiceWithMultipleParameters::class.java
        )

        val methods = scanner.discoverMethods(classes)

        assertTrue(methods.size > 0)
    }

    @Test
    fun `should filter during discovery`() {
        val classes = setOf(ServiceWithSuspendMethods::class.java)

        val suspendMethods = scanner.discoverMethods(classes) { metadata ->
            metadata.isSuspend
        }

        assertEquals(2, suspendMethods.size)
    }

    @Test
    fun `should discover method in class with optional parameters`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithOptionalParams::class.java)

        assertEquals(2, methods.size)
    }

    @Test
    fun `should handle parameter count with optional parameters`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithOptionalParams::class.java)
        val methodWithOptional = methods.first { it.name == "methodWithOptional" }

        // Should have 2 parameters even though one is optional
        assertEquals(2, methodWithOptional.parameters.size)
    }

    @Test
    fun `should discover methods with annotations in real world validators`() {
        val validatorScanner = KotlinMethodScanner<Validator<*>>()
        val classes = setOf(
            UserValidator::class.java,
            ProductValidator::class.java
        )

        val methods = validatorScanner.discoverMethods(classes)

        // Each validator has 2 methods: validate() and getValidationErrors()
        assertEquals(4, methods.size)
    }

    @Test
    fun `should correctly identify suspend methods in grouped results`() {
        val classes = setOf(ServiceWithSuspendMethods::class.java)

        val grouped = scanner.discoverMethodsGroupedByClass(classes)
        val suspendCount = grouped.values
            .flatMap { it }
            .count { it.isSuspend }

        assertEquals(2, suspendCount)
    }

    @Test
    fun `should extract parameter types correctly`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val multiParamMethod = methods.first { it.name == "multiParam" }

        val paramTypes = multiParamMethod.parameters.map { it.typeName }

        assertEquals(listOf("String", "Int", "Double"), paramTypes)
    }

    @Test
    fun `should handle empty class parameter list`() {
        val classes = emptySet<Class<out TestService>>()

        val methods = scanner.discoverMethods(classes)

        assertTrue(methods.isEmpty())
    }
}
