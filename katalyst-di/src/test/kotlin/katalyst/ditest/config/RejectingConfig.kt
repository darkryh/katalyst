package katalyst.ditest.config

import io.github.darkryh.katalyst.config.provider.ConfigPrefix

/**
 * Config fixture for
 * [io.github.darkryh.katalyst.di.internal.ConfigBindingFailureMessageTest].
 *
 * It rejects an out-of-range value from its own `init {}` block, which is the ordinary way a
 * Katalyst configuration class validates itself. Lives under the `katalyst.ditest` root so no other
 * test's classpath scan discovers it.
 */
@ConfigPrefix("rejecting")
data class RejectingConfig(val port: Int) {
    init {
        require(port in 1..65535) { "port must be between 1 and 65535 but was $port" }
    }
}
