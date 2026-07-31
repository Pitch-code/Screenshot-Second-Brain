package com.shelfie.core.classify

import com.google.common.truth.Truth.assertThat
import com.shelfie.core.model.ScreenshotAction
import com.shelfie.core.model.ScreenshotCategory
import org.junit.Test

/**
 * Text samples here are written to mirror what real screenshots actually
 * contain, because the roadmap gate for this module is accuracy against a
 * realistic corpus rather than against tidy synthetic strings.
 */
class ScreenshotClassifierTest {

    private val classifier = ScreenshotClassifier()

    // ------------------------------------------------------------ payments

    @Test
    fun `classifies a upi payment receipt and surfaces the amount`() {
        val result = classifier.classify(
            """
            Payment Successful
            ₹1,240.50
            Paid to RAMESH KUMAR
            UPI Ref No: 412345678901
            31 Jul 2026, 14:22
            """.trimIndent(),
        )

        assertThat(result.category).isEqualTo(ScreenshotCategory.PAYMENTS)
        assertThat(result.primaryValue).isEqualTo("1,240.50")
        assertThat(result.confidence).isGreaterThan(0.6)
    }

    @Test
    fun `picks the largest amount as the headline value`() {
        // Receipts routinely show subtotal, tax and total; the total is what matters.
        val result = classifier.classify(
            "Payment successful. Subtotal ₹450 GST ₹81 Total ₹531 debited via UPI",
        )
        assertThat(result.primaryValue).isEqualTo("531")
    }

    // ----------------------------------------------------------------- OTP

    @Test
    fun `classifies an otp message and offers copy code`() {
        val result = classifier.classify(
            "483920 is your OTP for HDFC Bank login. Valid for 10 minutes. Do not share.",
        )

        assertThat(result.category).isEqualTo(ScreenshotCategory.OTP_CODES)
        assertThat(result.primaryValue).isEqualTo("483920")
        assertThat(result.primaryAction).isEqualTo(ScreenshotAction.COPY_CODE)
    }

    // ------------------------------------------------------------- tickets

    @Test
    fun `classifies a train ticket and offers calendar action`() {
        val result = classifier.classify(
            """
            IRCTC e-Ticket
            PNR: 2456789012
            Train 12951 MUMBAI RAJDHANI
            Coach B3 Seat 42
            Departure 14 Aug 2026 17:40
            """.trimIndent(),
        )

        assertThat(result.category).isEqualTo(ScreenshotCategory.TICKETS)
        assertThat(result.primaryValue).isEqualTo("2456789012")
        assertThat(result.primaryAction).isEqualTo(ScreenshotAction.ADD_TO_CALENDAR)
    }

    @Test
    fun `classifies a movie booking as a ticket`() {
        val result = classifier.classify(
            "BookMyShow booking confirmed. PVR Phoenix. Screen 3 Row H Seat 12. Showtime 21:15",
        )
        assertThat(result.category).isEqualTo(ScreenshotCategory.TICKETS)
    }

    // ---------------------------------------------------------------- wifi

    @Test
    fun `classifies a wifi credential screenshot`() {
        val result = classifier.classify("Wi-Fi Password\nSSID: CafeGuest\nPassword: Hunter2Guest!")

        assertThat(result.category).isEqualTo(ScreenshotCategory.WIFI_PASSWORDS)
        assertThat(result.primaryValue).isEqualTo("Hunter2Guest!")
        assertThat(result.primaryAction).isEqualTo(ScreenshotAction.COPY_CODE)
    }

    // ------------------------------------------------------------ products

    @Test
    fun `classifies a shopping listing and prefers the link action`() {
        val result = classifier.classify(
            """
            boAt Airdopes 141
            ₹1,299 MRP ₹4,490 71% off
            Add to Cart  Buy Now
            Free delivery by Tomorrow
            https://flipkart.com/p/boat-141
            """.trimIndent(),
        )

        assertThat(result.category).isEqualTo(ScreenshotCategory.PRODUCTS)
        assertThat(result.primaryAction).isEqualTo(ScreenshotAction.OPEN_LINK)
    }

    // ------------------------------------------------------------- recipes

    @Test
    fun `classifies a recipe from measurements and instructions`() {
        val result = classifier.classify(
            """
            Paneer Butter Masala
            Ingredients
            250 grams paneer
            2 tbsp butter
            1 cup tomato puree
            Preheat pan and simmer for 10 minutes. Serves 4.
            """.trimIndent(),
        )
        assertThat(result.category).isEqualTo(ScreenshotCategory.RECIPES)
    }

    // --------------------------------------------------------------- chats

    @Test
    fun `classifies a chat screenshot`() {
        val result = classifier.classify(
            "WhatsApp\nAmit\nonline\nyou deleted this message\ntyping...",
        )
        assertThat(result.category).isEqualTo(ScreenshotCategory.CHATS)
    }

    // ----------------------------------------------------------- documents

    @Test
    fun `classifies an id document`() {
        val result = classifier.classify(
            "Government of India\nAadhaar\nDate of Birth: 12/03/1994\nValid until further notice",
        )
        assertThat(result.category).isEqualTo(ScreenshotCategory.DOCUMENTS)
    }

    // -------------------------------------------------------------- places

    @Test
    fun `classifies a maps screenshot`() {
        val result = classifier.classify(
            "Google Maps\nIndiranagar Metro\n2.4 km away\nETA 11 min\nGet directions",
        )
        assertThat(result.category).isEqualTo(ScreenshotCategory.PLACES)
    }

    // ------------------------------------------------------------ contacts

