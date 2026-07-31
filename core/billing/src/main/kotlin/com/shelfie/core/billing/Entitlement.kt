package com.shelfie.core.billing

/**
 * What the user has paid for.
 *
 * There is exactly one product and it never expires. No subscription tiers, no
 * trials, no expiry to track — which is the point of the pricing model, and also
 * why the entitlement can be cached locally without any staleness risk.
 */
data class Entitlement(
    val isFullVersion: Boolean = false,
) {
    companion object {
        val Free = Entitlement(isFullVersion = false)
        val Full = Entitlement(isFullVersion = true)
    }
}

/** Connection and product state for the purchase UI. */
sealed interface BillingState {

    data object Connecting : BillingState

    data class Available(
        /** Localised price straight from Play, never hardcoded. */
        val formattedPrice: String,
    ) : BillingState

    /** Already owned; the purchase button should not be offered. */
    data object Owned : BillingState

    /**
     * Play Billing is unreachable — sideloaded build, no Play Store, or the
     * product is not configured yet. The app stays fully usable at the free tier.
     */
    data class Unavailable(val reason: String) : BillingState
}

/** Outcome of a purchase attempt. */
sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object Cancelled : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data object Pending : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

object ShelfieProducts {
    /**
     * The single one-time unlock. Must match the product id created in Play
     * Console exactly.
     */
    const val FULL_VERSION = "shelfie_full_version"
}
