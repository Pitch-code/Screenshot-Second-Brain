package com.shelfie.core.model

/**
 * Which colour scheme the app uses.
 *
 * [SYSTEM] is the default rather than [DARK], even though the app is designed
 * dark-first. Someone who has set their phone to light mode has already stated a
 * preference, and overriding it by default is the app deciding it knows better.
 */
enum class ThemeMode {
    /** Follow the phone's own light/dark setting. */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        val Default = SYSTEM

        /**
         * Decodes a stored name, falling back to the default.
         *
         * Never throws: a value written by a newer build that is then downgraded
         * must not make the app unable to pick a theme, which would fail before
         * anything could be drawn.
         */
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
