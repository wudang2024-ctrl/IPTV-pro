package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY lastUpdated DESC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY lastUpdated DESC")
    suspend fun getAllPlaylists(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels")
    fun getAllChannelsFlow(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    fun getChannelsByPlaylistFlow(playlistId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelsByPlaylist(playlistId: Long): List<Channel>

    @Query("SELECT DISTINCT category FROM channels WHERE playlistId = :playlistId")
    fun getCategoriesForPlaylistFlow(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND category = :category")
    fun getChannelsByCategoryFlow(playlistId: Long, category: String): Flow<List<Channel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Long)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun updateFavorite(channelId: Long, isFavorite: Boolean)

    @Query("SELECT * FROM channels WHERE isFavorite = 1")
    fun getFavoriteChannelsFlow(): Flow<List<Channel>>
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_programs WHERE channelName = :channelName AND endTime > :now ORDER BY startTime ASC")
    fun getProgramsForChannelFlow(channelName: String, now: Long): Flow<List<EPGProgram>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EPGProgram>)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAllPrograms()
}

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC")
    fun getHistoryFlow(): Flow<List<PlaybackHistory>>

    @Query("SELECT * FROM playback_history WHERE streamUrl = :streamUrl LIMIT 1")
    suspend fun getHistoryByStreamUrl(streamUrl: String): PlaybackHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistory)

    @Query("DELETE FROM playback_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}
