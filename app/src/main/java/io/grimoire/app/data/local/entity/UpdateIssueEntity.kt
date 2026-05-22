package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Severity of a library-refresh problem, stored as the ordinal in [UpdateIssueEntity.severity]. */
enum class UpdateIssueSeverity {
    /** The novel still updated, but some data was kept from a previous refresh. */
    WARNING,

    /** The refresh failed outright; the novel was left untouched. */
    ERROR,
}

/**
 * The current refresh problem for a novel. At most one row exists per novel — it is
 * replaced on each new problem and deleted once the novel refreshes cleanly again,
 * so the warnings page always reflects what is broken right now.
 */
@Entity(
    tableName = "update_issues",
    foreignKeys = [ForeignKey(
        entity = NovelEntity::class,
        parentColumns = ["id"],
        childColumns = ["novelId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["novelId"], unique = true)],
)
data class UpdateIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelId: Long,
    val sourcePackage: String,
    val novelUrl: String,
    val novelTitle: String,
    val severity: Int,
    val message: String,
    val occurredAt: Long,
)
