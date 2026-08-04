package io.github.darkryh.katalyst.testing.core.secondarytypes.appb

import io.github.darkryh.katalyst.core.component.Component
import io.github.darkryh.katalyst.testing.core.secondarytypes.PaymentGateway

/** Application B's only [PaymentGateway]. */
class PaypalGateway : Component, PaymentGateway {
    override val provider: String = "paypal"
}
