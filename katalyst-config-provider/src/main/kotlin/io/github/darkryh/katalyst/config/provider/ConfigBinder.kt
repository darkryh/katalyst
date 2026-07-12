package io.github.darkryh.katalyst.config.provider

import io.github.darkryh.katalyst.core.config.ConfigException
import io.github.darkryh.katalyst.core.config.ConfigProvider
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

/**
 * Reflective binder for configuration classes.
 *
 * Supports two binding styles, discovered automatically during component scanning:
 *
 *  - **Annotation-driven** — classes annotated with [ConfigPrefix] (with optional per-property
 *    [ConfigKey] overrides) are constructed by reading each primary-constructor property from
 *    the [ConfigProvider]. Data-class `init { require(...) }` validation runs automatically.
 *  - **Code escape hatch** — classes implementing [ConfigBinding] are constructed by calling
 *    their single-[ConfigProvider]-argument primary constructor.
 */
object ConfigBinder {

    private val log = LoggerFactory.getLogger(ConfigBinder::class.java)

    /**
     * Bind an annotation-driven configuration class ([ConfigPrefix]) from the provider.
     */
    fun bind(type: KClass<*>, provider: ConfigProvider): Any {
        val ctor = type.primaryConstructor
            ?: throw ConfigException("Config type ${type.simpleName} has no primary constructor")

        val prefix = type.findAnnotation<ConfigPrefix>()?.value

        val args = mutableMapOf<KParameter, Any?>()

        for (param in ctor.parameters) {
            if (param.kind != KParameter.Kind.VALUE) continue
            val name = param.name
                ?: throw ConfigException("Config type ${type.simpleName} has an unnamed constructor parameter")

            val key = param.findAnnotation<ConfigKey>()?.value
                ?: if (prefix != null) {
                    "$prefix.${kebab(name)}"
                } else {
                    throw ConfigException(
                        "Config parameter ${type.simpleName}.$name needs @ConfigKey or class @ConfigPrefix"
                    )
                }

            if (!provider.hasKey(key)) {
                when {
                    param.isOptional -> continue // let the Kotlin default apply
                    param.type.isMarkedNullable -> args[param] = null
                    else -> throw ConfigException(
                        "Required configuration key '$key' is missing for ${type.simpleName}.$name"
                    )
                }
                continue
            }

            val classifier = param.type.classifier
            val value: Any? = when (classifier) {
                // requireRaw (not a plain get()?.toString()) so a key that hasKey() reports as
                // present but resolves to a null value fails fast with the same "required
                // configuration key is missing" ConfigException as Int/Long/Boolean below,
                // instead of silently binding null into a non-nullable String parameter and
                // blowing up later with an opaque Kotlin-reflection error. Empty/blank strings
                // are untouched by this - only an actual null raw value is rejected.
                String::class -> requireRaw(provider, key).toString()
                Int::class -> parseInt(key, requireRaw(provider, key))
                Long::class -> parseLong(key, requireRaw(provider, key))
                Boolean::class -> parseBooleanValue(key, requireRaw(provider, key))
                else -> throw ConfigException(
                    "Unsupported configuration type '${(classifier as? KClass<*>)?.simpleName ?: classifier}' " +
                        "for ${type.simpleName}.$name"
                )
            }
            args[param] = value
        }

        return try {
            ctor.callBy(args)
        } catch (e: InvocationTargetException) {
            // Surface data-class `init { require(...) }` validation failures (and any other
            // constructor error) as a clean ConfigException naming the type, instead of leaking
            // the reflection wrapper.
            val cause = e.targetException ?: e
            throw ConfigException("Invalid configuration ${type.simpleName}: ${cause.message}", cause)
        }
    }

    /**
     * Bind a [ConfigBinding] implementor by calling its single-[ConfigProvider] constructor.
     */
    fun bindBinding(type: KClass<out ConfigBinding>, provider: ConfigProvider): ConfigBinding {
        val ctor = type.primaryConstructor?.takeIf { c ->
            c.parameters.size == 1 &&
                c.parameters[0].type.classifier == ConfigProvider::class
        } ?: throw ConfigException(
            "ConfigBinding ${type.simpleName} must declare a primary constructor taking a single ConfigProvider"
        )
        return ctor.call(provider)
    }

