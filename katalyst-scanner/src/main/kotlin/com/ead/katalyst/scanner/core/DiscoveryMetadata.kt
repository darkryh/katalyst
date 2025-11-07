package com.ead.katalyst.scanner.core

import com.ead.katalyst.scanner.util.MethodMetadata
import com.ead.katalyst.scanner.util.GenericTypeExtractor

/**
 * Metadata about a discovered type that can be useful for instantiation and dependency injection.
 *
 * This provides additional information discovered about a class that can help with:
 * - Determining if it has no-args constructor
 * - Understanding its dependencies
 * - Package location
 * - Generic type information
 * - Method-level metadata
 *
 * **Features:**
 * - Class-level information: name, package, constructors
 * - Generic type parameters: extracted from superclass/interfaces
 * - Method metadata: discovered methods with their structure
 * - Type parameter mapping: name -> class (e.g., "E" -> User::class.java)
 *
 * @param discoveredClass The class that was discovered
 * @param packageName The package where the class is located
 * @param simpleName The simple name of the class (without package)
 * @param hasNoArgsConstructor Whether the class has a no-args constructor
 * @param constructorCount Number of constructors available
 * @param genericTypeArguments Generic type arguments if applicable
 * @param methods Methods discovered in this class (optional, populated on demand)
 * @param typeParameterMapping Map of type parameter name to actual class (e.g., "E" -> User::class.java)
 * @param customMetadata Extensible map for storing custom metadata
 */
data class DiscoveryMetadata(
    val discoveredClass: Class<*>,
    val packageName: String,
   val simpleName: String,
   val hasNoArgsConstructor: Boolean = false,
   val constructorCount: Int = 0,
   val genericTypeArguments: List<String> = emptyList(),
   val methods: List<MethodMetadata> = emptyList(),
   val typeParameterMapping: Map<String, Class<*>> = emptyMap(),
   val customMetadata: Map<String, Any> = emptyMap()
) {
    /**
     * Checks if the class has any methods.
     */
    fun hasMethods(): Boolean = methods.isNotEmpty()

    /**
     * Finds a method by name.
     */
    fun findMethodByName(name: String): MethodMetadata? {
        return methods.firstOrNull { it.name == name }
    }

    /**
     * Checks if a specific type parameter exists.
     *
     * **Example:**
     * ```kotlin
     * if (metadata.hasTypeParameter("E")) {
     *     val entityClass = metadata.getTypeParameter("E")
     * }
     * ```
     */
    fun hasTypeParameter(paramName: String): Boolean {
        return typeParameterMapping.containsKey(paramName)
    }

    /**
     * Gets a type parameter by name.
     *
     * @param paramName The type parameter name (e.g., "E", "D")
     * @return The actual class for this type parameter, or null if not found
     */
    fun getTypeParameter(paramName: String): Class<*>? {
        return typeParameterMapping[paramName]
    }

    /**
     * Gets all type parameters as a list.
     *
     * **Example:**
     * ```kotlin
     * val types = metadata.getTypeParameters()  // [User::class.java, UserDTO::class.java]
     * ```
     */
    fun getTypeParameters(): List<Class<*>> {
        return typeParameterMapping.values.toList()
    }

    /**
     * Human-readable description of the metadata.
     *
     * **Example:**
     * ```
     * UserRepositoryImpl (com.ead.repositories)
     *   Type Parameters: E=User, D=UserDTO
     *   Methods: 3
     *   Constructors: 1
     * ```
     */
    fun describe(): String {
        val typeParamStr = if (typeParameterMapping.isNotEmpty()) {
            "\n  Type Parameters: ${typeParameterMapping.entries.joinToString(", ") { "${it.key}=${it.value.simpleName}" }}"
        } else {
            ""
        }

        val methodStr = if (methods.isNotEmpty()) {
            "\n  Methods: ${methods.size}"
        } else {
            ""
        }

        return "$simpleName ($packageName)$typeParamStr$methodStr\n  Constructors: $constructorCount"
    }

    companion object {
        /**
         * Creates metadata for a discovered class by analyzing it with reflection.
         *
         * Extracts:
         * - Class information (name, package, constructors)
         * - (Optional) Methods via separate method scanner
         * - (Optional) Generic type parameters via GenericTypeExtractor
         *
         * @param clazz The class to create metadata for
         * @param methods Optional pre-discovered methods
         * @param baseType Optional base type to extract generic parameters from
         */
        fun from(
            clazz: Class<*>,
            methods: List<MethodMetadata> = emptyList(),
            baseType: Class<*>? = null
        ): DiscoveryMetadata {
            val hasNoArgsConstructor = try {
                clazz.getDeclaredConstructor()
                true
            } catch (e: NoSuchMethodException) {
                false
            }

            // Extract generic type parameters if baseType provided
            val typeParameterMapping = if (baseType != null && baseType.typeParameters.isNotEmpty()) {
                try {
                    GenericTypeExtractor.extractTypeArgumentsAsMap(clazz, baseType)
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }

            return DiscoveryMetadata(
                discoveredClass = clazz,
                packageName = clazz.packageName,
                simpleName = clazz.simpleName,
                hasNoArgsConstructor = hasNoArgsConstructor,
                constructorCount = clazz.constructors.size,
                methods = methods,
                typeParameterMapping = typeParameterMapping
            )
        }
    }
}
