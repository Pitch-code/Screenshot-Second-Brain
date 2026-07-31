package com.shelfie.core.classify

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntityExtractorTest {

    // ------------------------------------------------------------- amounts

    @Test
    fun `extracts rupee amount with symbol and comma grouping`() {
        val entities = EntityExtractor.extract("Paid to Ramesh Kumar ₹1,240.50 Payment successful")
        assertThat(entities.amounts).contains("1,240.50")
    }

    @Test
    fun `extracts amount written as Rs`() {
        assertThat(EntityExtractor.extract("Total Rs. 499 only").amounts).contains("499")
    }

    @Test
    fun `extracts amount with trailing currency code`() {
        assertThat(EntityExtractor.extract("Charged 2,999.00 INR today").amounts).contains("2,999.00")
    }

    @Test
    fun `extracts dollar amount`() {
        assertThat(EntityExtractor.extract("Subscription \$4.99 per month").amounts).contains("4.99")
    }

    // ----------------------------------------------------------------- OTP

    @Test
    fun `extracts otp when keyword precedes the code`() {
        val entities = EntityExtractor.extract("Your OTP is 483920. Do not share it with anyone.")
        assertThat(entities.otpCodes).containsExactly("483920")
    }

    @Test
    fun `extracts otp when code precedes the keyword`() {
        val entities = EntityExtractor.extract("192837 is your verification code for login")
        assertThat(entities.otpCodes).contains("192837")
    }

    @Test
    fun `does not treat a bare six digit number as an otp`() {
        // Pincodes and order numbers are six digits far more often than OTPs are.
        val entities = EntityExtractor.extract("Delivering to Bengaluru 560001 by Tuesday")
        assertThat(entities.otpCodes).isEmpty()
    }

    // --------------------------------------------------------------- phone

    @Test
    fun `extracts indian mobile number with and without country code`() {
        val entities = EntityExtractor.extract("Call me on +91 9876543210 or 8123456789")
        assertThat(entities.phoneNumbers).isNotEmpty()
        assertThat(entities.phoneNumbers.joinToString()).contains("9876543210")
    }

    @Test
    fun `an otp is not also reported as a phone number`() {
        val entities = EntityExtractor.extract("Your OTP is 483920")
        assertThat(entities.otpCodes).contains("483920")
        assertThat(entities.phoneNumbers).isEmpty()
    }

    @Test
    fun `a ten digit number is not treated as an otp`() {
        // Real OTPs are 4-8 digits. A 10-digit value beside the word "OTP" is far
        // more likely a support phone number, so the length bound matters.
        val entities = EntityExtractor.extract("For OTP help call 9876543210")

        assertThat(entities.otpCodes).isEmpty()
        assertThat(entities.phoneNumbers.joinToString()).contains("9876543210")
    }

    @Test
    fun `ignores numbers that start with an invalid indian prefix`() {
        assertThat(EntityExtractor.extract("Order 1234567890 shipped").phoneNumbers).isEmpty()
    }

    // ----------------------------------------------------------------- url

    @Test
    fun `extracts http and www urls`() {
        val entities = EntityExtractor.extract("See https://example.com/deal and www.shop.in/x")
        assertThat(entities.urls).hasSize(2)
        assertThat(entities.urls.first()).isEqualTo("https://example.com/deal")
    }

    @Test
    fun `does not report an email that is part of a url`() {
        val entities = EntityExtractor.extract("Visit https://site.com/u/a@b.com now")
        assertThat(entities.emails).isEmpty()
    }

    @Test
    fun `extracts a standalone email`() {
        assertThat(EntityExtractor.extract("Write to help@shelfie.app").emails)
            .containsExactly("help@shelfie.app")
    }

    // -------------------------------------------------------- refs and PNR

    @Test
    fun `extracts upi reference number`() {
        val entities = EntityExtractor.extract("UPI Ref No: 412345678901 Payment successful")
        assertThat(entities.referenceIds).contains("412345678901")
    }

    @Test
    fun `extracts utr`() {
        assertThat(EntityExtractor.extract("UTR 3216549870ABC").referenceIds)
            .contains("3216549870ABC")
    }

    @Test
    fun `extracts pnr and normalises case`() {
        assertThat(EntityExtractor.extract("PNR: 2456ab7890").pnrCodes).contains("2456AB7890")
    }

    // ----------------------------------------------------------- date/time

    @Test
    fun `extracts numeric and textual dates`() {
        assertThat(EntityExtractor.extract("Journey on 14/08/2026").dates).contains("14/08/2026")
        assertThat(EntityExtractor.extract("Departs 14 Aug 2026").dates).contains("14 Aug 2026")
        assertThat(EntityExtractor.extract("Show on Aug 14, 2026").dates).contains("Aug 14, 2026")
    }

    @Test
    fun `extracts time in both 12 and 24 hour form`() {
        assertThat(EntityExtractor.extract("Boarding at 06:45 AM").times).isNotEmpty()
        assertThat(EntityExtractor.extract("Departs 18:30").times).contains("18:30")
    }

    // ------------------------------------------------------------ password

    @Test
    fun `extracts wifi password`() {
        val entities = EntityExtractor.extract("SSID: CafeGuest\nPassword: Hunter2Guest!")
        assertThat(entities.passwords).contains("Hunter2Guest!")
    }

    // ------------------------------------------------------------ degenerate

    @Test
    fun `blank text yields empty entities`() {
        assertThat(EntityExtractor.extract("   ").isEmpty).isTrue()
    }

    @Test
    fun `text with no entities yields empty entities`() {
        assertThat(EntityExtractor.extract("hello there friend").isEmpty).isTrue()
    }
}
