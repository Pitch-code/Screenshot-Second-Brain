package com.shelfie.core.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for reordering recognised text by position.
 *
 * Plain JUnit, no Robolectric: [ReadingOrder] is deliberately pure Kotlin over a
 * simple coordinate type so the ordering rules can be tested without a bitmap, an
 * ML Kit model, or an Android runtime.
 *
 * Every case feeds fragments in an order that is *wrong on purpose*, because the
 * bug being fixed was exactly that — the app trusted the recogniser's emission
 * order. Input order must never influence the output.
 */
class ReadingOrderTest {

    /** A one-line fragment of standard height at a given position. */
    private fun line(text: String, x: Int, y: Int, width: Int = 100, height: Int = 20) =
        TextFragment(text = text, left = x, top = y, right = x + width, bottom = y + height)

    @Test
    fun `no fragments produces empty text`() {
        assertThat(ReadingOrder.arrange(emptyList())).isEmpty()
    }

    @Test
    fun `blank fragments are dropped rather than producing empty lines`() {
        val result = ReadingOrder.arrange(
            listOf(
                line("Total", x = 0, y = 0),
                line("   ", x = 0, y = 30),
                line("", x = 0, y = 60),
                line("₹450", x = 0, y = 90),
            ),
        )

        assertThat(result.lines().filter { it.isBlank() && it.isNotEmpty() }).isEmpty()
        assertThat(result).contains("Total")
        assertThat(result).contains("₹450")
    }

    @Test
    fun `fragments on the same row are ordered left to right`() {
        // Fed right-to-left.
        val result = ReadingOrder.arrange(
            listOf(
                line("90%", x = 400, y = 10),
                line("9:58", x = 0, y = 10),
            ),
        )

        assertThat(result).isEqualTo("9:58 90%")
    }

    @Test
    fun `rows are ordered top to bottom regardless of input order`() {
        val result = ReadingOrder.arrange(
            listOf(
                line("Third", x = 0, y = 200),
                line("First", x = 0, y = 0),
                line("Fourth", x = 0, y = 300),
                line("Second", x = 0, y = 100),
            ),
        )

        assertThat(result.lines().filter { it.isNotBlank() })
            .containsExactly("First", "Second", "Third", "Fourth")
            .inOrder()
    }

    @Test
    fun `a tall heading shares a row with small text beside it`() {
        // Overlap is measured against the shorter fragment, so a 60px heading and a
        // 16px label whose extents overlap belong together. Centre-distance
        // comparison would have split these.
        val result = ReadingOrder.arrange(
            listOf(
                line("Change", x = 300, y = 120, height = 16),
                line("Payments", x = 0, y = 100, height = 60),
            ),
        )

        assertThat(result).isEqualTo("Payments Change")
    }

    @Test
    fun `consecutive lines of a paragraph stay on separate rows`() {
        // Adjacent but not overlapping: these must not be merged into one line.
        val result = ReadingOrder.arrange(
            listOf(
                line("Shelfie reads the text in your", x = 0, y = 0, height = 20),
                line("screenshots so you can search", x = 0, y = 22, height = 20),
                line("them.", x = 0, y = 44, height = 20),
            ),
        )

        assertThat(result.lines()).hasSize(3)
        assertThat(result.lines().first()).isEqualTo("Shelfie reads the text in your")
    }

    @Test
    fun `a large vertical gap becomes a blank line`() {
        val result = ReadingOrder.arrange(
            listOf(
                line("Heading", x = 0, y = 0, height = 20),
                line("Body after a gap", x = 0, y = 120, height = 20),
            ),
        )

        assertThat(result).isEqualTo("Heading\n\nBody after a gap")
    }

    @Test
    fun `a small vertical gap stays a single newline`() {
        val result = ReadingOrder.arrange(
            listOf(
                line("Line one", x = 0, y = 0, height = 20),
                line("Line two", x = 0, y = 24, height = 20),
            ),
        )

        assertThat(result).isEqualTo("Line one\nLine two")
    }

    @Test
    fun `zero height fragments do not crash or divide by zero`() {
        val degenerate = TextFragment(text = "Odd", left = 0, top = 50, right = 10, bottom = 50)

        val result = ReadingOrder.arrange(listOf(degenerate, line("Normal", x = 0, y = 0)))

        assertThat(result).contains("Odd")
        assertThat(result).contains("Normal")
    }

    /**
     * The case that motivated all of this.
     *
     * A payment confirmation whose lines come back from the recogniser in an order
     * that splits the phrase the classifier needs. The category rules match
     * `payment successful` as a phrase with flexible whitespace, so it matches only
     * when those two words end up adjacent. Emission order put "Successful" three
     * lines away from "Payment", the phrase never matched, and a perfectly obvious
     * payment screenshot landed in "Not sorted yet".
     */
    @Test
    fun `a shuffled payment screenshot reads in order and keeps its phrases intact`() {
        val result = ReadingOrder.arrange(
            listOf(
                line("UPI transaction ID", x = 30, y = 400, width = 200, height = 18),
                line("Successful", x = 150, y = 152, width = 90, height = 22),
                line("₹1,250", x = 120, y = 240, width = 140, height = 40),
                line("Payment", x = 40, y = 150, width = 100, height = 22),
                line("To Kiran Stores", x = 40, y = 320, width = 200, height = 20),
                line("4098 2211 7734", x = 30, y = 430, width = 200, height = 18),
            ),
        )

        assertThat(result).contains("Payment Successful")
        assertThat(result).contains("UPI transaction ID")
        assertThat(result.indexOf("Payment")).isLessThan(result.indexOf("₹1,250"))
        assertThat(result.indexOf("₹1,250")).isLessThan(result.indexOf("To Kiran Stores"))
        assertThat(result.indexOf("To Kiran Stores")).isLessThan(result.indexOf("UPI transaction ID"))
    }

