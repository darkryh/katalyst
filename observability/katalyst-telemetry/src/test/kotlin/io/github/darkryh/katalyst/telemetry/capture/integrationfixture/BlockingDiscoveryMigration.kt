package io.github.darkryh.katalyst.telemetry.capture.integrationfixture

import io.github.darkryh.katalyst.migrations.KatalystMigration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Test fixture that holds component registration open at a deterministic point. */
class BlockingDiscoveryMigration : KatalystMigration {
    init {
        entered.countDown()
        check(release.await(10, TimeUnit.SECONDS)) { "test did not release migration discovery" }
    }

    override val id: String = ID

    override fun up() = Unit

    companion object {
        const val ID = "1_blocking_discovery"

        @Volatile
        var entered = CountDownLatch(1)

        @Volatile
        var release = CountDownLatch(1)

        fun reset() {
            entered = CountDownLatch(1)
            release = CountDownLatch(1)
        }
    }
}
