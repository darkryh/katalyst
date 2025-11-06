package com.ead.katalyst.scanner.predicates

import com.ead.katalyst.scanner.core.DiscoveryPredicate
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

/**
 * Built-in predicates for common filtering scenarios.
 *
 * These predicates can be combined using `.and()`, `.or()`, and `.not()` methods.
 *
 * **Usage Examples:**
 * ```kotlin
 * // Match classes implementing a specific interface
 * val servicesPredicate = implementsInterface(Service::class)
 *
 * // Exclude test classes
 * val noTests = matchesClassName("Mock.*".toRegex()).not()
 *
 * // Combine predicates
 * val combined = implementsInterface(Service::class)
 *     .and(matchesClassName(".*Service".toRegex()))
 *     .and(matchesPackage("com.ead.xtory"))
 *
 * // Use with scanner
 * val scanner = ReflectionsTypeScanner(
 *     baseType = Service::class.java,
 *     predicate = combined
 * )
 * ```
 */

/**
 * Matches classes that implement a specific interface or extend a specific class.
 *
 * @param T The base type
 * @param interfaceClass The interface or class to check
 * @return Predicate that returns true if the discovered class implements/extends the interface
 */
fun <T> implementsInterface(interfaceClass: KClass<*>): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        interfaceClass.java.isAssignableFrom(clazz)
    }
}

/**
 * Matches classes that have a specific annotation.
 *
 * @param T The base type
 * @param annotationClass The annotation class to look for
 * @return Predicate that returns true if the class has the annotation
 */
fun <T> hasAnnotation(annotationClass: KClass<out Annotation>): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        clazz.isAnnotationPresent(annotationClass.java)
    }
}

/**
 * Matches classes in a specific package or its sub-packages.
 *
 * @param T The base type
 * @param packageName The package name to match
 * @return Predicate that returns true if the class is in the package
 */
fun <T> matchesPackage(packageName: String): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        clazz.packageName.startsWith(packageName)
    }
}

/**
 * Matches classes whose name matches a regex pattern.
 *
 * @param T The base type
 * @param pattern The regex pattern to match against class simple names
 * @return Predicate that returns true if the simple name matches the pattern
 *
 * **Examples:**
 * ```kotlin
 * matchesClassName(".*Service".toRegex())      // Matches: EmailService, SmsService, etc.
 * matchesClassName("Test.*".toRegex())         // Matches: TestEmailService, etc.
 * matchesClassName(".*Repository".toRegex())   // Matches: UserRepository, etc.
 * ```
 */
fun <T> matchesClassName(pattern: Regex): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        pattern.matches(clazz.simpleName)
    }
}

/**
 * Matches classes whose fully qualified name matches a regex pattern.
 *
 * @param T The base type
 * @param pattern The regex pattern to match against canonical names
 * @return Predicate that returns true if the canonical name matches the pattern
 *
 * **Examples:**
 * ```kotlin
 * matchesCanonicalName("com.ead.xtory..*Service".toRegex())
 * ```
 */
fun <T> matchesCanonicalName(pattern: Regex): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        pattern.matches(clazz.canonicalName ?: "")
    }
}

/**
 * Matches classes that are NOT abstract.
 *
 * @param T The base type
 * @return Predicate that returns true for concrete classes
 */
fun <T> isConcrete(): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        !Modifier.isAbstract(clazz.modifiers)
    }
}

/**
 * Matches classes that are NOT interfaces.
 *
 * @param T The base type
 * @return Predicate that returns true for non-interface classes
 */
fun <T> isNotInterface(): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        !clazz.isInterface
    }
}

/**
 * Matches classes that have a no-args constructor.
 *
 * @param T The base type
 * @return Predicate that returns true for classes with no-args constructors
 */
fun <T> hasNoArgsConstructor(): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        try {
            clazz.getDeclaredConstructor()
            true
        } catch (e: NoSuchMethodException) {
            false
        }
    }
}

/**
 * Matches classes that are NOT test classes (common naming patterns).
 *
 * Excludes classes matching:
 * - `*Test`
 * - `Test*`
 * - `*TestImpl`
 * - `*Mock`
 * - `Mock*`
 *
 * @param T The base type
 * @return Predicate that excludes common test class patterns
 */
fun <T> isNotTestClass(): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        val name = clazz.simpleName
        !name.endsWith("Test") &&
            !name.startsWith("Test") &&
            !name.endsWith("TestImpl") &&
            !name.endsWith("Mock") &&
            !name.startsWith("Mock")
    }
}

/**
 * Matches classes that are NOT synthetic or generated.
 *
 * Excludes:
 * - Classes with names containing `$` (inner classes, lambdas)
 * - Classes starting with `$` (generated by javac)
 *
 * @param T The base type
 * @return Predicate that excludes synthetic classes
 */
fun <T> isNotSynthetic(): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        !clazz.simpleName.contains("$")
    }
}

/**
 * Matches classes that are in a specific module/package hierarchy.
 *
 * **Examples:**
 * ```kotlin
 * // Match only classes in com.ead.xtory.auth and its sub-packages
 * isInModule("com.ead.xtory.auth")
 *
 * // Match classes in com.ead.xtory.auth, com.ead.xtory.notification, etc.
 * isInModule("com.ead.xtory.auth").or(isInModule("com.ead.xtory.notification"))
 * ```
 *
 * @param T The base type
 * @param moduleName The module/package name
 * @return Predicate that returns true for classes in the module
 */
fun <T> isInModule(moduleName: String): DiscoveryPredicate<T> {
    return matchesPackage(moduleName)
}

/**
 * Matches classes that have methods with a specific annotation.
 *
 * This predicate uses Kotlin reflection to check if any method in the class
 * is annotated with the given annotation.
 *
 * **Usage:**
 * ```kotlin
 * // Find controllers that have @RouteHandler methods
 * val withRoutes = hasMethodsWithAnnotation<RouteController>(RouteHandler::class)
 *
 * // Combine with other predicates
 * val combined = isNotTestClass<RouteController>()
 *     .and(hasMethodsWithAnnotation<RouteController>(RouteHandler::class))
 * ```
 *
 * @param T The base type
 * @param annotationClass The method annotation to look for
 * @return Predicate that returns true if class has methods with the annotation
 */
fun <T> hasMethodsWithAnnotation(
    annotationClass: KClass<out Annotation>
): DiscoveryPredicate<T> {
    val annotationJavaClass = annotationClass.java
    return DiscoveryPredicate { clazz ->
        try {
            clazz.declaredMethods.any { method ->
                method.isAnnotationPresent(annotationJavaClass)
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Matches classes that have public methods (not constructors, getters, etc).
 *
 * @param T The base type
 * @return Predicate that returns true for classes with public methods
 */
fun <T> hasPublicMethods(): DiscoveryPredicate<T> {
    return DiscoveryPredicate { clazz ->
        try {
            clazz.declaredMethods.any { method ->
                Modifier.isPublic(method.modifiers) &&
                        method.name != "toString" &&
                        method.name != "equals" &&
                        method.name != "hashCode"
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Matches classes that are instantiable (not abstract, not interface, has constructor).
 *
 * **Usage:**
 * ```kotlin
 * val instantiable = isInstantiable<MyType>()
 * ```
 *
 * @param T The base type
 * @return Predicate that returns true for instantiable classes
 */
fun <T> isInstantiable(): DiscoveryPredicate<T> {
    return isConcrete<T>()
        .and(isNotInterface<T>())
        .and(hasNoArgsConstructor<T>())
}
