package com.shelfie.core.classify

import com.shelfie.core.model.ScreenshotCategory

/**
 * A weighted piece of evidence for one category.
 *
 * Weights are coarse on purpose — 3.0 means "this phrase alone nearly settles
 * it", 1.0 means "mildly suggestive". Fine-tuning beyond that overfits to
 * whatever sample text we happened to test with.
 */
internal data class Signal(
    val pattern: Regex,
    val weight: Double,
) {
    fun score(text: String): Double = if (pattern.containsMatchIn(text)) weight else 0.0
}

private fun words(vararg terms: String, weight: Double): List<Signal> =
    terms.map { term ->
        // \b around a whole phrase, case-insensitive, with flexible whitespace.
        val escaped = term.split(" ").joinToString("""\s+""") { Regex.escape(it) }
        Signal(Regex("""\b$escaped\b""", RegexOption.IGNORE_CASE), weight)
    }

private fun patterns(vararg regexes: String, weight: Double): List<Signal> =
    regexes.map { Signal(Regex(it, RegexOption.IGNORE_CASE), weight) }

/**
 * Signal table.
 *
 * Heavily India-weighted, because that is the launch market: UPI apps, IRCTC,
 * Aadhaar/PAN, and the local commerce brands appear constantly in screenshots
 * there and almost never in a generic English keyword list.
 */
internal object CategorySignals {

    val table: Map<ScreenshotCategory, List<Signal>> = mapOf(
        ScreenshotCategory.PAYMENTS to buildList {
            addAll(words("payment successful", "paid to", "transaction successful", "payment of", "debited", "credited", "sent to", "money sent", "transfer successful", weight = 3.0))
            addAll(words("upi", "utr", "imps", "neft", "rtgs", "upi id", "bank account", weight = 2.0))
            addAll(words("paytm", "phonepe", "google pay", "gpay", "bhim", "amazon pay", "razorpay", weight = 2.0))
            addAll(words("receipt", "invoice", "amount", "balance", "transaction id", "reference no", weight = 1.5))
            addAll(patterns("""(?:₹|rs\.?|inr)\s?[0-9]""", weight = 2.0))
        },

        ScreenshotCategory.OTP_CODES to buildList {
            addAll(words("otp", "one time password", "one-time password", "verification code", "security code", "do not share", "never share", "valid for", weight = 3.0))
            addAll(words("expires in", "authentication code", "passcode", "2fa", weight = 2.0))
            addAll(patterns("""\b[0-9]{4,8}\b\s*is\s+your""", weight = 3.0))
        },

        ScreenshotCategory.TICKETS to buildList {
            addAll(words("pnr", "booking confirmed", "booking id", "e-ticket", "boarding pass", "seat no", "coach", "platform", "gate", "departure", "arrival", weight = 3.0))
            addAll(words("irctc", "indigo", "air india", "spicejet", "vistara", "redbus", "makemytrip", "bookmyshow", "pvr", "inox", weight = 2.5))
            addAll(words("train", "flight", "bus", "movie", "showtime", "screen", "row", "check-in", weight = 1.5))
            addAll(words("passenger", "journey", "travel date", "ticket", weight = 1.5))
        },

        ScreenshotCategory.WIFI_PASSWORDS to buildList {
            addAll(words("wifi password", "wi-fi password", "ssid", "network name", "network key", "passphrase", "wpa2", "wpa3", weight = 3.0))
            addAll(words("wifi", "wi-fi", "hotspot", "connect to network", weight = 1.5))
            addAll(patterns("""\bpass(?:word|phrase)\s*[:\-]""", weight = 2.0))
        },

        ScreenshotCategory.PRODUCTS to buildList {
            addAll(words("add to cart", "buy now", "add to bag", "out of stock", "in stock", "free delivery", "delivery by", "order now", "checkout", weight = 3.0))
            addAll(words("amazon", "flipkart", "myntra", "meesho", "ajio", "nykaa", "blinkit", "zepto", "bigbasket", weight = 2.0))
            addAll(words("offer price", "mrp", "discount", "off", "ratings", "reviews", "wishlist", "deal of the day", weight = 1.5))
        },

        ScreenshotCategory.CHATS to buildList {
            addAll(words("typing", "online", "last seen", "you deleted this message", "this message was deleted", "forwarded", weight = 3.0))
            addAll(words("whatsapp", "telegram", "instagram", "messenger", "snapchat", weight = 2.0))
            addAll(words("replied to", "reacted to", "voice message", "seen", weight = 1.5))
        },

        ScreenshotCategory.DOCUMENTS to buildList {
            addAll(words("aadhaar", "aadhar", "pan card", "permanent account number", "driving licence", "driving license", "passport no", "voter id", weight = 3.0))
            addAll(words("date of birth", "valid until", "valid till", "expiry date", "issued on", "government of india", weight = 2.0))
            addAll(words("policy number", "insurance", "certificate", "registration no", "enrolment", weight = 1.5))
        },

        ScreenshotCategory.RECIPES to buildList {
            addAll(words("ingredients", "preheat", "serves", "prep time", "cook time", "recipe", weight = 3.0))
            addAll(patterns("""\b[0-9½¼¾]+\s?(?:cups?|tbsp|tsp|tablespoons?|teaspoons?|grams?|kg|ml|litres?)\b""", weight = 2.5))
            addAll(words("saute", "simmer", "garnish", "marinate", "knead", "whisk", "bake", weight = 1.5))
        },

        ScreenshotCategory.PLACES to buildList {
            addAll(words("directions", "eta", "km away", "nearby", "open now", "closed now", "get directions", weight = 3.0))
            addAll(words("google maps", "swiggy", "zomato", "uber", "ola", "rapido", weight = 2.0))
            addAll(patterns("""\b[0-9]+(?:\.[0-9]+)?\s?km\b""", """\bpin\s?code\s*[:\-]?\s*[0-9]{6}\b""", weight = 2.0))
            addAll(words("address", "landmark", "sector", "road", "nagar", weight = 1.0))
        },

        ScreenshotCategory.STUDY to buildList {
            addAll(words("syllabus", "chapter", "marks", "answer key", "question paper", "assignment", "semester", "exam", weight = 3.0))
            addAll(words("lecture", "notes", "tutorial", "homework", "solution", "theorem", weight = 1.5))
            addAll(patterns("""\bq\s?[0-9]{1,2}[.)]""", weight = 1.5))
        },

        ScreenshotCategory.CONTACTS to buildList {
            addAll(words("save contact", "add to contacts", "phone number", "mobile no", "contact details", "call now", weight = 3.0))
            addAll(patterns("""(?:\+?91[\s\-]?)?\b[6-9][0-9]{9}\b""", weight = 1.5))
        },
    )
}
