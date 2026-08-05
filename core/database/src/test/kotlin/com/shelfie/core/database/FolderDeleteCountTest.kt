package com.shelfie.core.database

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.FolderEntity
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.model.IndexState
import com.shelfie.core.model.ScreenshotCategory
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
 * Tests the number quoted in the folder delete confirmation, against real SQLite.
 *
 * A user selected two folders holding three screenshots between them and was told
 * there were two. The number in that dialog decides whether someone agrees to delete
 * their own pictures permanently, so it has to be right, and it has to be right for
 * the same reason the folder cards are right — the two must not be able to disagree.
 *
 * The invariant locked down here: **the count in the prompt equals the sum of the
 * counts on the cards.** Asserted by running both real queries and comparing, never by
 * re-implementing either, because a re-implementation would agree with itself while
 * both disagreed with the database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderDeleteCountTest {

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

    private suspend fun folder(name: String): Long =
        dao.insertFolder(FolderEntity(name = name, nameKey = name.lowercase()))

    private suspend fun insert(
        mediaStoreId: Long,
        folderId: Long? = null,
        isDeleted: Boolean = false,
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
                indexState = IndexState.INDEXED,
                category = ScreenshotCategory.NOT_SORTED,
                categoryConfidence = 0f,
                folderId = folderId,
                isDeleted = isDeleted,
            ),
        )
    }

    @Test
    fun `the reported case - two folders holding three screenshots count three`() = runTest {
        val first = folder("test1")
        val second = folder("test2")

        insert(1, folderId = first)
        insert(2, folderId = second)
        insert(3, folderId = second)

        val affected = dao.screenshotIdsInFolders(listOf(first, second))

        assertThat(affected).hasSize(3)
    }

    @Test
    fun `the prompt count always equals the sum of the card counts`() = runTest {
        val first = folder("bills")
        val second = folder("warranties")
        val untouched = folder("recipes")

        insert(1, folderId = first)
        insert(2, folderId = second)
        insert(3, folderId = second)
        insert(4, folderId = untouched)
        insert(5) // Filed nowhere.

        val cards = dao.observeFolderCounts().first().associateBy { it.folderId }
        val selected = listOf(first, second)

        val fromCards = selected.sumOf { id -> cards.getValue(id).count }
        val fromPrompt = dao.screenshotIdsInFolders(selected).size

        assertThat(fromPrompt).isEqualTo(fromCards)
        assertThat(fromPrompt).isEqualTo(3)
    }

    @Test
    fun `a trashed screenshot is excluded from both, so they still agree`() = runTest {
        // The likeliest way for the two to drift: one query filters soft-deleted rows
        // and the other does not. A screenshot in the bin must be invisible to both.
        val bills = folder("bills")

        insert(1, folderId = bills)
        insert(2, folderId = bills, isDeleted = true)

        val cards = dao.observeFolderCounts().first()
        val cardCount = cards.first { it.folderId == bills }.count
        val promptCount = dao.screenshotIdsInFolders(listOf(bills)).size

        assertThat(cardCount).isEqualTo(1)
        assertThat(promptCount).isEqualTo(1)
    }

    @Test
    fun `an empty folder counts zero rather than one`() = runTest {
        // The LEFT JOIN behind the card counts would report 1 for an empty folder if it
        // counted rows instead of joined screenshots.
        val empty = folder("empty")

        val cards = dao.observeFolderCounts().first()

        assertThat(cards.first { it.folderId == empty }.count).isEqualTo(0)
        assertThat(dao.screenshotIdsInFolders(listOf(empty))).isEmpty()
    }

    @Test
    fun `screenshots in unselected folders are never counted`() = runTest {
        val selected = folder("selected")
        val other = folder("other")

        insert(1, folderId = selected)
        insert(2, folderId = other)
        insert(3, folderId = other)

        assertThat(dao.screenshotIdsInFolders(listOf(selected))).hasSize(1)
    }

    @Test
    fun `no folders selected counts nothing`() = runTest {
        insert(1, folderId = folder("bills"))

        assertThat(dao.screenshotIdsInFolders(emptyList())).isEmpty()
    }
}
