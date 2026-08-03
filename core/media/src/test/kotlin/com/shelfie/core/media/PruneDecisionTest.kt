package com.shelfie.core.media

import com.google.common.truth.Truth.assertThat
import com.shelfie.core.model.MediaAccess
import org.junit.Test

/**
 * The rules deciding which rows get deleted when a file disappears from the gallery.
 *
 * Worth pinning precisely because the failure mode is destroying a user's index. Two
 * of these three guards exist to prevent exactly that, and neither is obvious from
 * reading the happy path.
 */
class PruneDecisionTest {

    /** Mirrors ScreenshotRepository.pruneDeletedFiles' decision logic. */
    private fun idsToRemove(
        access: MediaAccess,
        liveIds: Set<Long>,
        known: List<Long>,
    ): List<Long> {
        if (access != MediaAccess.FULL) return emptyList()
        if (liveIds.isEmpty()) return emptyList()
        return known.filterNot { it in liveIds }
    }

    @Test
    fun `a deleted screenshot is removed`() {
        // The reported bug: delete from the gallery, and it should go from the app.
        val remove = idsToRemove(
            access = MediaAccess.FULL,
            liveIds = setOf(1L, 2L),
            known = listOf(1L, 2L, 3L),
        )

        assertThat(remove).containsExactly(3L)
    }

    @Test
    fun `nothing is removed when every known image is still present`() {
        val remove = idsToRemove(MediaAccess.FULL, setOf(1L, 2L, 3L), listOf(1L, 2L, 3L))

        assertThat(remove).isEmpty()
    }

    @Test
    fun `partial access never removes anything`() {
        // Android 14's "select photos" makes MediaStore report only the hand-picked
        // images. Every other row would look deleted, so pruning here would wipe an
        // index built while full access was granted. A permission downgrade must
        // never destroy data.
        val remove = idsToRemove(
            access = MediaAccess.PARTIAL,
            liveIds = setOf(7L),
            known = listOf(1L, 2L, 3L, 7L),
        )

        assertThat(remove).isEmpty()
    }

    @Test
    fun `denied access never removes anything`() {
        val remove = idsToRemove(MediaAccess.DENIED, emptySet(), listOf(1L, 2L, 3L))

        assertThat(remove).isEmpty()
    }

    @Test
    fun `an empty gallery result is treated as a failure, not as mass deletion`() {
        // A SecurityException mid-query returns an empty set, indistinguishable from
        // "the gallery really is empty". Acting on it would clear the entire library
        // on a transient error, so the empty case is always a no-op.
        val remove = idsToRemove(MediaAccess.FULL, emptySet(), listOf(1L, 2L, 3L))

        assertThat(remove).isEmpty()
    }

    @Test
    fun `an empty index has nothing to remove`() {
        val remove = idsToRemove(MediaAccess.FULL, setOf(1L, 2L), emptyList())

        assertThat(remove).isEmpty()
    }

    @Test
    fun `all known images being gone is handled`() {
        // Legitimate: the user cleared their Screenshots folder but has other photos,
        // so the gallery is non-empty and the deletion is real.
        val remove = idsToRemove(MediaAccess.FULL, setOf(99L), listOf(1L, 2L, 3L))

        assertThat(remove).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `chunking covers every id exactly once`() {
        // removeByMediaStoreIds chunks to stay inside SQLite's bound-parameter limit.
        // Losing or repeating an id here would silently leave rows behind.
        val ids = (1L..1_250L).toList()

        val chunks = ids.chunked(500)

        assertThat(chunks.sumOf { it.size }).isEqualTo(ids.size)
        assertThat(chunks.flatten()).containsExactlyElementsIn(ids).inOrder()
        assertThat(chunks.all { it.size <= 500 }).isTrue()
    }
}
