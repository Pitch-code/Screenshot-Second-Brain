package com.shelfie.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FolderTest {

    @Test
    fun `names are trimmed so near-duplicates cannot be created`() {
        assertThat(Folder.normaliseName("  Work  ")).isEqualTo("Work")
    }

    @Test
    fun `names are capped to what a chip can display`() {
        val long = "a".repeat(Folder.MAX_NAME_LENGTH + 20)

        assertThat(Folder.normaliseName(long)).hasLength(Folder.MAX_NAME_LENGTH)
    }

    @Test
    fun `blank and whitespace-only names are rejected`() {
        assertThat(Folder.isValidName("")).isFalse()
        assertThat(Folder.isValidName("   ")).isFalse()
        assertThat(Folder.isValidName("\n\t")).isFalse()
    }

    @Test
    fun `a name with real characters is accepted`() {
        assertThat(Folder.isValidName(" Bills ")).isTrue()
    }

    @Test
    fun `unknown stored icon falls back rather than throwing`() {
        // A row written by a newer version, or a downgrade, must not crash the
        // shelf — every folder has to render something.
        assertThat(FolderIcon.fromNameOrDefault("NOT_A_REAL_ICON")).isEqualTo(FolderIcon.FOLDER)
        assertThat(FolderIcon.fromNameOrDefault(null)).isEqualTo(FolderIcon.FOLDER)
    }

    @Test
    fun `known stored icon round-trips`() {
        FolderIcon.entries.forEach { icon ->
            assertThat(FolderIcon.fromNameOrDefault(icon.name)).isEqualTo(icon)
        }
    }
}

class ShelfSortOrderTest {

    @Test
    fun `only date orders group by date`() {
        // Size sorts must not insert date headers: the list crosses days
        // arbitrarily, which would repeat a day's header and produce duplicate
        // Paging keys.
        assertThat(ShelfSortOrder.NEWEST_FIRST.groupsByDate).isTrue()
        assertThat(ShelfSortOrder.OLDEST_FIRST.groupsByDate).isTrue()
        assertThat(ShelfSortOrder.LARGEST_FIRST.groupsByDate).isFalse()
        assertThat(ShelfSortOrder.SMALLEST_FIRST.groupsByDate).isFalse()
    }

    @Test
    fun `unknown stored sort order falls back to the default`() {
        assertThat(ShelfSortOrder.fromNameOrDefault("BY_COLOUR"))
            .isEqualTo(ShelfSortOrder.Default)
        assertThat(ShelfSortOrder.fromNameOrDefault(null)).isEqualTo(ShelfSortOrder.Default)
    }

    @Test
    fun `every sort order round-trips through its stored name`() {
        ShelfSortOrder.entries.forEach { order ->
            assertThat(ShelfSortOrder.fromNameOrDefault(order.name)).isEqualTo(order)
        }
    }
}
