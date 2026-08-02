package com.shelfie.core.billing

/**
 * Whether a purchase query actually succeeded.
 *
 * Exists so that [EntitlementResolver.resolve] cannot be called without having
 * considered it. The original code passed the query's result code to `_` and looked
 * only at the returned list, which made "the query failed" indistinguishable from
 * "you own nothing" — and silently revoked paid access. Making the status a required
 * argument means that specific mistake is no longer expressible.
 */
enum class QueryStatus { OK, FAILED }

/** The minimum a purchase needs to expose for an entitlement decision. */
data class PurchaseSnapshot(
    val productIds: List<String>,
    val isPurchased: Boolean,
    val isAcknowledged: Boolean,
    /** Play's purchase token, used to match a snapshot back to its Purchase. */
    val token: String = "",
)

/** What the app should do with its cached entitlement after a query. */
enum class EntitlementVerdict {
    /** Play confirmed ownership. Cache true. */
    OWNED,

    /** Play confirmed the product is not owned. Cache false. */
    NOT_OWNED,

    /**
     * The query did not produce a trustworthy answer. Leave the cache alone.
     *
     * This is the case that matters. Play can be signed out, mid-update, or
     * temporarily unavailable on a device that is otherwise online — and in every
     * one of those cases it returns an empty purchase list with a non-OK code. A
     * paying user must not be downgraded because of it.
     */
    UNKNOWN,
}

/**
 * Decides the entitlement from a purchase query, kept pure so it is testable
 * without a `BillingClient`.
 *
 * The rule the app relies on: **only a successful query may revoke access.**
 */
object EntitlementResolver {

    fun resolve(
        status: QueryStatus,
        purchases: List<PurchaseSnapshot>,
        productId: String = ShelfieProducts.FULL_VERSION,
    ): EntitlementVerdict {
        val ownsProduct = purchases.any { it.isPurchased && productId in it.productIds }

        return when {
            // Ownership is believed even from a failed query: a purchase Play just
            // told us about is not made less true by the call having errored, and
            // erring towards granting access is the correct direction for a bug.
            ownsProduct -> EntitlementVerdict.OWNED
            status == QueryStatus.OK -> EntitlementVerdict.NOT_OWNED
            else -> EntitlementVerdict.UNKNOWN
        }
    }

    /**
     * Purchases needing acknowledgement.
     *
     * Google auto-refunds anything left unacknowledged for three days, so this runs
     * on every launch rather than only after a purchase — an acknowledgement that
     * failed once then has repeated chances to succeed.
     *
     * Scoped to [productId]: acknowledging unrelated products would be
     * acknowledging purchases this app knows nothing about.
     */
    fun needingAcknowledgement(
        purchases: List<PurchaseSnapshot>,
        productId: String = ShelfieProducts.FULL_VERSION,
    ): List<PurchaseSnapshot> = purchases.filter {
        it.isPurchased && !it.isAcknowledged && productId in it.productIds
    }
}
