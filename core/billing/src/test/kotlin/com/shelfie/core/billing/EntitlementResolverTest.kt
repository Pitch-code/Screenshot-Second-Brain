package com.shelfie.core.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Entitlement decisions, which decide whether someone keeps what they paid for.
 *
 * These exist because of a shipped bug: the purchase query's result code was
 * discarded, so a query that failed while connected returned an empty list, read as
 * "owns nothing", and wrote `false` over a paid entitlement. The user silently
 * dropped to the free tier.
 *
 * The rule being locked down: **only a successful query may revoke access.**
 */
class EntitlementResolverTest {

    private val fullVersion = ShelfieProducts.FULL_VERSION

    private fun purchase(
        productId: String = ShelfieProducts.FULL_VERSION,
        isPurchased: Boolean = true,
        isAcknowledged: Boolean = true,
    ) = PurchaseSnapshot(
        productIds = listOf(productId),
        isPurchased = isPurchased,
        isAcknowledged = isAcknowledged,
        token = "token-$productId",
    )

    @Test
    fun `a successful query showing the product grants access`() {
        val verdict = EntitlementResolver.resolve(QueryStatus.OK, listOf(purchase()))

        assertThat(verdict).isEqualTo(EntitlementVerdict.OWNED)
    }

    @Test
    fun `a successful empty query revokes access`() {
        // The one case where writing false is correct: Play was reachable, answered,
        // and said the product is not owned. Refunds and chargebacks land here.
        val verdict = EntitlementResolver.resolve(QueryStatus.OK, emptyList())

        assertThat(verdict).isEqualTo(EntitlementVerdict.NOT_OWNED)
    }

    @Test
    fun `a failed query never revokes access`() {
        // The exact shipped bug. An empty list from a failed query is not evidence
        // that nothing is owned, and must leave the cached entitlement alone.
        val verdict = EntitlementResolver.resolve(QueryStatus.FAILED, emptyList())

        assertThat(verdict).isEqualTo(EntitlementVerdict.UNKNOWN)
        assertThat(verdict).isNotEqualTo(EntitlementVerdict.NOT_OWNED)
    }

    @Test
    fun `a failed query that still reports the purchase grants access`() {
        // Erring towards granting: a purchase Play told us about is not less true
        // for the call having errored.
        val verdict = EntitlementResolver.resolve(QueryStatus.FAILED, listOf(purchase()))

        assertThat(verdict).isEqualTo(EntitlementVerdict.OWNED)
    }

    @Test
    fun `a pending purchase does not grant access`() {
        // Deferred payments (UPI, cash) must not unlock before Play confirms, or
        // the app is given away to anyone who starts a payment and abandons it.
        val verdict = EntitlementResolver.resolve(
            QueryStatus.OK,
            listOf(purchase(isPurchased = false)),
        )

        assertThat(verdict).isEqualTo(EntitlementVerdict.NOT_OWNED)
    }

    @Test
    fun `owning a different product does not grant access`() {
        val verdict = EntitlementResolver.resolve(
            QueryStatus.OK,
            listOf(purchase(productId = "some_other_product")),
        )

        assertThat(verdict).isEqualTo(EntitlementVerdict.NOT_OWNED)
    }

    @Test
    fun `the product is found even when bundled with others in one purchase`() {
        val bundled = PurchaseSnapshot(
            productIds = listOf("other", fullVersion),
            isPurchased = true,
            isAcknowledged = true,
        )

        assertThat(EntitlementResolver.resolve(QueryStatus.OK, listOf(bundled)))
            .isEqualTo(EntitlementVerdict.OWNED)
    }

    @Test
    fun `unacknowledged purchases are reported for acknowledgement`() {
        // Google auto-refunds anything unacknowledged after three days, so missing
        // this means the user is refunded and loses access through no fault.
        val needing = EntitlementResolver.needingAcknowledgement(
            listOf(purchase(isAcknowledged = false)),
        )

        assertThat(needing).hasSize(1)
    }

    @Test
    fun `already acknowledged purchases are not acknowledged again`() {
        assertThat(EntitlementResolver.needingAcknowledgement(listOf(purchase()))).isEmpty()
    }

    @Test
    fun `acknowledgement is scoped to this app's product`() {
        // Acknowledging an unrelated product means vouching for a purchase this app
        // knows nothing about.
        val needing = EntitlementResolver.needingAcknowledgement(
            listOf(purchase(productId = "other_app_product", isAcknowledged = false)),
        )

        assertThat(needing).isEmpty()
    }

    @Test
    fun `pending purchases are not acknowledged`() {
        val needing = EntitlementResolver.needingAcknowledgement(
            listOf(purchase(isPurchased = false, isAcknowledged = false)),
        )

        assertThat(needing).isEmpty()
    }

    @Test
    fun `a reinstall with no local flag is restored by a successful query`() {
        // The user's actual question: paid, uninstalled, reinstalled. The local
        // cache is gone, so everything depends on this query.
        val verdict = EntitlementResolver.resolve(QueryStatus.OK, listOf(purchase()))

        assertThat(verdict).isEqualTo(EntitlementVerdict.OWNED)
    }
}
