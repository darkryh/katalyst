package com.ead.katalyst.di.internal

import com.ead.katalyst.components.Component
import com.ead.katalyst.tables.Table
import com.ead.katalyst.database.DatabaseTransactionManager
import com.ead.katalyst.events.EventHandler
import com.ead.katalyst.error.DependencyInjectionException
import com.ead.katalyst.repositories.Repository
import com.ead.katalyst.routes.KtorModule
import com.ead.katalyst.services.Service
import com.ead.katalyst.scanner.core.DiscoveryConfig
import com.ead.katalyst.scanner.core.DiscoveryPredicate
import com.ead.katalyst.scanner.scanner.ReflectionsTypeScanner
import com.ead.katalyst.services.service.SchedulerService
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import org.koin.core.definition.Kind
import org.koin.core.definition.indexKey
import org.koin.core.error.DefinitionOverrideException
import org.koin.core.instance.SingleInstanceFactory
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.KMutableProperty1
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import java.lang.reflect.Method

private val logger = LoggerFactory.getLogger("AutoBindingRegistrar")

/**
 * Discovers framework components (services, repositories, validators, event handlers)
 * and registers them into the active Koin container.
 */
@OptIn(KoinInternalApi::class)
class AutoBindingRegistrar(
    private val koin: Koin,
    scanPackages: Array<String>
) {

    private val packages: List<String> = scanPackages.filter { it.isNotBlank() }

    fun registerAll() {
        registerComponents(Repository::class.java, "repositories")
        registerComponents(Component::class.java, "components")
        registerTables()
        registerComponents(KtorModule::class.java, "ktor modules")
        @Suppress("UNCHECKED_CAST")
        registerComponents(EventHandler::class.java as Class<EventHandler<*>>, "event handlers")
        registerRouteFunctions()
    }

    private inline fun <reified T : Any> registerComponents(baseType: Class<T>, label: String) {
        val pending = discoverConcreteTypes(baseType).toMutableSet()
        if (pending.isEmpty()) {
            logger.debug("No {} discovered for {}", baseType.simpleName, label)
            return
        }

        val deferred = mutableSetOf<Class<out T>>()

        while (pending.isNotEmpty()) {
            var progressMade = false
            val iterator = pending.iterator()

            while (iterator.hasNext()) {
                val clazz = iterator.next()

                if (isAlreadyRegistered(clazz)) {
                    logger.debug("Skipping {} - already registered", clazz.name)
                    iterator.remove()
                    progressMade = true
                    continue
                }

                val result = runCatching {
                    val instance = instantiate(clazz.kotlin)
                    injectWellKnownProperties(instance)

                    val secondaryTypes = computeSecondaryTypes(clazz.kotlin, baseType.kotlin)
                    registerInstanceWithKoin(instance, clazz.kotlin, secondaryTypes)
                    if (baseType == KtorModule::class.java) {
                        KtorModuleRegistry.register(instance as KtorModule)
                    }
                    logger.info("Registered {} component {}", label, clazz.name)
                    if (koin.getFromKoinOrNull(clazz.kotlin) == null) {
                        logger.warn("Verification failed for {}: instance not retrievable immediately after registration", clazz.name)
                    }
                }

                when {
                    result.isSuccess -> {
                        iterator.remove()
                        deferred.remove(clazz)
                        progressMade = true
                    }

                    result.exceptionOrNull() is DependencyInjectionException -> {
                        if (deferred.add(clazz)) {
                            logger.debug(
                                "Deferring registration of {} until dependencies are available",
                                clazz.name
                            )
                        }
                    }

                    else -> {
                        val error = result.exceptionOrNull()!!
                        logger.error(
                            "Failed to register {} {}: {}",
                            label.dropLastWhile { it == 's' },
                            clazz.name,
                            error.message
                        )
                        logger.debug("Full error while registering ${clazz.name}", error)
                        iterator.remove()
                        progressMade = true
                    }
                }
            }

            if (!progressMade) {
                deferred.forEach { deferredClass ->
                    logger.error(
                        "Unable to resolve dependencies for {} {}. Please ensure all required components are discoverable.",
                        label.dropLastWhile { it == 's' },
                        deferredClass.name
                    )
                }
                break
            }
        }
    }

    private fun <T : Any> discoverConcreteTypes(baseType: Class<T>): Set<Class<out T>> {
        val predicate: DiscoveryPredicate<T> = DiscoveryPredicate { candidate ->
            !Modifier.isAbstract(candidate.modifiers) && !candidate.isInterface
        }

        val config = DiscoveryConfig(
            scanPackages = packages.ifEmpty { emptyList() },
            predicate = predicate
        )

        val scanner = ReflectionsTypeScanner(baseType, config)
        return scanner.discover()
    }

    private fun isAlreadyRegistered(clazz: Class<*>): Boolean =
        koin.getFromKoinOrNull(clazz.kotlin) != null

    private fun <T : Any> instantiate(target: KClass<T>): T {
        val constructor = target.primaryConstructor ?: target.constructors
            .minByOrNull { ctor -> ctor.parameters.count { !it.isOptional && it.kind == KParameter.Kind.VALUE } }
            ?: throw IllegalStateException("No usable constructor found for ${target.qualifiedName}")

        val args = mutableMapOf<KParameter, Any?>()

        constructor.parameters.forEach { parameter ->
            if (parameter.kind != KParameter.Kind.VALUE) return@forEach
            if (parameter.isOptional) return@forEach

            val dependencyType = parameter.type.classifier as? KClass<*>
                ?: throw IllegalStateException("Unsupported constructor parameter ${parameter.name} in ${target.qualifiedName}")

            val resolved = resolveDependency(dependencyType, parameter.type, target)
            args[parameter] = resolved
        }

        constructor.isAccessible = true
        return if (args.isEmpty()) {
            constructor.call()
        } else {
            constructor.callBy(args)
        }
    }

    private fun resolveDependency(type: KClass<*>, kType: KType, owner: KClass<*>): Any? =
        when (type) {
            Koin::class -> koin
            else -> koin.getFromKoinOrNull(type) ?: if (kType.isMarkedNullable) {
                null
            } else {
                throw DependencyInjectionException(
                    "Cannot resolve dependency ${type.qualifiedName} for ${owner.qualifiedName}"
                )
            }
        }

    private fun injectWellKnownProperties(instance: Any) {
        @Suppress("UNCHECKED_CAST")
        val mutableProperties = instance::class.memberProperties
            .filterIsInstance<KMutableProperty1<Any, Any?>>()

        mutableProperties.forEach { property ->
            val classifier = property.returnType.classifier as? KClass<*> ?: return@forEach

            val value = when (classifier) {
                DatabaseTransactionManager::class -> koin.getFromKoinOrNull(DatabaseTransactionManager::class)
                SchedulerService::class -> koin.getFromKoinOrNull(SchedulerService::class)
                else -> null
            } ?: return@forEach

            if (propertyAlreadyInitialised(property, instance)) return@forEach
            runCatching {
                property.isAccessible = true
                property.setter.call(instance, value)
            }.onFailure { error ->
                logger.warn(
                    "Failed to assign {} on {}: {}",
                    classifier.simpleName,
                    instance::class.qualifiedName,
                    error.message
                )
            }
        }
    }

    private fun propertyAlreadyInitialised(property: KMutableProperty1<Any, Any?>, instance: Any): Boolean =
        try {
            val current = property.get(instance)
            current != null
        } catch (_: UninitializedPropertyAccessException) {
            false
        }

    private fun computeSecondaryTypes(
        clazz: KClass<*>,
        baseType: KClass<*>
    ): List<KClass<*>> {
        val reserved = setOf(
            Any::class,
            Component::class,
            Service::class,
            Repository::class,
            EventHandler::class
        )

        return clazz.supertypes
            .map { it.jvmErasure }
            .filter { candidate ->
                candidate != clazz &&
                    candidate != baseType &&
                    candidate.java.isInterface &&
                    candidate !in reserved
            }
    }

    private fun registerRouteFunctions() {
        val methods = discoverRouteFunctions()
        if (methods.isEmpty()) {
            logger.debug("No route extension functions discovered in {}", packages.joinToString())
            return
        }

        methods.forEach { method ->
            val module = RouteFunctionModule(method)
            KtorModuleRegistry.register(module)
            logger.info(
                "Registered route function {}.{} as Ktor module",
                method.declaringClass.name,
                method.name
            )
        }
    }

    private fun registerTables() {
        if (packages.isEmpty()) {
            logger.debug("No packages configured for scanning, skipping table discovery")
            return
        }

        val tables = discoverExposedTables()
        if (tables.isEmpty()) {
            logger.debug("No Exposed Table implementations discovered")
            return
        }

        tables.forEach { (tableClass, instance) ->
            try {
                // Register table with Table interface as secondary type for discovery
                registerInstanceWithKoin(instance, tableClass, listOf(Table::class))
                logger.info("Registered Exposed table {} with Table marker interface", tableClass.qualifiedName)
            } catch (e: Exception) {
                logger.error("Failed to register Exposed table {}: {}", tableClass.qualifiedName, e.message)
                logger.debug("Full error registering table ${tableClass.qualifiedName}", e)
            }
        }
    }

    private fun discoverExposedTables(): Map<KClass<*>, Any> {
        val results = mutableMapOf<KClass<*>, Any>()

        try {
            val urls = packages.flatMap { pkg -> ClasspathHelper.forPackage(pkg) }
            if (urls.isEmpty()) return results

            val config = ConfigurationBuilder()
                .setUrls(urls)
                .filterInputsBy(
                    packages.fold(FilterBuilder()) { builder, pkg ->
                        builder.includePackage(pkg)
                    }
                )
                .setScanners(SubTypesScanner(false))

            val reflections = Reflections(config)

            // Find all classes implementing Table interface
            val tableInterface = Class.forName("com.ead.katalyst.common.Table")
            @Suppress("UNCHECKED_CAST")
            val tableImplementations = reflections.getSubTypesOf(tableInterface as Class<*>)

            val classLoader = Thread.currentThread().contextClassLoader

            tableImplementations.forEach { clazz ->
                runCatching {
                    // Load the class
                    val loadedClass = Class.forName(clazz.name, false, classLoader)

                    // For Kotlin objects (singletons), access the INSTANCE field
                    if (loadedClass.isKotlinObject()) {
                        val instanceField = loadedClass.getDeclaredField("INSTANCE")
                        instanceField.isAccessible = true
                        val instance = instanceField.get(null)
                        results[loadedClass.kotlin] = instance
                    }
                }.onFailure { error ->
                    logger.debug("Could not instantiate table {}: {}", clazz.name, error.message)
                }
            }
        } catch (e: Exception) {
            logger.debug("Error during Exposed table discovery: {}", e.message)
        }

        return results
    }

    private fun Class<*>.isKotlinObject(): Boolean {
        return try {
            this.getDeclaredField("INSTANCE") != null
        } catch (e: NoSuchFieldException) {
            false
        }
    }

    private fun discoverRouteFunctions(): List<Method> {
        if (packages.isEmpty()) return emptyList()

        val urls = packages.flatMap { pkg -> ClasspathHelper.forPackage(pkg) }
        if (urls.isEmpty()) return emptyList()

        val config = ConfigurationBuilder()
            .setUrls(urls)
            .filterInputsBy(
                packages.fold(FilterBuilder()) { builder, pkg ->
                    builder.includePackage(pkg)
                }
            )
            .setScanners(SubTypesScanner(false))

        val reflections = Reflections(config)

        val classLoader = Thread.currentThread().contextClassLoader
        val methods = mutableSetOf<Method>()

        reflections.allTypes
            .asSequence()
            .filter { typeName -> typeName.endsWith("Kt") && packages.any { typeName.startsWith(it) } }
            .forEach { typeName ->
                runCatching { Class.forName(typeName, false, classLoader) }
                    .onSuccess { clazz ->
                        clazz.declaredMethods
                            .filterTo(methods) { method ->
                                method.parameterTypes.isNotEmpty() &&
                                    Modifier.isStatic(method.modifiers) &&
                                    (Route::class.java.isAssignableFrom(method.parameterTypes[0]) ||
                                        Application::class.java.isAssignableFrom(method.parameterTypes[0]))
                            }
                    }
                    .onFailure { error ->
                        logger.debug("Failed to examine class {} for route functions: {}", typeName, error.message)
                    }
            }

        val seen = mutableSetOf<String>()
        return methods.filter { method ->
            val signature = "${method.declaringClass.name}#${method.name}(${method.parameterTypes.joinToString { it.name }})"
            if (!seen.add(signature)) return@filter false

            Modifier.isStatic(method.modifiers) &&
                method.declaringClass.name.endsWith("Kt") &&
                (method.returnType == Void.TYPE || method.returnType.name == "kotlin.Unit")
        }
    }

    private fun registerInstanceWithKoin(
        instance: Any,
        primaryType: KClass<*>,
        secondaryTypes: List<KClass<*>>
    ) {
        logger.debug(
            "Registering {} with secondary bindings: {}",
            primaryType.qualifiedName,
            secondaryTypes.joinToString { it.qualifiedName ?: it.simpleName.orEmpty() }
        )

        val scopeQualifier = koin.scopeRegistry.rootScope.scopeQualifier
        val definition = BeanDefinition(
            scopeQualifier = scopeQualifier,
            primaryType = primaryType,
            qualifier = null,
            definition = { instance as Any },
            kind = Kind.Singleton,
            secondaryTypes = secondaryTypes
        )
        val factory = SingleInstanceFactory(definition)
        val primaryKey = indexKey(primaryType, definition.qualifier, scopeQualifier)
        runCatching {
            koin.instanceRegistry.saveMapping(true, primaryKey, factory, logWarning = false)
            secondaryTypes.forEach { type ->
                val key = indexKey(type, definition.qualifier, scopeQualifier)
                koin.instanceRegistry.saveMapping(true, key, factory, logWarning = false)
            }
            logger.debug("Registered {} as {} in Koin", primaryType.qualifiedName, primaryKey)
            logger.trace(
                "Koin registry keys after {}: {}",
                primaryType.simpleName,
                koin.instanceRegistry.instances.keys.joinToString()
            )
        }.onFailure { error ->
            if (error is DefinitionOverrideException) {
                logger.debug("Skipping duplicate registration for {}", primaryType.qualifiedName)
            } else {
                throw error
            }
        }
    }
}

private fun Koin.getFromKoinOrNull(kClass: KClass<*>): Any? =
    try {
        @Suppress("UNCHECKED_CAST")
        get(kClass as KClass<Any>, qualifier = null) as Any
    } catch (_: Exception) {
        null
    }

private class RouteFunctionModule(
    private val method: Method
) : KtorModule {

    override val order: Int = 0

    override fun install(application: Application) {
        try {
            method.isAccessible = true
            val parameterTypes = method.parameterTypes
            if (parameterTypes.isEmpty()) {
                logger.warn("Skipping route function {} – no parameters found", method)
                return
            }

            val firstParam = parameterTypes.first()
            when {
                Application::class.java.isAssignableFrom(firstParam) -> {
                    method.invoke(null, application)
                }

                Route::class.java.isAssignableFrom(firstParam) -> {
                    application.routing {
                        method.invoke(null, this)
                    }
                }

                else -> logger.warn(
                    "Skipping route function {} – unsupported parameter type {}",
                    method,
                    firstParam.name
                )
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to invoke route function {}: {}",
                method,
                e.message,
                e
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("RouteFunctionModule")
    }
}
