package com.ead.katalyst.scanner.util

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import kotlin.reflect.KClass
import kotlin.reflect.KTypeParameter

/**
 * Utility for extracting generic type parameters from classes.
 *
 * This is essential for discovering generic relationships like:
 * - `Repository<User, UserDTO>` -> Extract User and UserDTO
 * - `EventHandler<UserCreatedEvent>` -> Extract UserCreatedEvent
 * - `Validator<Product>` -> Extract Product
 *
 * **Usage Examples:**
 * ```kotlin
 * // Extract Entity and DTO types from a Repository
 * val (entityClass, dtoClass) = GenericTypeExtractor.extractTypeArguments(
 *     UserRepositoryImpl::class.java,
 *     Repository::class.java
 * )
 * // entityClass = User::class.java
 * // dtoClass = UserDTO::class.java
 *
 * // Extract single generic parameter
 * val eventClass = GenericTypeExtractor.extractTypeArgument(
 *     UserWelcomeEmailHandler::class.java,
 *     EventHandler::class.java,
 *     0
 * )
 * // eventClass = UserCreatedEvent::class.java
 *
 * // Get all type parameters
 * val allTypes = GenericTypeExtractor.extractAllTypeArguments(
 *     UserRepositoryImpl::class.java,
 *     Repository::class.java
 * )
 * ```
 */
object GenericTypeExtractor {

    /**
     * Extracts all generic type arguments from a class for a specific base interface/class.
     *
     * **Example:**
     * ```kotlin
     * class UserRepositoryImpl : Repository<User, UserDTO> { ... }
     * val types = extractTypeArguments(UserRepositoryImpl::class.java, Repository::class.java)
     * // Returns: listOf(User::class.java, UserDTO::class.java)
     * ```
     *
     * @param clazz The concrete class that extends/implements the base type
     * @param baseType The generic base interface/class to extract type arguments from
     * @return List of extracted type arguments, empty list if not found or not assignable
     */
    fun extractTypeArguments(
        clazz: Class<*>,
        baseType: Class<*>
    ): List<Class<*>> {
        if (!baseType.isAssignableFrom(clazz)) {
            return emptyList()
        }

        return extractAllTypeArguments(clazz, baseType)
    }

    /**
     * Extracts a specific generic type argument from a class.
     *
     * **Example:**
     * ```kotlin
     * class UserWelcomeEmailHandler : EventHandler<UserCreatedEvent> { ... }
     * val eventType = extractTypeArgument(
     *     UserWelcomeEmailHandler::class.java,
     *     EventHandler::class.java,
     *     0  // First type parameter
     * )
     * // Returns: UserCreatedEvent::class.java
     * ```
     *
     * @param clazz The concrete class
     * @param baseType The generic base interface/class
     * @param position The position of the type argument (0-based)
     * @return The extracted type argument class, or null if not found
     * @throws IndexOutOfBoundsException if position is invalid
     */
    fun extractTypeArgument(
        clazz: Class<*>,
        baseType: Class<*>,
        position: Int
    ): Class<*>? {
        val args = extractAllTypeArguments(clazz, baseType)
        if (position >= args.size) {
            throw IndexOutOfBoundsException(
                "Type argument position $position is out of bounds. " +
                    "Found ${args.size} type argument(s) in ${baseType.simpleName}"
            )
        }
        return args.getOrNull(position)
    }

    /**
     * Extracts type arguments as Kotlin classes.
     *
     * @param clazz The concrete class
     * @param baseType The generic base interface/class
     * @return List of extracted type arguments as KClass
     */
    fun extractTypeArgumentsAsKClass(
        clazz: Class<*>,
        baseType: Class<*>
    ): List<KClass<*>> {
        return extractTypeArguments(clazz, baseType).map { it.kotlin }
    }

    /**
     * Extracts type arguments as a map (name -> class).
     *
     * Useful for understanding which type parameter is which.
     *
     * **Example:**
     * ```kotlin
     * class UserRepositoryImpl : Repository<User, UserDTO> { ... }
     * val typeMap = extractTypeArgumentsAsMap(
     *     UserRepositoryImpl::class.java,
     *     Repository::class.java
     * )
     * // Returns: mapOf("E" to User::class.java, "D" to UserDTO::class.java)
     * ```
     *
     * @param clazz The concrete class
     * @param baseType The generic base interface/class
     * @return Map of type parameter names to their classes
     */
    fun extractTypeArgumentsAsMap(
        clazz: Class<*>,
        baseType: Class<*>
    ): Map<String, Class<*>> {
        val typeParams = baseType.typeParameters
        val typeArgs = extractAllTypeArguments(clazz, baseType)

        return typeParams.zip(typeArgs).associate { (param, arg) ->
            param.name to arg
        }
    }

