package io.github.darkryh.katalyst.scanner.util

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType

/**
 * Metadata about a discovered method and its parameters.
 *
 * Provides structural information about methods discovered during scanning,
 * including parameter metadata, return type, and suspension/static flags.
 */
data class MethodMetadata(
    val declaringClass: Class<*>,
    val method: KFunction<*>,
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
 * @param index The position of the parameter in the method signature (0-based)
 * @param isOptional Whether the parameter is optional/has a default value
 * @param customMetadata Extensible metadata for custom use cases
 */
data class ParameterMetadata(
    val name: String,
    val type: KType,
    val index: Int = 0,
    val isOptional: Boolean = false,
    val customMetadata: Map<String, Any> = emptyMap()
) {
    /**
     * Gets the simple class name of the parameter type.
     */
    val typeName: String
        get() = getTypeSimpleName(type)

    /**
     * Human-readable description of the parameter.
     *
     * **Example:** `id: Long`
     */
    fun getDescription(): String {
        return "$name: $typeName"
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
