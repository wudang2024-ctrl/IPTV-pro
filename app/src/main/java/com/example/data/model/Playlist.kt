package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val content: String = "",
    val isLocked: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