    /**
     * Discover all bindable configuration types in the given packages.
     *
     * Scans for both [ConfigPrefix]-annotated classes ([Scanners.TypesAnnotated]) and
     * [ConfigBinding] implementors ([Scanners.SubTypes]). Returns their union, excluding the
     * [ConfigBinding] interface itself and abstract classes.
     */
    fun discoverConfigTypes(scanPackages: Array<String>): Set<KClass<*>> {
        if (scanPackages.isEmpty()) {
            return emptySet()
        }

        val urls = scanPackages.flatMap { pkg -> ClasspathHelper.forPackage(pkg) }
        if (urls.isEmpty()) {
            return emptySet()
        }

        val filter = scanPackages.fold(FilterBuilder()) { builder, pkg ->
            builder.includePackage(pkg)
        }

        val config = ConfigurationBuilder()
            .setUrls(urls)
            .filterInputsBy(filter)
            .setScanners(Scanners.TypesAnnotated, Scanners.SubTypes)

        val reflections = Reflections(config)

        val result = mutableSetOf<KClass<*>>()

        reflections.getTypesAnnotatedWith(ConfigPrefix::class.java).forEach { clazz ->
            if (isConcrete(clazz)) result += clazz.kotlin
        }

        reflections.getSubTypesOf(ConfigBinding::class.java).forEach { clazz ->
            if (clazz != ConfigBinding::class.java && isConcrete(clazz)) result += clazz.kotlin
        }

        return result
    }

    /**
     * Discover, bind, and return every configuration instance keyed by its type.
     *
     * Convenience overload that runs [discoverConfigTypes] itself. Callers that already scanned
     * (e.g. DI bootstrap, which discovers config types once for dependency-graph pre-validation)
     * should call [bindAll] with that pre-discovered [Set] instead, to avoid a second classpath
     * scan.
     *
     * Fails fast: any single type that cannot be bound aborts the whole operation with a
     * [ConfigException] naming the offending type.
     */
    fun bindAll(scanPackages: Array<String>, provider: ConfigProvider): Map<KClass<*>, Any> =
        bindAll(discoverConfigTypes(scanPackages), provider)

    /**
     * Bind every configuration instance for an already-discovered set of types, keyed by type.
     *
     * Use this overload whenever the caller already has the result of [discoverConfigTypes] (or
     * an equivalent scan) on hand, instead of triggering a second, redundant classpath/Reflections
     * scan.
     *
     * Fails fast: any single type that cannot be bound aborts the whole operation with a
     * [ConfigException] naming the offending type.
     */
    fun bindAll(types: Set<KClass<*>>, provider: ConfigProvider): Map<KClass<*>, Any> {
        if (types.isEmpty()) {
            return emptyMap()
        }

        log.info("Discovered {} configuration type(s)", types.size)

        val result = LinkedHashMap<KClass<*>, Any>()
        for (type in types) {
            try {
                val instance = if (type.isSubclassOf(ConfigBinding::class)) {
                    @Suppress("UNCHECKED_CAST")
                    bindBinding(type as KClass<out ConfigBinding>, provider)
                } else {
                    bind(type, provider)
                }
                result[type] = instance
                log.debug("Bound configuration {}", type.simpleName)
            } catch (e: ConfigException) {
                throw ConfigException("Failed to bind configuration ${type.simpleName}: ${e.message}", e)
            } catch (e: Exception) {
                throw ConfigException("Failed to bind configuration ${type.simpleName}: ${e.message}", e)
            }
        }
        return result
    }

    private fun requireRaw(provider: ConfigProvider, key: String): Any =
        provider.get<Any>(key)
            ?: throw ConfigException("Required configuration key '$key' is missing")

    private fun isConcrete(clazz: Class<*>): Boolean =
        !clazz.isInterface && !Modifier.isAbstract(clazz.modifiers)

    /**
     * camelCase -> kebab-case (e.g. webhookSecret -> webhook-secret, secretApiKey -> secret-api-key).
     */
    private fun kebab(name: String): String {
        val sb = StringBuilder()
        for ((index, ch) in name.withIndex()) {
            if (ch.isUpperCase()) {
                if (index > 0) sb.append('-')
                sb.append(ch.lowercaseChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
