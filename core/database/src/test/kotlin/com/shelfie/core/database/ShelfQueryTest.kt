package com.shelfie.core.database

import com.google.common.truth.Truth.assertThat
import com.shelfie.core.model.ScreenshotCategory
import com.shelfie.core.model.ShelfFilter
import com.shelfie.core.model.ShelfSortOrder
import org.junit.Test

/**
 * The shelf query is assembled as a string, so these tests exist to make that
 * safe: they enumerate every filter and sort combination and assert the invariants
 * that hand-written SQL variants would eventually break.
 */
class ShelfQueryTest {

    private val allFilters = listOf(
        ShelfFilter.All,
        ShelfFilter.Category(ScreenshotCategory.RECIPES),
        ShelfFilter.InFolder(7L),
    )

    @Test
    fun `every combination excludes soft-deleted rows`() {
        // The single most damaging thing to forget in a copied query: deleted
        // screenshots reappearing on the shelf.
        allFilters.forEach { filter ->
            ShelfSortOrder.entries.forEach { sort ->
                assertThat(ShelfQuery.shelf(filter, sort).sql).contains("is_deleted = 0")
            }
        }
    }

    @Test
    fun `every combination has a total ordering`() {
        // Without the id tiebreaker, rows that tie on the sort column can swap
        // between page loads, which surfaces as duplicated or missing tiles.
        allFilters.forEach { filter ->
            ShelfSortOrder.entries.forEach { sort ->
                assertThat(ShelfQuery.shelf(filter, sort).sql).contains("id")
            }
        }
    }

    @Test
    fun `sort order maps to the expected column and direction`() {
        fun sqlFor(sort: ShelfSortOrder) = ShelfQuery.shelf(ShelfFilter.All, sort).sql

        assertThat(sqlFor(ShelfSortOrder.NEWEST_FIRST)).contains("date_added DESC")
        assertThat(sqlFor(ShelfSortOrder.OLDEST_FIRST)).contains("date_added ASC")
        assertThat(sqlFor(ShelfSortOrder.LARGEST_FIRST)).contains("size_bytes DESC")
        assertThat(sqlFor(ShelfSortOrder.SMALLEST_FIRST)).contains("size_bytes ASC")
    }

    @Test
    fun `unfiltered query binds no arguments and adds no predicate`() {
        val query = ShelfQuery.shelf(ShelfFilter.All, ShelfSortOrder.NEWEST_FIRST)

        assertThat(query.argCount).isEqualTo(0)
        assertThat(query.sql).doesNotContain("category =")
        assertThat(query.sql).doesNotContain("folder_id =")
    }

    @Test
    fun `category filter hides screenshots the user has filed into a folder`() {
        // Otherwise moving something to a folder appears to do nothing, because it
        // stays listed under the category it was moved out of.
        val query = ShelfQuery.shelf(
            ShelfFilter.Category(ScreenshotCategory.PAYMENTS),
            ShelfSortOrder.NEWEST_FIRST,
        )

        assertThat(query.sql).contains("category = ?")
        assertThat(query.sql).contains("folder_id IS NULL")
        assertThat(query.argCount).isEqualTo(1)
    }

    @Test
    fun `folder filter binds the folder id rather than interpolating it`() {
        val query = ShelfQuery.shelf(ShelfFilter.InFolder(42L), ShelfSortOrder.OLDEST_FIRST)

        assertThat(query.sql).contains("folder_id = ?")
        assertThat(query.sql).doesNotContain("42")
        assertThat(query.argCount).isEqualTo(1)
    }

    @Test
    fun `filter values are never interpolated into the sql`() {
        // The reason string-built SQL is acceptable here at all.
        val query = ShelfQuery.shelf(
            ShelfFilter.Category(ScreenshotCategory.OTP_CODES),
            ShelfSortOrder.NEWEST_FIRST,
        )

        assertThat(query.sql).doesNotContain("OTP_CODES")
    }
}
