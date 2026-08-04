package com.shelfie.core.database

import androidx.paging.PagingSource
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.model.IndexState
import com.shelfie.core.model.ScreenshotCategory
import com.shelfie.core.model.ShelfFilter
import com.shelfie.core.model.ShelfSortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests the category list against a real SQLite database.
 *
 * These exist because of a shipped bug that pure-Kotlin tests could never have caught.
 * The Find tab's category list was computed with `index_state = 'INDEXED'`, while the
 * view it links to had no state filter at all. The two queries disagreed, so a
 * screenshot the user deliberately moved into a category simply vanished: the category
 * did not appear, and no error was raised anywhere.
 *
 * The invariant being locked down is the one that broke — **if opening a category would
 * show a screenshot, the category list must count it.** Asserted by running both real
 * queries and comparing, rather than by re-implementing either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryVisibilityTest {

    private lateinit var database: ShelfieDatabase
    private lateinit var dao: ScreenshotDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ShelfieDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.screenshotDao()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun insert(
        mediaStoreId: Long,
        category: ScreenshotCategory,
        state: IndexState,
        confidence: Float = 0.5f,
    ) {
        dao.upsert(
            ScreenshotEntity(
                mediaStoreId = mediaStoreId,
                uri = "content://media/external/images/media/$mediaStoreId",
                displayName = "Screenshot_$mediaStoreId.png",
                relativePath = "Pictures/Screenshots/",
                dateAdded = 1_700_000_000L + mediaStoreId,
                sizeBytes = 1_000,
                width = 1080,
                height = 2400,
                indexState = state,
                category = category,
                categoryConfidence = confidence,
            ),
        )
    }

    /** Runs the real paging query and counts what it would actually display. */
    private suspend fun browseCount(filter: ShelfFilter): Int {
        val source = dao.pagedShelf(filter, ShelfSortOrder.NEWEST_FIRST)
        val page = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 500,
                placeholdersEnabled = false,
            ),
        )
        return (page as PagingSource.LoadResult.Page).data.size
    }

    @Test
    fun `a single manually moved screenshot makes its category appear`() = runTest {
        // The exact report: hold a screenshot, move it to OTP codes, and OTP codes
        // never showed up in the Find tab.
        insert(1, ScreenshotCategory.OTP_CODES, IndexState.INDEXED, confidence = 1.0f)

        val categories = dao.observeCategoryCounts().first()

        assertThat(categories.map { it.category }).contains(ScreenshotCategory.OTP_CODES)
        assertThat(categories.first { it.category == ScreenshotCategory.OTP_CODES }.count)
            .isEqualTo(1)
    }

    @Test
    fun `held back screenshots still count towards their category`() = runTest {
        // On the free tier most of a large library is QUOTA_HELD. Those screenshots are
        // still on the shelf and still browsable, so excluding them made categories
        // vanish for exactly the users who had the most screenshots.
        insert(1, ScreenshotCategory.PAYMENTS, IndexState.QUOTA_HELD)

        val categories = dao.observeCategoryCounts().first()

        assertThat(categories.map { it.category }).contains(ScreenshotCategory.PAYMENTS)
    }

    @Test
    fun `screenshots not yet read still count towards their category`() = runTest {
        insert(1, ScreenshotCategory.TICKETS, IndexState.PENDING)

        assertThat(dao.observeCategoryCounts().first().map { it.category })
            .contains(ScreenshotCategory.TICKETS)
    }

    @Test
    fun `the list and the browse view agree for every state`() = runTest {
        // The invariant that broke. Every index state is represented, so a filter added
        // to one query and not the other fails here.
        insert(1, ScreenshotCategory.WIFI_PASSWORDS, IndexState.INDEXED)
        insert(2, ScreenshotCategory.WIFI_PASSWORDS, IndexState.QUOTA_HELD)
        insert(3, ScreenshotCategory.WIFI_PASSWORDS, IndexState.PENDING)
        insert(4, ScreenshotCategory.WIFI_PASSWORDS, IndexState.FAILED)
        insert(5, ScreenshotCategory.WIFI_PASSWORDS, IndexState.SKIPPED)

        val listed = dao.observeCategoryCounts().first()
            .first { it.category == ScreenshotCategory.WIFI_PASSWORDS }
            .count
        val browsable = browseCount(ShelfFilter.Category(ScreenshotCategory.WIFI_PASSWORDS))

        assertThat(listed).isEqualTo(browsable)
        assertThat(listed).isEqualTo(5)
    }

    @Test
    fun `a screenshot filed into a folder leaves its category count`() = runTest {
        val folderId = dao.insertFolder(
            com.shelfie.core.database.entity.newFolderEntity(
                rawName = "Work",
                icon = com.shelfie.core.model.FolderIcon.WORK,
                createdAt = 0,
            ),
        )
        insert(1, ScreenshotCategory.DOCUMENTS, IndexState.INDEXED)
        insert(2, ScreenshotCategory.DOCUMENTS, IndexState.INDEXED)

        // Row ids are autogenerated and are not the MediaStore ids, so the real one has
        // to be looked up rather than assumed.
        val rowId = dao.idsForMediaStoreIds(listOf(1L)).single()
        dao.setFolder(id = rowId, folderId = folderId)

        // Filed rows are excluded from category counts, matching the browse query, so
        // moving something to a folder visibly removes it from its old category.
        val listed = dao.observeCategoryCounts().first()
            .firstOrNull { it.category == ScreenshotCategory.DOCUMENTS }
            ?.count ?: 0
        val browsable = browseCount(ShelfFilter.Category(ScreenshotCategory.DOCUMENTS))

        assertThat(listed).isEqualTo(browsable)
    }

    @Test
    fun `deleted screenshots are excluded from both`() = runTest {
        insert(1, ScreenshotCategory.RECIPES, IndexState.INDEXED)
        insert(2, ScreenshotCategory.RECIPES, IndexState.INDEXED)
        dao.softDelete(listOf(1L), deletedAt = 0)

        val listed = dao.observeCategoryCounts().first()
            .first { it.category == ScreenshotCategory.RECIPES }
            .count

        assertThat(listed).isEqualTo(browseCount(ShelfFilter.Category(ScreenshotCategory.RECIPES)))
    }

    @Test
    fun `an empty category does not appear`() = runTest {
        insert(1, ScreenshotCategory.PLACES, IndexState.INDEXED)

        val categories = dao.observeCategoryCounts().first().map { it.category }

        // Listing all twelve regardless would fill the tab with dead ends.
        assertThat(categories).doesNotContain(ScreenshotCategory.STUDY)
        assertThat(categories).contains(ScreenshotCategory.PLACES)
    }

    @Test
    fun `folders appear even while empty`() = runTest {
        dao.insertFolder(
            com.shelfie.core.database.entity.newFolderEntity(
                rawName = "Bills",
                icon = com.shelfie.core.model.FolderIcon.MONEY,
                createdAt = 0,
            ),
        )

        // Unlike categories: the user made this deliberately, so hiding it until it
        // reaches a threshold would look like the app lost it.
        val folders = dao.observeFolderCounts().first()

        assertThat(folders).hasSize(1)
        assertThat(folders.first().name).isEqualTo("Bills")
        assertThat(folders.first().count).isEqualTo(0)
    }
}
