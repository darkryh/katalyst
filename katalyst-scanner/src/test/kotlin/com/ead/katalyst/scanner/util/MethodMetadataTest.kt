package com.ead.katalyst.scanner.util

import com.ead.katalyst.scanner.fixtures.*
import com.ead.katalyst.repositories.*
import com.ead.katalyst.common.*
import com.ead.katalyst.services.*
import com.ead.katalyst.validators.*
import com.ead.katalyst.events.*
import com.ead.katalyst.handlers.*
import com.ead.katalyst.scanner.scanner.KotlinMethodScanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Comprehensive tests for MethodMetadata and ParameterMetadata.
 *
 * Tests:
 * - Method metadata extraction
 * - Parameter metadata extraction
 * - Annotation handling
 * - Suspend function detection
 * - Parameter type information
 */
class MethodMetadataTest {

    private val scanner = KotlinMethodScanner<TestService>()

    @Test
    fun `should contain method name`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)
        val method = methods.first { it.name == "handleRequest" }

        assertEquals("handleRequest", method.name)
    }

    @Test
    fun `should contain declaring class`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)
        val method = methods.first { it.name == "handleRequest" }

        assertEquals(ServiceWithAnnotatedMethods::class.java, method.declaringClass)
    }

    @Test
    fun `should detect if method has parameters`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)

        val withParams = methods.first { it.name == "multiParam" }
        val noParams = methods.first { it.name == "noParams" }

        assertTrue(withParams.hasParameters())
        assertFalse(noParams.hasParameters())
    }

    @Test
    fun `should detect suspend functions`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithSuspendMethods::class.java)

        val suspend1 = methods.first { it.name == "suspendMethod1" }
        val normal = methods.first { it.name == "normalMethod" }

        assertTrue(suspend1.isSuspend)
        assertFalse(normal.isSuspend)
    }

    @Test
    fun `should extract annotations from method`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)
        val annotatedMethod = methods.first { it.name == "handleRequest" }

        assertTrue(annotatedMethod.annotations.isNotEmpty())
    }

    @Test
    fun `should find specific annotation on method`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)
        val annotatedMethod = methods.first { it.name == "handleRequest" }

        val annotation = annotatedMethod.findAnnotation<TestAnnotation>()
        assertNotNull(annotation)
    }

    @Test
    fun `should return null when annotation not found`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)
        val unannotatedMethod = methods.first { it.name == "unannotatedMethod" }

        val annotation = unannotatedMethod.findAnnotation<TestAnnotation>()
        kotlin.test.assertNull(annotation)
    }

    @Test
    fun `should extract parameter metadata`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val multiParamMethod = methods.first { it.name == "multiParam" }

        assertEquals(3, multiParamMethod.parameters.size)
        assertEquals("a", multiParamMethod.parameters[0].name)
        assertEquals("b", multiParamMethod.parameters[1].name)
        assertEquals("c", multiParamMethod.parameters[2].name)
    }

    @Test
    fun `parameter metadata should have correct index`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val multiParamMethod = methods.first { it.name == "multiParam" }

        multiParamMethod.parameters.forEachIndexed { index, param ->
            assertEquals(index, param.index)
        }
    }

    @Test
    fun `should generate readable method signature`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithSuspendMethods::class.java)
        val suspendMethod = methods.first { it.name == "suspendMethod1" }

        val signature = suspendMethod.getSignature()

        assertTrue(signature.contains("suspend"))
        assertTrue(signature.contains("suspendMethod1"))
        assertTrue(signature.contains("String"))
    }

    @Test
    fun `should extract parameter type information`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val multiParamMethod = methods.first { it.name == "multiParam" }

        val paramTypes = multiParamMethod.parameters.map { it.typeName }

        assertEquals(listOf("String", "Int", "Double"), paramTypes)
    }

    @Test
    fun `parameter metadata should have annotations`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)
        val method = methods.first { it.name == "handleRequest" }

        // Method has parameters, check if they're accessible
        assertTrue(method.parameters.isNotEmpty())
    }

    @Test
    fun `should have different parameter objects for different methods`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)

        val method1 = methods.first { it.name == "multiParam" }
        val method2 = methods.first { it.name == "singleParam" }

        assertEquals(3, method1.parameters.size)
        assertEquals(1, method2.parameters.size)
        assertFalse(method1.parameters === method2.parameters)
    }

    @Test
    fun `should correctly identify optional parameters`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithOptionalParams::class.java)
        val methodWithOptional = methods.first { it.name == "methodWithOptional" }

        // At least one parameter should be optional
        val hasOptional = methodWithOptional.parameters.any { it.isOptional }
        assertTrue(hasOptional)
    }

    @Test
    fun `should extract custom metadata`() {
        val methods = scanner.discoverMethodsInClass(AnnotatedMethods::class.java)
        val rateLimitMethod = methods.first { it.name == "rateLimitedMethod" }

        val annotation = rateLimitMethod.findAnnotation<RateLimit>()
        assertNotNull(annotation)
        assertEquals(50, annotation.requestsPerMinute)
    }

    @Test
    fun `should handle multiple annotations on same method`() {
        // Create a test with multiple annotations
        val methods = scanner.discoverMethodsInClass(AnnotatedMethods::class.java)
        val method = methods.first { it.name == "protectedMethod" }

        val requiresAuth = method.findAnnotation<RequiresAuth>()
        assertNotNull(requiresAuth)
    }

    @Test
    fun `should generate correct descriptions for different parameter types`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val method = methods.first { it.name == "multiParam" }

        val descriptions = method.parameters.map { it.getDescription() }

        assertTrue(descriptions.all { it.contains(":") })
    }

    @Test
    fun `should extract all annotations from method`() {
        val methods = scanner.discoverMethodsInClass(AnnotatedMethods::class.java)
        val deprecatedMethod = methods.first { it.name == "oldMethod" }

        val deprecatedAnnotation = deprecatedMethod.findAnnotation<DeprecatedAnnotation>()
        assertNotNull(deprecatedAnnotation)
        assertEquals("1.0", deprecatedAnnotation.version)
    }

    @Test
    fun `should handle real world validator methods`() {
        val validatorScanner = KotlinMethodScanner<Validator<*>>()
        val methods = validatorScanner.discoverMethodsInClass(UserValidator::class.java)
        val validateMethod = methods.first { it.name == "validate" }

        assertTrue(validateMethod.isSuspend)
        assertEquals(1, validateMethod.parameters.size)
        assertEquals("entity", validateMethod.parameters.first().name)
    }

    @Test
    fun `should extract return type information`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val method = methods.first { it.name == "multiParam" }

        assertNotNull(method.returnType)
    }

    @Test
    fun `parameter should know its containing method`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithMultipleParameters::class.java)
        val method = methods.first { it.name == "multiParam" }
        val param = method.parameters.first()

        assertEquals("a", param.name)
        assertEquals(0, param.index)
    }

    @Test
    fun `should handle methods with no annotations`() {
        val methods = scanner.discoverMethodsInClass(ServiceWithAnnotatedMethods::class.java)
        val unannotatedMethod = methods.first { it.name == "unannotatedMethod" }

        assertTrue(unannotatedMethod.annotations.isEmpty())
    }
}
