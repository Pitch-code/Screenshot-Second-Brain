package com.shelfie.core.database

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.shelfie.core.model.ShelfFilter
import com.shelfie.core.model.ShelfSortOrder

/**
 * Builds the shelf's paging query.
 *
 * ### On assembling SQL as a string
 *
 * Normally a smell, so the constraints are worth stating: every fragment here is
 * selected by a `when` over a **closed enum or sealed type**, so the set of
 * possible SQL strings is fixed at compile time and enumerable by tests. The only
 * values that vary at runtime — a category name, a folder id — are passed as bound
 * arguments, never interpolated. Nothing a user types can reach this.
 *
 * The alternative was a dozen copy-pasted `@Query` methods, which is worse: SQLite
 * cannot parameterise `ORDER BY`, so each sort order needs its own statement, and
 * keeping twelve of them consistent by hand is exactly how an `is_deleted = 0` gets
 * forgotten from one branch.
 */
object ShelfQuery {

    /**
     * Every row carries a total ordering.
     *
     * The `id` tiebreaker is not cosmetic: Paging keys pages by offset, so two rows
     * that compare equal on the sort column could otherwise swap places between
     * page loads, which shows up as a duplicated or skipped tile. Sizes collide
     * often — identical screenshots, or a run of similar captures.
     */
    private fun orderBy(sort: ShelfSortOrder): String = when (sort) {
        ShelfSortOrder.NEWEST_FIRST -> "date_added DESC, id DESC"
        ShelfSortOrder.OLDEST_FIRST -> "date_added ASC, id ASC"
        ShelfSortOrder.LARGEST_FIRST -> "size_bytes DESC, id DESC"
        ShelfSortOrder.SMALLEST_FIRST -> "size_bytes ASC, id ASC"
    }

    fun shelf(filter: ShelfFilter, sort: ShelfSortOrder): SupportSQLiteQuery {
        val args = mutableListOf<Any>()

        val predicate = when (filter) {
            ShelfFilter.All -> ""

            // Filed screenshots are excluded from their automatic category: once
            // the user has put something in a folder, seeing it still listed under
            // the app's guess makes the move look like it did not work.
            is ShelfFilter.Category -> {
                args += filter.category.name
                " AND category = ? AND folder_id IS NULL"
            }

            is ShelfFilter.InFolder -> {
                args += filter.folderId
                " AND folder_id = ?"
            }
        }

        return SimpleSQLiteQuery(
            "SELECT * FROM screenshots WHERE is_deleted = 0$predicate ORDER BY ${orderBy(sort)}",
            args.toTypedArray(),
        )
    }
}