    @Test
    fun `classifies a contact and offers dial`() {
        val result = classifier.classify("Save contact\nPlumber Suresh\nMobile no 9876543210")

        assertThat(result.category).isEqualTo(ScreenshotCategory.CONTACTS)
        assertThat(result.primaryValue).contains("9876543210")
        assertThat(result.primaryAction).isEqualTo(ScreenshotAction.DIAL_NUMBER)
    }

    // ---------------------------------------------------------- user rules

    @Test
    fun `a user rule overrides the built in scorer`() {
        // Text scores strongly as PAYMENTS, but the user has decided otherwise.
        val rules = listOf(
            UserRule(id = 7, keyword = "Zerodha", category = ScreenshotCategory.DOCUMENTS),
        )
        val result = classifier.classify(
            "Zerodha Payment successful ₹5,000 debited via UPI",
            rules,
        )

        assertThat(result.category).isEqualTo(ScreenshotCategory.DOCUMENTS)
        assertThat(result.matchedRuleId).isEqualTo(7)
        assertThat(result.confidence).isEqualTo(1.0)
    }

    @Test
    fun `a disabled user rule is ignored`() {
        val rules = listOf(
            UserRule(id = 7, keyword = "Zerodha", category = ScreenshotCategory.DOCUMENTS, enabled = false),
        )
        val result = classifier.classify("Zerodha Payment successful ₹5,000 debited UPI", rules)

        assertThat(result.category).isEqualTo(ScreenshotCategory.PAYMENTS)
        assertThat(result.matchedRuleId).isNull()
    }

    @Test
    fun `user rules match whole words only`() {
        val rules = listOf(
            UserRule(id = 1, keyword = "pay", category = ScreenshotCategory.STUDY),
        )
        // "Payment" must not be matched by the rule keyword "pay".
        val result = classifier.classify("Payment successful ₹100 UPI", rules)
        assertThat(result.matchedRuleId).isNull()
    }

    @Test
    fun `user rule matching is case insensitive`() {
        val rules = listOf(
            UserRule(id = 3, keyword = "swiggy", category = ScreenshotCategory.PLACES),
        )
        val result = classifier.classify("SWIGGY order delivered", rules)
        assertThat(result.matchedRuleId).isEqualTo(3)
    }

    // ------------------------------------------------------------ fallback

    @Test
    fun `falls back to payments when only an amount is present`() {
        val result = classifier.classify("₹250")
        assertThat(result.category).isEqualTo(ScreenshotCategory.PAYMENTS)
        assertThat(result.confidence).isLessThan(0.5)
    }

    @Test
    fun `unrecognisable text is not sorted but still gets a copy action`() {
        val result = classifier.classify("asdf qwer zxcv")

        assertThat(result.category).isEqualTo(ScreenshotCategory.NOT_SORTED)
        assertThat(result.primaryAction).isEqualTo(ScreenshotAction.COPY_TEXT)
    }

    @Test
    fun `a bare link is not sorted but offers open link`() {
        val result = classifier.classify("https://some-blog.example/post/12")

        assertThat(result.category).isEqualTo(ScreenshotCategory.NOT_SORTED)
        assertThat(result.primaryAction).isEqualTo(ScreenshotAction.OPEN_LINK)
    }

    @Test
    fun `blank text is unsorted`() {
        assertThat(classifier.classify("").category).isEqualTo(ScreenshotCategory.NOT_SORTED)
    }

    @Test
    fun `classification is deterministic`() {
        val text = "Payment Successful ₹999 UPI Ref No: 987654321012"
        val first = classifier.classify(text)
        val second = classifier.classify(text)
        assertThat(first).isEqualTo(second)
    }

    // --------------------------------------------------- accuracy corpus gate

    @Test
    fun `meets the accuracy gate across a mixed corpus`() {
        val corpus: List<Pair<String, ScreenshotCategory>> = listOf(
            "Payment successful ₹340 paid to Zomato via UPI" to ScreenshotCategory.PAYMENTS,
            "₹12,500 debited from your bank account. UTR 552341009812" to ScreenshotCategory.PAYMENTS,
            "PhonePe transaction successful. Amount ₹75. Ref no 4432190087" to ScreenshotCategory.PAYMENTS,
            "204512 is your OTP. Do not share with anyone." to ScreenshotCategory.OTP_CODES,
            "Your verification code is 8891. Expires in 5 minutes" to ScreenshotCategory.OTP_CODES,
            "IRCTC PNR 4412398760 Coach S4 Seat 18 departure 02 Sep 2026" to ScreenshotCategory.TICKETS,
            "Boarding pass IndiGo 6E-234 Gate 14 Departure 08:20" to ScreenshotCategory.TICKETS,
            "SSID HomeNet_5G network key Summer#2026rain" to ScreenshotCategory.WIFI_PASSWORDS,
            "Add to cart. Nykaa. Offer price ₹649. Free delivery" to ScreenshotCategory.PRODUCTS,
            "Ingredients 2 cups rice 1 tsp salt preheat oven serves 3" to ScreenshotCategory.RECIPES,
            "Telegram last seen recently forwarded from channel" to ScreenshotCategory.CHATS,
            "PAN Card Permanent Account Number Date of Birth 04/07/1988" to ScreenshotCategory.DOCUMENTS,
            "Uber 3.1 km away ETA 7 min get directions" to ScreenshotCategory.PLACES,
            "Semester 4 syllabus chapter 6 assignment marks 25" to ScreenshotCategory.STUDY,
            "Add to contacts mobile no 7012345678" to ScreenshotCategory.CONTACTS,
        )

        val correct = corpus.count { (text, expected) ->
            classifier.classify(text).category == expected
        }
        val accuracy = correct.toDouble() / corpus.size

        assertThat(accuracy).isAtLeast(0.85)
    }
}
