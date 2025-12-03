package io.github.darkryh.katalyst.scanner.scanner

import io.github.darkryh.katalyst.scanner.util.MethodMetadata
import io.github.darkryh.katalyst.scanner.util.ParameterMetadata
import org.slf4j.LoggerFactory
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod

/**
 * Scanner for discovering methods declared on classes that implement a given base type.
 *
 * The scanner inspects declared Kotlin member functions (excluding those inherited from `Any`)
 * and returns structured metadata including parameters, return type, and suspend/static flags.
 *
 * @param T The base type or marker interface
 */
class KotlinMethodScanner<T> {

    private val logger = LoggerFactory.getLogger(KotlinMethodScanner::class.java)

    /**
     * Discovers all methods in a set of classes.
     *
     * @param classes Set of classes to scan
     * @param filter Optional predicate to filter methods (true = include)
     * @return List of discovered method metadata
     */
    fun discoverMethods(
        classes: Set<Class<out T>>,
        filter: (MethodMetadata) -> Boolean = { true }
    ): List<MethodMetadata> {
        logger.info("🔍 Scanning {} class(es) for methods...", classes.size)

        val methods = mutableListOf<MethodMetadata>()

        classes.forEach { clazz ->
            try {
                val classMethods = discoverMethodsInClass(clazz, filter)
                methods.addAll(classMethods)
                logger.debug("  └─ Found {} method(s) in {}", classMethods.size, clazz.simpleName)
            } catch (e: Exception) {
                logger.warn("  ⚠️ Error scanning methods in {}: {}", clazz.simpleName, e.message)
            }
        }

        logger.info("✓ Found {} method(s) total", methods.size)
        return methods
    }

    /**
     * Discovers all methods in a specific class.
     *
     * @param clazz The class to scan
     * @param filter Optional predicate to filter methods (true = include)
     * @return List of discovered method metadata
     */
    fun discoverMethodsInClass(
        clazz: Class<out T>,
        filter: (MethodMetadata) -> Boolean = { true }
    ): List<MethodMetadata> {
        val methods = mutableListOf<MethodMetadata>()

        try {
            val kotlinClass = clazz.kotlin

            // Get member functions declared in the target class (not inherited from Any)
            // Use Java reflection to get only declared methods, then filter to declared functions
            val declaredMethods = clazz.declaredMethods.map { it.name }.toSet()

            kotlinClass.memberFunctions
                .filter { it.name in declaredMethods }
                .forEach { function ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val metadata = createMethodMetadata(clazz as Class<T>, function)
                    if (filter(metadata)) {
                        methods.add(metadata)
                    }
                } catch (e: Exception) {
                    logger.debug("Could not create metadata for method {}: {}", function.name, e.message)
                }
            }
        } catch (e: Exception) {
            logger.warn("Error scanning methods in class {}: {}", clazz.simpleName, e.message)
        }

        return methods
    }

    /**
     * Finds a specific method by name.
     *
     * @param classes Set of classes to search
     * @param methodName The method name to find
     * @return The method metadata if found, null otherwise
     */
    fun findMethodByName(
        classes: Set<Class<out T>>,
        methodName: String
    ): MethodMetadata? {
        return discoverMethods(classes).firstOrNull { it.name == methodName }
    }

    /**
     * Groups discovered methods by their declaring class.
     *
     * **Example:**
     * ```kotlin
     * val groupedMethods = methodScanner.discoverMethodsGroupedByClass(classes)
     * groupedMethods.forEach { (clazz, methods) ->
     *     println("Class: ${clazz.simpleName}")
     *     methods.forEach { method ->
     *         println("  - ${method.getSignature()}")
     *     }
     * }
     * ```
     *
     * @param classes Set of classes to scan
     * @param filter Optional predicate to filter methods
     * @return Map of class to its methods
     */
    fun discoverMethodsGroupedByClass(
        classes: Set<Class<out T>>,
        filter: (MethodMetadata) -> Boolean = { true }
    ): Map<Class<out T>, List<MethodMetadata>> {
        val grouped = mutableMapOf<Class<out T>, MutableList<MethodMetadata>>()

        classes.forEach { clazz ->
            val methods = discoverMethodsInClass(clazz, filter)
            if (methods.isNotEmpty()) {
                grouped[clazz] = methods.toMutableList()
            }
        }

        return grouped
    }

    // ==================== Internal Implementation ====================

    /**
     * Creates metadata for a KFunction.
     */
    private fun createMethodMetadata(
        declaringClass: Class<T>,
        function: KFunction<*>
    ): MethodMetadata {
        val parameters = extractParameterMetadata(function)
        val isSuspend = function.isSuspend
        val returnType = function.returnType

        return MethodMetadata(
            declaringClass = declaringClass,
            method = function,
            parameters = parameters,
            returnType = returnType,
            isSuspend = isSuspend,
            isStatic = false  // All member functions are instance-level
        )
    }

    /**
     * Extracts metadata for all parameters of a function.
     */
    private fun extractParameterMetadata(function: KFunction<*>): List<ParameterMetadata> {
        return function.parameters
            .filter { it.name != null }  // Skip implicit parameters
            .mapIndexed { index, kParam ->
                ParameterMetadata(
                    name = kParam.name!!,
                    type = kParam.type,
                    index = index,
                    isOptional = kParam.isOptional
                )
            }
    }
}
