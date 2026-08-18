package io.github.darkryh.katalyst.di.invocation

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.darkryh.katalyst.core.di.KatalystContainer
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ParameterResolver] has two fallbacks that build the component anyway when a dependency cannot be
 * produced: an `isOptional` parameter keeps its Kotlin default, and a nullable one gets `null`.
 * Both used to discard the captured container failure with no output at any level, so a service
 * whose collaborator blew up during construction came out silently half-wired.
 *
 * The distinction that matters is *why* resolution failed. "Nothing is bound for this type" is the
 * whole point of an optional parameter and stays at DEBUG; a binding that exists and throws while
 * being produced is a real failure and is now reported at WARN naming the parameter, its type, the
 * owner and the reason.
 */
class ParameterResolverDropSiteLoggingTest {

    private lateinit var resolverLogger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>
    private var previousLevel: Level? = null

    @BeforeTest
    fun setUp() {
        resolverLogger = LoggerFactory.getLogger("ParameterResolver") as Logger
        previousLevel = resolverLogger.level
        resolverLogger.level = Level.DEBUG
        appender = ListAppender<ILoggingEvent>().apply { start() }
        resolverLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        resolverLogger.detachAppender(appender)
        appender.stop()
        resolverLogger.level = previousLevel
    }

    // ---------------------------------------------------------------- optional parameter

    @Test
    fun `a real construction failure behind an optional parameter is reported at WARN`() {
        val resolver = ParameterResolver(
            FailingContainer(IllegalStateException("pool exhausted while building Collaborator"))
        )
        val constructor = ComponentWithOptionalDependency::class.primaryConstructor!!

        resolver.resolveParameters(constructor, ownerDescription = "ComponentWithOptionalDependency")

        val warning = assertNotNull(
            appender.list.singleOrNull { it.level == Level.WARN },
            "a container failure discarded by the optional-default fallback must be reported at WARN, " +
                "got: ${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
        assertTrue(warning.formattedMessage.contains("collaborator"), warning.formattedMessage)
        assertTrue(warning.formattedMessage.contains(Collaborator::class.qualifiedName!!), warning.formattedMessage)
        assertTrue(warning.formattedMessage.contains("ComponentWithOptionalDependency"), warning.formattedMessage)
        assertTrue(warning.formattedMessage.contains("pool exhausted"), warning.formattedMessage)
    }

    @Test
    fun `an absent binding behind an optional parameter stays at DEBUG`() {
        val resolver = ParameterResolver(EmptyContainer())
        val constructor = ComponentWithOptionalDependency::class.primaryConstructor!!

        resolver.resolveParameters(constructor, ownerDescription = "ComponentWithOptionalDependency")

        assertTrue(
            appender.list.none { it.level == Level.WARN },
            "an optional parameter falling back to its default because nothing is bound is routine " +
                "and must not warn: ${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
        assertTrue(
            appender.list.any { it.level == Level.DEBUG && it.formattedMessage.contains("No binding for parameter") },
            "the routine case must still leave a DEBUG trail: ${appender.list.map { it.formattedMessage }}",
        )
    }

    // ---------------------------------------------------------------- nullable parameter

    @Test
    fun `a real construction failure behind a nullable parameter is reported at WARN`() {
        val resolver = ParameterResolver(
            FailingContainer(IllegalStateException("pool exhausted while building Collaborator"))
        )
        val constructor = ComponentWithNullableDependency::class.primaryConstructor!!

        val args = resolver.resolveParameters(
            constructor,
            ownerDescription = "ComponentWithNullableDependency",
        )

        assertNull(
            args[constructor.parameters.single { it.name == "collaborator" }],
            "the nullable fallback behaviour itself is unchanged",
        )
        val warning = assertNotNull(
            appender.list.singleOrNull { it.level == Level.WARN },
            "a container failure discarded by the nullable fallback must be reported at WARN, " +
                "got: ${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
        assertTrue(warning.formattedMessage.contains("pool exhausted"), warning.formattedMessage)
        assertTrue(warning.formattedMessage.contains("null"), warning.formattedMessage)
    }

    // ---------------------------------------------------------------- well-known property injection

    @Test
    fun `getFromContainerOrNull reports a real failure at WARN`() {
        val container = FailingContainer(IllegalStateException("transaction manager is closed"))

        val value = container.getFromContainerOrNull(
            kClass = Collaborator::class,
            ownerDescription = "property 'txManager' of SomeService",
        )

        assertNull(value)
        val warning = assertNotNull(
            appender.list.singleOrNull { it.level == Level.WARN },
            "well-known injection must not swallow a real resolution failure, " +
                "got: ${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
        assertTrue(warning.formattedMessage.contains(Collaborator::class.qualifiedName!!), warning.formattedMessage)
        assertTrue(warning.formattedMessage.contains("property 'txManager' of SomeService"), warning.formattedMessage)
        assertTrue(warning.formattedMessage.contains("transaction manager is closed"), warning.formattedMessage)
    }

    @Test
    fun `getFromContainerOrNull keeps an absent optional module at DEBUG`() {
        val container = EmptyContainer()

        val value = container.getFromContainerOrNull(
            kClass = Collaborator::class,
            ownerDescription = "property 'scheduler' of SomeService",
        )

        assertNull(value)
        assertTrue(
            appender.list.none { it.level == Level.WARN },
            "an absent optional framework module must not warn: " +
                "${appender.list.map { "${it.level}: ${it.formattedMessage}" }}",
        )
        assertTrue(
            appender.list.any { it.level == Level.DEBUG && it.formattedMessage.contains("leaving it unassigned") },
            "the routine case must still leave a DEBUG trail: ${appender.list.map { it.formattedMessage }}",
        )
    }

    class Collaborator

    class ComponentWithOptionalDependency(val collaborator: Collaborator = Collaborator())
    class ComponentWithNullableDependency(val collaborator: Collaborator?)

    /** A binding exists but blows up while being produced. */
    private class FailingContainer(private val failure: Throwable) : KatalystContainer {
        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T = throw failure
        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = throw failure
        override fun <T : Any> getAll(type: KClass<T>): List<T> = emptyList()
        override fun contains(type: KClass<*>, qualifier: String?): Boolean = false
    }

    /** Nothing is bound - the routine case every optional/nullable parameter is designed for. */
    private class EmptyContainer : KatalystContainer {
        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
            throw NoSuchElementException("No binding registered for ${type.simpleName}")
        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = null
        override fun <T : Any> getAll(type: KClass<T>): List<T> = emptyList()
        override fun contains(type: KClass<*>, qualifier: String?): Boolean = false
    }
}
