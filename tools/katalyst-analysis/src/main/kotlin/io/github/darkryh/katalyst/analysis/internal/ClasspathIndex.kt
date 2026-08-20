package io.github.darkryh.katalyst.analysis.internal

import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader

/**
 * Indexes a compiled classpath for analysis.
 *
 * Builds an isolated [URLClassLoader] (parented to the analysis classloader so framework marker
 * types resolve to the *same* Class objects) and a Reflections store restricted to the configured
 * scan packages. Classes are always loaded with `initialize = false` so no user static initialiser
 * runs — exactly mirroring how the runtime's `AutoBindingRegistrar` loads candidate classes.
 */
internal class ClasspathIndex private constructor(
    private val scanPackages: List<String>,
    private val loader: URLClassLoader,
    private val reflections: Reflections,
) : AutoCloseable {
    /** Fully-qualified names of every type the store knows about, restricted to scan packages. */
    fun allTypeNames(): Set<String> =
        reflections.getAll(Scanners.SubTypes)
            .filter { name -> scanPackages.any { name == it || name.startsWith("$it.") } }
            .toSet()

    /** Loads a class without running its static initialiser; null if it cannot be loaded. */
    fun loadOrNull(name: String): Class<*>? =
        runCatching { Class.forName(name, false, loader) }
            .onFailure { logger.debug("Could not load {}: {}", name, it.message) }
            .getOrNull()

    /** Concrete (non-abstract, non-interface) classes in scan packages. */
    fun concreteClasses(): List<Class<*>> =
        allTypeNames().mapNotNull { loadOrNull(it) }
            .filter { !it.isInterface && !Modifier.isAbstract(it.modifiers) && !it.isAnonymousClass }

    /**
     * Kotlin file-class (`*Kt`) static methods whose first parameter is one of the given receiver
     * types and which return `void`/`Unit`. These are the *candidate* function entrypoints; the
     * caller still confirms genuine DSL usage via [DslBytecodeAnalyzer].
     */
    fun topLevelReceiverFunctions(receiverTypes: Collection<Class<*>>): List<Method> {
        val seen = mutableSetOf<String>()
        return allTypeNames()
            .filter { it.endsWith("Kt") }
            .mapNotNull { loadOrNull(it) }
            .flatMap { clazz ->
                clazz.declaredMethods.asSequence()
                    .filter { m ->
                        Modifier.isStatic(m.modifiers) &&
                            m.parameterTypes.isNotEmpty() &&
                            receiverTypes.any { it.isAssignableFrom(m.parameterTypes[0]) } &&
                            (m.returnType == Void.TYPE || m.returnType.name == "kotlin.Unit")
                    }
                    .filter { m ->
                        val sig = "${m.declaringClass.name}#${m.name}(${m.parameterTypes.joinToString { it.name }})"
                        seen.add(sig)
                    }
                    .toList()
            }
    }

    fun classLoader(): ClassLoader = loader

    /**
     * Closes the isolated [URLClassLoader], releasing its open jar/file handles.
     *
     * Safe to call once the caller no longer needs to load classes through this index — no method
     * here retains state that requires the loader afterwards; callers must not invoke [loadOrNull]
     * or [classLoader] after closing.
     */
    override fun close() {
        loader.close()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClasspathIndex::class.java)

        fun build(classpath: List<File>, scanPackages: List<String>): ClasspathIndex {
            val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
            val loader = URLClassLoader(urls, ClasspathIndex::class.java.classLoader)

            val filter = scanPackages.fold(FilterBuilder()) { b, pkg -> b.includePackage(pkg) }
            val config = ConfigurationBuilder()
                .setUrls(urls.toList())
                .addClassLoaders(loader)
                .filterInputsBy(filter)
                // filterResultsBy(true) keeps every subtype edge so getAll(SubTypes) enumerates
                // the full set of types in scan packages, just like the runtime route discovery.
                .setScanners(Scanners.SubTypes.filterResultsBy { true })

            return ClasspathIndex(scanPackages, loader, Reflections(config))
        }
    }
}
