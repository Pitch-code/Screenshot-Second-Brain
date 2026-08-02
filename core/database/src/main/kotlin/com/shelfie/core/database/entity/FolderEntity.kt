package com.shelfie.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shelfie.core.model.Folder
import com.shelfie.core.model.FolderIcon

/**
 * A user-created folder. Added in schema v4.
 *
 * `name_key` exists so uniqueness can be enforced case-insensitively by the
 * database rather than by a check-then-insert in Kotlin, which would race two
 * rapid creations into duplicate folders.
 */
@Entity(
    tableName = "folders",
    indices = [Index(value = ["name_key"], unique = true)],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    /** Lower-cased [name], used only for the uniqueness constraint. */
    @ColumnInfo(name = "name_key")
    val nameKey: String,

    @ColumnInfo(name = "icon")
    val icon: String = FolderIcon.FOLDER.name,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0,
)

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    name = name,
    icon = FolderIcon.fromNameOrDefault(icon),
)

/** Builds a row from a raw user-typed name, applying trimming and the key. */
fun newFolderEntity(
    rawName: String,
    icon: FolderIcon,
    createdAt: Long,
): FolderEntity {
    val clean = Folder.normaliseName(rawName)
    return FolderEntity(
        name = clean,
        nameKey = clean.lowercase(),
        icon = icon.name,
        createdAt = createdAt,
    )
}
