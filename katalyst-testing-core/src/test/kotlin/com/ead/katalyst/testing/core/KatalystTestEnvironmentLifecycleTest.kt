package com.ead.katalyst.testing.core

import com.ead.katalyst.di.config.KatalystDIOptions
import com.ead.katalyst.ktor.KtorModule
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication

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
            org.koin.core.Koin::class.java,
            List::class.java,
            kotlin.jvm.functions.Function0::class.java
        )
        constructor.isAccessible = true

        val koin = koinApplication { }.koin
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
            koin,
            emptyList<KtorModule>(),
            hook
        ).also {
            stopKoin()
        }
    }
}
