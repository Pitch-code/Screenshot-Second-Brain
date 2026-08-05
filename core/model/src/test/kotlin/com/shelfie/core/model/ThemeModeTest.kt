package com.shelfie.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `each stored name decodes back to itself`() {
        ThemeMode.entries.forEach { mode ->
            assertThat(ThemeMode.fromName(mode.name)).isEqualTo(mode)
        }
    }

    @Test
    fun `an absent preference gives the default`() {
        assertThat(ThemeMode.fromName(null)).isEqualTo(ThemeMode.Default)
    }

    @Test
    fun `an unrecognised name gives the default rather than throwing`() {
        // A value written by a newer build and then read after a downgrade. Throwing
        // here would fail before the theme could be chosen, so before anything at all
        // could be drawn — the app would not start.
        assertThat(ThemeMode.fromName("SEPIA")).isEqualTo(ThemeMode.Default)
        assertThat(ThemeMode.fromName("")).isEqualTo(ThemeMode.Default)
        assertThat(ThemeMode.fromName("light")).isEqualTo(ThemeMode.Default)
    }

    @Test
    fun `the default follows the phone`() {
        // Not DARK, even though the app is designed dark-first: someone who set their
        // phone to light has already said what they want.
        assertThat(ThemeMode.Default).isEqualTo(ThemeMode.SYSTEM)
    }
}
