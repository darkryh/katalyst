package com.ead.katalyst.scanner.util

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType

/**
 * Metadata about a discovered method with its annotations and parameters.
 *
 * This provides detailed information about methods found during scanning,
 * including annotations, parameters, return type, and whether it's suspend.
 *
 * **Usage:**
 * ```kotlin
 * val methodMetadata = MethodMetadata(
 *     declaringClass = UserController::class.java,
 *     method = UserController::getUser,
 *     annotations = listOf(RouteHandler(...)),
 *     parameters = listOf(
 *         ParameterMetadata("id", Long::class, listOf(PathVariable(...)))
 *     ),
 *     returnType = UserDTO::class,
 *     isSuspend = true
 * )
 * ```
 *
 * @param declaringClass The class that declares this method
 * @param method The KFunction representing the method
 * @param annotations Annotations on the method
 * @param parameters List of method parameters with their metadata
 * @param returnType The return type of the method
 * @param isSuspend Whether this is a suspend function
 * @param isStatic Whether this is a static/object method
 * @param customMetadata Extensible metadata for custom use cases
 */
data class MethodMetadata(
    val declaringClass: Class<*>,
    val method: KFunction<*>,
    val annotations: List<Annotation> = emptyList(),
    val parameters: List<ParameterMetadata> = emptyList(),
    val returnType: KType? = null,
    val isSuspend: Boolean = false,
    val isStatic: Boolean = false,
    val customMetadata: Map<String, Any> = emptyMap()
) {
    /**
     * Gets the simple name of the method.
     */
    val name: String
        get() = method.name

    /**
     * Checks if the method has a specific annotation.
     */
    fun hasAnnotation(annotation: Annotation): Boolean {
        return annotations.any { it::class == annotation::class }
    }

    /**
     * Finds the first annotation of a given type.
     */
    inline fun <reified T : Annotation> findAnnotation(): T? {
        return annotations.filterIsInstance<T>().firstOrNull()
    }

    /**
     * Gets all annotations of a given type.
     */
    inline fun <reified T : Annotation> findAnnotations(): List<T> {
        return annotations.filterIsInstance<T>()
    }

    /**
     * Checks if this method has parameters.
     */
    fun hasParameters(): Boolean {
        return parameters.isNotEmpty()
    }

    /**
     * Finds a parameter by name.
     */
    fun findParameter(name: String): ParameterMetadata? {
        return parameters.firstOrNull { it.name == name }
    }

    /**
     * Gets all parameters with a specific annotation.
     */
    inline fun <reified T : Annotation> findParametersWithAnnotation(): List<ParameterMetadata> {
        return parameters.filter { it.findAnnotation<T>() != null }
    }

    /**
     * Human-readable description of the method signature.
     *
     * **Example:** `fun getUser(id: Long): UserDTO`
     */
    fun getSignature(): String {
        val suspend = if (isSuspend) "suspend " else ""
        val params = parameters.joinToString(", ") { "${it.name}: ${it.typeName}" }
        val returnTypeName = returnType?.let { getTypeSimpleName(it) } ?: "Unit"
        return "${suspend}fun ${name}($params): $returnTypeName"
    }
}

/**
 * Metadata about a method parameter with its type and annotations.
 *
 * **Usage:**
 * ```kotlin
 * val paramMetadata = ParameterMetadata(
 *     name = "id",
 *     type = Long::class.createType(),
 *     annotations = listOf(PathVariable("id")),
 *     index = 0
 * )
 * ```
 *
 * @param name The parameter name
 * @param type The parameter type
 * @param annotations Annotations on the parameter
 * @param index The position of the parameter in the method signature (0-based)
 * @param isOptional Whether the parameter is optional/has a default value
 * @param customMetadata Extensible metadata for custom use cases
 */
data class ParameterMetadata(
    val name: String,
    val type: KType,
    val annotations: List<Annotation> = emptyList(),
    val index: Int = 0,
    val isOptional: Boolean = false,
    val customMetadata: Map<String, Any> = emptyMap()
) {
    /**
     * Checks if the parameter has a specific annotation.
     */
    fun hasAnnotation(annotation: Annotation): Boolean {
        return annotations.any { it::class == annotation::class }
    }

    /**
     * Finds the first annotation of a given type.
     */
    inline fun <reified T : Annotation> findAnnotation(): T? {
        return annotations.filterIsInstance<T>().firstOrNull()
    }

    /**
     * Gets all annotations of a given type.
     */
    inline fun <reified T : Annotation> findAnnotations(): List<T> {
        return annotations.filterIsInstance<T>()
    }

    /**
     * Gets the simple class name of the parameter type.
     */
    val typeName: String
        get() = getTypeSimpleName(type)

    /**
     * Human-readable description of the parameter.
     *
     * **Example:** `id: Long @PathVariable("id")`
     */
    fun getDescription(): String {
        val annotationStr = if (annotations.isNotEmpty()) {
            " @${annotations.map { it::class.simpleName }.joinToString(" @")}"
        } else {
            ""
        }
        return "$name: $typeName$annotationStr"
    }
}

/**
 * Helper function to get the simple name of a KType.
 */
private fun getTypeSimpleName(type: KType): String {
    return when (val classifier = type.classifier) {
        is KClass<*> -> classifier.simpleName ?: "?"
        else -> type.toString()
    }
}
