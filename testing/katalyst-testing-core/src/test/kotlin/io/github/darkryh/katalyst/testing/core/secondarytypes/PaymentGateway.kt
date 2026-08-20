package io.github.darkryh.katalyst.testing.core.secondarytypes

/**
 * A contract that two independent applications each implement exactly once.
 *
 * Declared outside both scan packages on purpose: neither bootstrap may discover the other
 * application's implementation, so any collision between them can only come from state that
 * outlived a bootstrap.
 */
interface PaymentGateway {
    val provider: String
}
