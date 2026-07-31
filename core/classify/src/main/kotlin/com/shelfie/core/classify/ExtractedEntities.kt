package com.shelfie.core.classify

/**
 * Structured values pulled out of a screenshot's recognised text.
 *
 * These are what make the index feel alive: a shelf tile shows an amount or an
 * OTP rather than a filename, and each entity implies an action the user can
 * take without opening the image.
 */
data class ExtractedEntities(
    val amounts: List<String> = emptyList(),
    val otpCodes: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val dates: List<String> = emptyList(),
    val times: List<String> = emptyList(),
    /** UPI reference / UTR / transaction id. */
    val referenceIds: List<String> = emptyList(),
    val pnrCodes: List<String> = emptyList(),
    val passwords: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = amounts.isEmpty() && otpCodes.isEmpty() && urls.isEmpty() &&
            emails.isEmpty() && phoneNumbers.isEmpty() && dates.isEmpty() &&
            times.isEmpty() && referenceIds.isEmpty() && pnrCodes.isEmpty() &&
            passwords.isEmpty()

    companion object {
        val Empty = ExtractedEntities()
    }
}
