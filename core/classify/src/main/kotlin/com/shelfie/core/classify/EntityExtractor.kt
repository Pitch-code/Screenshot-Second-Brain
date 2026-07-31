package com.shelfie.core.classify

/**
 * Pulls structured values out of OCR text using deterministic patterns.
 *
 * Deliberately not machine learning. This runs on every screenshot on devices
 * with no AICore and 3GB of RAM, so it has to be instant, predictable, and
 * debuggable. Regex gets the common Indian and global formats right, and when
 * it finds nothing the app degrades to plain full-text search rather than
 * guessing.
 */
object EntityExtractor {

    // Currency symbol or code, then a number. Also handles "1,234.50 INR".
    private val AMOUNT_PREFIXED = Regex(
        """(?:₹|Rs\.?|INR|US\$|\$|€|£)\s?([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val AMOUNT_SUFFIXED = Regex(
        """([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s?(?:INR|rupees|rs\.?)\b""",
        RegexOption.IGNORE_CASE,
    )

    // An OTP is only an OTP when a nearby word says so — bare 6-digit numbers
    // are far too common (pincodes, order counts) to treat as codes.
    private val OTP_KEYWORD_FIRST = Regex(
        """(?:OTP|one[\s-]?time\s?(?:password|pin|code)|verification\s?code|security\s?code|auth(?:entication)?\s?code|passcode)\b\D{0,24}?([0-9]{4,8})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val OTP_CODE_FIRST = Regex(
        """\b([0-9]{4,8})\b\s*(?:is\s+(?:your|the)\s+)?(?:OTP|one[\s-]?time\s?(?:password|pin|code)|verification\s?code|security\s?code)""",
        RegexOption.IGNORE_CASE,
    )

    private val URL = Regex(
        """\b((?:https?://|www\.)[^\s<>"'\\)\]]{3,})""",
        RegexOption.IGNORE_CASE,
    )

    private val EMAIL = Regex(
        """\b([A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,})\b""",
    )

    // Indian mobile numbers (start 6-9, ten digits), optionally +91 prefixed.
    private val PHONE_IN = Regex("""(?:\+?91[\s\-]?)?\b([6-9][0-9]{9})\b""")

    // Explicit international form, e.g. +1 415 555 0132.
    private val PHONE_INTL = Regex("""(\+[1-9][0-9]{0,2}[\s\-]?(?:[0-9][\s\-]?){7,12})""")

    private val DATE_NUMERIC = Regex(
        """\b([0-3]?[0-9][/\-.][0-1]?[0-9][/\-.](?:20)?[0-9]{2})\b""",
    )
    private val DATE_TEXTUAL = Regex(
        """\b([0-3]?[0-9]\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?,?\s+(?:20)?[0-9]{2})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val DATE_TEXTUAL_REVERSED = Regex(
        """\b((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+[0-3]?[0-9],?\s+(?:20)?[0-9]{2})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val TIME = Regex(
        """\b((?:[0-1]?[0-9]|2[0-3]):[0-5][0-9](?:\s?[AaPp]\.?[Mm]\.?)?)\b""",
    )

    private val REFERENCE_ID = Regex(
        """(?:UPI\s?(?:Ref(?:erence)?|Txn|Transaction)?\s?(?:No\.?|ID|Id)?|UTR(?:\s?No\.?)?|Transaction\s?(?:ID|Id|No\.?)|Txn\s?(?:ID|No\.?)|Ref(?:erence)?\s?(?:No\.?|ID))\s*[:#\-]?\s*([A-Za-z0-9]{6,24})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val PNR = Regex(
        """\bPNR\s?(?:No\.?|Number)?\s*[:#\-]?\s*([A-Z0-9]{6,10})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val PASSWORD = Regex(
        """(?:pass(?:word|phrase)|wi[\s-]?fi\s?(?:key|password)|network\s?key)\s*[:\-]?\s*(\S{6,40})""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(text: String): ExtractedEntities {
        if (text.isBlank()) return ExtractedEntities.Empty

        val urls = URL.captures(text)
        // Emails and phone numbers inside a URL are part of the link, not
        // standalone contact details.
        val emails = EMAIL.captures(text).filterNot { email -> urls.any { it.contains(email) } }

        val otps = (OTP_KEYWORD_FIRST.captures(text) + OTP_CODE_FIRST.captures(text)).distinct()

        val phones = (PHONE_IN.captures(text) + PHONE_INTL.captures(text))
            .map { it.trim() }
            // An OTP is not a phone number, and neither is part of a URL.
            .filterNot { phone -> otps.any { phone.contains(it) } }
            .filterNot { phone -> urls.any { it.contains(phone) } }
            .map { it.replace(Regex("""[\s\-]+"""), " ") }
            .distinct()

        return ExtractedEntities(
            amounts = (AMOUNT_PREFIXED.captures(text) + AMOUNT_SUFFIXED.captures(text)).distinct(),
            otpCodes = otps,
            urls = urls,
            emails = emails,
            phoneNumbers = phones,
            dates = (
                DATE_NUMERIC.captures(text) +
                    DATE_TEXTUAL.captures(text) +
                    DATE_TEXTUAL_REVERSED.captures(text)
                ).distinct(),
            times = TIME.captures(text).distinct(),
            referenceIds = REFERENCE_ID.captures(text).distinct(),
            pnrCodes = PNR.captures(text).map { it.uppercase() }.distinct(),
            passwords = PASSWORD.captures(text).distinct(),
        )
    }

    /** All first-group captures, trimmed, with blanks dropped. */
    private fun Regex.captures(input: String): List<String> =
        findAll(input)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .filter { it.isNotBlank() }
            .toList()
}
