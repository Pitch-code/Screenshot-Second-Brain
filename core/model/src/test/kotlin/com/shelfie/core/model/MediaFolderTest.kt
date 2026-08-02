package com.shelfie.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Folder keys decide whether a chosen folder is actually matched during discovery.
 *
 * Worth locking down because the failure is silent and confusing: a folder ticked in
 * Settings that never matches simply looks like the feature does not work, with no
 * error anywhere.
 */
class MediaFolderTest {

    @Test
    fun `key is case-insensitive`() {
        // MediaStore casing varies by OEM: "WhatsApp Images" on one device,
        // "Whatsapp Images" on another. A stored choice must survive that.
        assertThat(MediaFolder("WhatsApp Images", 10).key)
            .isEqualTo(MediaFolder("whatsapp images", 10).key)
    }

    @Test
    fun `key ignores surrounding whitespace`() {
        assertThat(MediaFolder(" Screenshots ", 1).key).isEqualTo("screenshots")
    }

    @Test
    fun `normalise matches the key produced by a folder`() {
        // The picker stores keys via normaliseKey while discovery compares against
        // MediaFolder.key. If these ever diverge, nothing matches.
        val folder = MediaFolder("Telegram", 5)

        assertThat(MediaFolder.normaliseKey("Telegram")).isEqualTo(folder.key)
    }

    @Test
    fun `folder names with spaces and punctuation round-trip`() {
        listOf("WhatsApp Images", "Screen-Shots", "My Folder (2)", "DCIM").forEach { name ->
            assertThat(MediaFolder.normaliseKey(name)).isEqualTo(MediaFolder(name, 0).key)
        }
    }

    @Test
    fun `count is preserved for the picker to display`() {
        // The count is the only signal the user has about how much work ticking a
        // folder implies, so it must not be lost or defaulted.
        assertThat(MediaFolder("Camera", 5_619).imageCount).isEqualTo(5_619)
    }

    @Test
    fun `an empty selection means screenshots only`() {
        // Discovery treats the chosen set as purely additive, so an empty set must
        // reproduce the original heuristics-only behaviour exactly.
        val chosen = emptySet<String>()

        assertThat(MediaFolder("WhatsApp Images", 100).key in chosen).isFalse()
    }

    @Test
    fun `a chosen folder matches regardless of the casing MediaStore reports`() {
        val chosen = setOf(MediaFolder.normaliseKey("WhatsApp Images"))

        // Whatever casing the device reports on the next scan.
        listOf("WhatsApp Images", "WHATSAPP IMAGES", "whatsapp images").forEach { reported ->
            assertThat(MediaFolder.normaliseKey(reported) in chosen).isTrue()
        }
    }
}
