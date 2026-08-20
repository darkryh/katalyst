package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.core.di.KatalystContainer
import io.github.darkryh.katalyst.di.config.KatalystDIOptions
import io.github.darkryh.katalyst.ktor.KtorModule
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.reflect.KClass

class KatalystTestEnvironmentLifecycleTest {

    @Test
    fun `close invokes shutdown hook`() {
        var closed = false
        val env = fakeEnvironment {
            closed = true
        }

        env.close()

        assertTrue(closed, "shutdown hook should be invoked on close()")
    }

    private fun fakeEnvironment(onClose: () -> Unit): KatalystTestEnvironment {
        val constructor = KatalystTestEnvironment::class.java.getDeclaredConstructor(
            KatalystDIOptions::class.java,
            KatalystContainer::class.java,
            List::class.java,
            kotlin.jvm.functions.Function0::class.java
        )
        constructor.isAccessible = true

        val options = KatalystDIOptions(
            databaseConfig = inMemoryDatabaseConfig(),
            scanPackages = emptyArray(),
            features = emptyList()
        )

        val hook = object : kotlin.jvm.functions.Function0<Unit> {
            override fun invoke() {
                onClose()
            }
        }

        return constructor.newInstance(
            options,
            FakeKatalystContainer(),
            emptyList<KtorModule>(),
            hook
        )
    }

    private class FakeKatalystContainer : KatalystContainer {
        override fun <T : Any> get(type: KClass<T>, qualifier: String?): T =
            error("No fake binding registered for ${type.qualifiedName}")

        override fun <T : Any> getOrNull(type: KClass<T>, qualifier: String?): T? = null

        override fun <T : Any> getAll(type: KClass<T>): List<T> = emptyList()

        override fun contains(type: KClass<*>, qualifier: String?): Boolean = false
    }
}