    @Test
    fun `a list of rows with trailing values pairs each label with its own value`() {
        // Two columns. Reading down each column instead of across the row would
        // produce "Places Payments Products 6 5 3" — every count attached to the
        // wrong label.
        val result = ReadingOrder.arrange(
            listOf(
                line("3 screenshots", x = 300, y = 200, width = 120, height = 18),
                line("Places", x = 40, y = 100, width = 100, height = 20),
                line("6 screenshots", x = 300, y = 100, width = 120, height = 18),
                line("Products", x = 40, y = 200, width = 100, height = 20),
                line("Payments", x = 40, y = 150, width = 100, height = 20),
                line("5 screenshots", x = 300, y = 150, width = 120, height = 18),
            ),
        )

        assertThat(result.lines().filter { it.isNotBlank() })
            .containsExactly(
                "Places 6 screenshots",
                "Payments 5 screenshots",
                "Products 3 screenshots",
            )
            .inOrder()
    }
}


/**
 * Tests for dropping the status bar.
 *
 * The reported symptom was shelf tiles labelled `2:03 2.00` and `2:03 0.62 Y…` —
 * the clock and the phone's network speed indicator. Correct reading order put the
 * status bar first, and first is where the label is taken from.
 *
 * These deliberately include the cases where a status bar must *not* be assumed,
 * because text discarded here can never be searched for again.
 */
class StatusBarRemovalTest {

    /** A typical 1080x2400 screenshot, downsampled. Status bar occupies the top ~40px. */
    private val imageHeight = 1200

    private fun line(text: String, x: Int, y: Int, width: Int = 200, height: Int = 22) =
        TextFragment(text = text, left = x, top = y, right = x + width, bottom = y + height)

    @Test
    fun `the clock and network speed are dropped`() {
        val result = ReadingOrder.arrange(
            listOf(
                line("2:03", x = 30, y = 14, width = 60),
                line("2.00 KB/s", x = 300, y = 14, width = 120),
                line("71%", x = 900, y = 14, width = 60),
                line("Hyderabad", x = 40, y = 200, width = 220),
            ),
            imageHeight = imageHeight,
        )

        assertThat(result).isEqualTo("Hyderabad")
        assertThat(result).doesNotContain("2:03")
        assertThat(result).doesNotContain("KB/s")
        assertThat(result).doesNotContain("71%")
    }

    @Test
    fun `the first real line becomes the label instead of the clock`() {
        // The whole point: what is first after removal is what the shelf shows.
        val result = ReadingOrder.arrange(
            listOf(
                line("2:03  0.62  71%", x = 30, y = 12, width = 400),
                line("Cognizant Technology Solutions", x = 40, y = 180, width = 400),
                line("Get Directions", x = 40, y = 240, width = 200),
            ),
            imageHeight = imageHeight,
        )

        assertThat(result.lineSequence().first()).isEqualTo("Cognizant Technology Solutions")
    }

    @Test
    fun `nothing is dropped when the image height is unknown`() {
        // Height 0 means the caller could not supply it. Guessing a position without
        // it would risk discarding real content, so removal is disabled instead.
        val result = ReadingOrder.arrange(
            listOf(
                line("2:03", x = 30, y = 14, width = 60),
                line("Hyderabad", x = 40, y = 200),
            ),
        )

        assertThat(result).contains("2:03")
    }

    @Test
    fun `a time further down the screenshot is kept`() {
        // A chat timestamp, a departure time, an appointment. Only the topmost band is
        // ever a candidate, and this one is nowhere near the top.
        val result = ReadingOrder.arrange(
            listOf(
                line("2:03", x = 30, y = 14, width = 60),
                line("Departs 6:45 PM", x = 40, y = 500, width = 240),
            ),
            imageHeight = imageHeight,
        )

        assertThat(result).isEqualTo("Departs 6:45 PM")
    }

    @Test
    fun `a long top line containing a time is kept`() {
        // A real heading that happens to mention a time must survive. The length gate
        // is what protects it: a status bar is never this wordy.
        val heading = "Your 2:30 appointment with Dr Rao is confirmed for today"

        val result = ReadingOrder.arrange(
            listOf(line(heading, x = 20, y = 10, width = 900)),
            imageHeight = imageHeight,
        )

        assertThat(result).isEqualTo(heading)
    }

    @Test
    fun `a short top line with no status bar signature is kept`() {
        // No clock, no percentage, no speed — so it is content, however short.
        val result = ReadingOrder.arrange(
            listOf(
                line("Inbox", x = 40, y = 14, width = 100),
                line("Hyderabad", x = 40, y = 200),
            ),
            imageHeight = imageHeight,
        )

        assertThat(result).contains("Inbox")
    }

    @Test
    fun `only the status bar is dropped, not the row below it`() {
        val result = ReadingOrder.arrange(
            listOf(
                line("9:58", x = 30, y = 10, width = 60),
                line("90%", x = 900, y = 10, width = 60),
                line("Cart", x = 40, y = 90, width = 100),
                line("Total ₹6692", x = 40, y = 300, width = 200),
            ),
            imageHeight = imageHeight,
        )

        assertThat(result.lineSequence().first()).isEqualTo("Cart")
        assertThat(result).contains("Total ₹6692")
    }
}
