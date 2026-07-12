package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val theme: String = "Dark",
    val decoder: String = "Hardware",
    val playerEngine: String = "ExoPlayer", // "ExoPlayer", "IjkPlayer", "MpvPlayer"
    val remotePushPort: Int = 8080,
    val customBackgroundUrl: String = "",
    val childLockPin: String = "",
    val childLockEnabled: Boolean = false,
    val language: String = "zh",
    val voiceControlEnabled: Boolean = true,
    val cacheLimitMb: Int = 512,
    val autoCacheEnabled: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val autoFullscreenEnabled: Boolean = false,
    val minimalistModeEnabled: Boolean = false,
    val preferredAudioLanguage: String = "Auto",
    val preferredAudioFormat: String = "Auto",
    val autoSelectBestAudio: Boolean = true
)
