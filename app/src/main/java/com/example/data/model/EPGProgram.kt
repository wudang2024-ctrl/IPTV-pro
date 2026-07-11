package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_programs")
data class EPGProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelName: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String = ""
)
