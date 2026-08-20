package io.github.darkryh.katalyst.testing.core

import io.github.darkryh.katalyst.database.SqlExecutor
import io.github.darkryh.katalyst.testing.core.configfixtures.AppConfig
import io.github.darkryh.katalyst.testing.core.configfixtures.ConfigConsumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * End-to-end bootstrap test for change A (annotated config binding) and change E
 * (injectable [SqlExecutor]).
 *
 * Boots a full Katalyst container over a fixture package containing a `@ConfigPrefix`
 * data class and a [io.github.darkryh.katalyst.core.component.Service] that injects it, and
 * asserts: the config is discovered, bound from the provider, registered by type, and
 * injected into a constructor — and that [SqlExecutor] resolves from the container.
 */
class ConfigBindingBootstrapTest {

    @Test
    fun `ConfigPrefix data class binds, injects by type, and SqlExecutor resolves`() {
        katalystTestEnvironment {
            scan("io.github.darkryh.katalyst.testing.core.configfixtures")
            config(mapOf("app.name" to "bound", "app.retries" to 7))
        }.use { env ->
            // A: discovered + bound from the provider + registered by its own type
            val cfg = env.get<AppConfig>()
            assertEquals("bound", cfg.name)
            assertEquals(7, cfg.retries)

            // A: injected into a Service constructor by type
            assertEquals("bound", env.get<ConfigConsumer>().config.name)

            // E: SqlExecutor is injectable straight from the container
            assertNotNull(env.get<SqlExecutor>())
        }
    }
}
