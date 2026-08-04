package io.github.darkryh.katalyst.testing.core.secondarytypes.appa

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.testing.core.secondarytypes.PaymentGateway

/** Application A's only [PaymentGateway]. */
class StripeGateway : Component, PaymentGateway {
    override val provider: String = "stripe"
}