    /**
     * Checks if a class has generic type arguments for a base type.
     *
     * @param clazz The concrete class
     * @param baseType The generic base interface/class
     * @return true if type arguments exist, false otherwise
     */
    fun hasTypeArguments(
        clazz: Class<*>,
        baseType: Class<*>
    ): Boolean {
        return extractAllTypeArguments(clazz, baseType).isNotEmpty()
    }

    /**
     * Gets a description of the generic type for logging/debugging.
     *
     * **Example:**
     * ```kotlin
     * val desc = getTypeDescription(
     *     UserRepositoryImpl::class.java,
     *     Repository::class.java
     * )
     * // Returns: "Repository<User, UserDTO>"
     * ```
     *
     * @param clazz The concrete class
     * @param baseType The generic base interface/class
     * @return Human-readable description like "Repository<User, UserDTO>"
     */
    fun getTypeDescription(
        clazz: Class<*>,
        baseType: Class<*>
    ): String {
        val args = extractTypeArguments(clazz, baseType)
        val argsStr = args.joinToString(", ") { it.simpleName }
        return "${baseType.simpleName}<$argsStr>"
    }

    // ==================== Internal Implementation ====================

    /**
     * Internal: Recursively extracts type arguments from superclasses and interfaces.
     */
    private fun extractAllTypeArguments(
        clazz: Class<*>,
        baseType: Class<*>
    ): List<Class<*>> {
        // Check direct implementation
        val directTypeArgs = extractFromDirectSuperTypes(clazz, baseType)
        if (directTypeArgs.isNotEmpty()) {
            return directTypeArgs
        }

        // Check superclass
        clazz.genericSuperclass?.let { superType ->
            val superTypeArgs = extractFromGenericType(superType, baseType)
            if (superTypeArgs.isNotEmpty()) {
                return superTypeArgs
            }
        }

        // Check interfaces
        clazz.genericInterfaces.forEach { interfaceType ->
            val interfaceTypeArgs = extractFromGenericType(interfaceType, baseType)
            if (interfaceTypeArgs.isNotEmpty()) {
                return interfaceTypeArgs
            }
        }

        return emptyList()
    }

    /**
     * Extract type arguments from direct superclass/interface implementations.
     */
    private fun extractFromDirectSuperTypes(
        clazz: Class<*>,
        baseType: Class<*>
    ): List<Class<*>> {
        // Check interfaces
        clazz.genericInterfaces.forEach { interfaceType ->
            if (interfaceType is ParameterizedType) {
                val rawType = interfaceType.rawType as? Class<*> ?: return@forEach
                if (baseType.isAssignableFrom(rawType)) {
                    return interfaceType.actualTypeArguments.mapNotNull { typeArg ->
                        (typeArg as? Class<*>)?.takeIf { it != Any::class.java }
                    }
                }
            }
        }

        // Check superclass
        clazz.genericSuperclass?.let { superType ->
            if (superType is ParameterizedType) {
                val rawType = superType.rawType as? Class<*> ?: return@let
                if (baseType.isAssignableFrom(rawType)) {
                    return superType.actualTypeArguments.mapNotNull { typeArg ->
                        (typeArg as? Class<*>)?.takeIf { it != Any::class.java }
                    }
                }
            }
        }

        return emptyList()
    }

    /**
     * Extract type arguments from a generic type.
     */
    private fun extractFromGenericType(
        type: Type,
        baseType: Class<*>
    ): List<Class<*>> {
        if (type !is ParameterizedType) {
            return emptyList()
        }

        val rawType = type.rawType as? Class<*> ?: return emptyList()

        // Found the base type
        if (baseType.isAssignableFrom(rawType) && rawType == baseType) {
            return type.actualTypeArguments.mapNotNull { typeArg ->
                (typeArg as? Class<*>)?.takeIf { it != Any::class.java }
            }
        }

        // Continue searching in superclasses
        if (baseType.isAssignableFrom(rawType)) {
            return rawType.genericInterfaces.asSequence()
                .mapNotNull { extractFromGenericType(it, baseType) }
                .firstOrNull() ?: emptyList()
        }

        return emptyList()
    }
}
