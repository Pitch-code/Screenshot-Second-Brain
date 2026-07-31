package com.shelfie.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Full-text search index over the OCR output.
 *
 * The FTS table's implicit `rowid` is set to the owning screenshot's id, which
 * makes the join in [com.shelfie.core.database.dao.ScreenshotDao.search] a
 * cheap integer lookup rather than a string comparison.
 *
 * FTS4 rather than FTS5 because FTS4 is guaranteed present on every Android
 * version we support; FTS5 availability varies by OEM SQLite build.
 */
@Fts4
@Entity(tableName = "screenshot_text_fts")
data class ScreenshotTextEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val screenshotId: Long,

    /** The raw text recognised in the image. */
    @ColumnInfo(name = "text")
    val text: String,
)
