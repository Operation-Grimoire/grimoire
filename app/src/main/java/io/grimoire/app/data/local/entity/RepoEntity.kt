package io.grimoire.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "repos",
    indices = [Index(value = ["indexUrl"], unique = true)],
)
data class RepoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val indexUrl: String,
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
)
