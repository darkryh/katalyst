package io.github.darkryh.katalyst.di.analysis

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ComponentOrderComputer.validateOrder] is the post-condition on the topological sort: every
 * ordered component present exactly once, each dependency before its dependent. It had **no caller
 * anywhere** - a check that never ran.
 *
 * Deleting it was not an option: it is part of the frozen public API surface guarded by
 * binary-compatibility-validator (`katalyst-di/api/katalyst-di.api`), so removing it would fail
 * `apiCheck`. It is therefore wired into [ComponentOrderComputer.computeOrder], where a violation
 * now aborts instead of handing a corrupted order to the registrar.
 */
class ComponentOrderPostConditionTest {

    private lateinit var computerLogger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>
    private var previousLevel: Level? = null

    @BeforeTest
    fun setUp() {
        computerLogger = LoggerFactory.getLogger("ComponentOrderComputer") as Logger
        previousLevel = computerLogger.level
        computerLogger.level = Level.DEBUG
        appender = ListAppender<ILoggingEvent>().apply { start() }
        computerLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        computerLogger.detachAppender(appender)
        appender.stop()
        computerLogger.level = previousLevel
    }

    @Test
    fun `computeOrder runs the post-condition on the order it returns`() {
        val order = ComponentOrderComputer(chainGraph()).computeOrder()

        assertEquals(listOf(Leaf::class, Middle::class, Root::class), order)
        assertTrue(
            appender.list.any { it.formattedMessage.contains("Order validation passed") },
            "computeOrder must actually run validateOrder on its result; without the wiring the " +
                "post-condition never executes: ${appender.list.map { it.formattedMessage }}",
        )
    }

    @Test
    fun `validateOrder rejects an order that drops a component`() {
        val computer = ComponentOrderComputer(chainGraph())

        assertFalse(
            computer.validateOrder(listOf(Leaf::class, Middle::class)),
            "an order missing a component must be rejected",
        )
        assertTrue(
            appender.list.any { it.level == Level.ERROR && it.formattedMessage.contains("Order size mismatch") },
            "the rejection must say what was wrong: ${appender.list.map { it.formattedMessage }}",
        )
    }

    @Test
    fun `validateOrder rejects an order that repeats a component`() {
        val computer = ComponentOrderComputer(chainGraph())

        assertFalse(
            computer.validateOrder(listOf(Leaf::class, Leaf::class, Middle::class)),
            "an order containing a duplicate must be rejected",
        )
    }

    @Test
    fun `validateOrder rejects an order that places a dependency after its dependent`() {
        val computer = ComponentOrderComputer(chainGraph())

        assertFalse(
            computer.validateOrder(listOf(Root::class, Middle::class, Leaf::class)),
            "a dependency placed after its dependent must be rejected",
        )
        assertTrue(
            appender.list.any { it.level == Level.ERROR && it.formattedMessage.contains("Invalid order") },
            "the rejection must name the pair that is out of order: " +
                "${appender.list.map { it.formattedMessage }}",
        )
    }

    /** Root -> Middle -> Leaf, so the only valid order is Leaf, Middle, Root. */
    private fun chainGraph() = DependencyGraph(
        nodes = mapOf(
            Root::class to ComponentNode(
                type = Root::class,
                dependencies = listOf(
                    Dependency(type = Middle::class, parameterName = "middle", isResolvable = true)
                ),
            ),
            Middle::class to ComponentNode(
                type = Middle::class,
                dependencies = listOf(
                    Dependency(type = Leaf::class, parameterName = "leaf", isResolvable = true)
                ),
            ),
            Leaf::class to ComponentNode(type = Leaf::class),
        ),
        edges = mapOf(
            Root::class to setOf(Middle::class),
            Middle::class to setOf(Leaf::class),
            Leaf::class to emptySet(),
        ),
    )

    private class Leaf
    private class Middle(val leaf: Leaf)
    private class Root(val middle: Middle)
}
