package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.parser.EpgParser
import com.example.data.parser.M3uParser
import com.example.data.server.RemotePushServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class IPTVRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    val playlistDao = database.playlistDao()
    val channelDao = database.channelDao()
    val epgDao = database.epgDao()
    val playbackHistoryDao = database.playbackHistoryDao()
    val appSettingsDao = database.appSettingsDao()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var remotePushServer: RemotePushServer? = null

    // Preset channels for instant playback on first launch
    suspend fun loadPresetChannelsIfEmpty() {
        val playlists = playlistDao.getAllPlaylists()
        if (playlists.isEmpty()) {
            val playlistId = playlistDao.insertPlaylist(
                Playlist(
                    name = "官方内置超清频道",
                    url = "local://preset",
                    content = "preset"
                )
            )

            val presetChannels = listOf(
                Channel(
                    playlistId = playlistId,
                    name = "CCTV-1 综合频道",
                    logoUrl = "https://tvg.m3u.cl/logos/cctv1.png",
                    streamUrl = "https://sf3-cdn-tos.douyinstatic.com/obj/media-fe/xgplayer_doc_video/hls/xgplayer-demo.m3u8",
                    category = "央视频道",
                    isFavorite = true
                ),
                Channel(
                    playlistId = playlistId,
                    name = "CCTV-5 体育超清",
                    logoUrl = "https://tvg.m3u.cl/logos/cctv5.png",
                    streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                    category = "央视频道",
                    isFavorite = false
                ),
                Channel(
                    playlistId = playlistId,
                    name = "CCTV-6 电影频道",
                    logoUrl = "https://tvg.m3u.cl/logos/cctv6.png",
                    streamUrl = "https://playertest.longtailvideo.com/adaptive/bipbop/bipbopall.m3u8",
                    category = "央视频道",
                    isFavorite = true
                ),
                Channel(
                    playlistId = playlistId,
                    name = "HBO 经典影院",
                    logoUrl = "https://tvg.m3u.cl/logos/hbo.png",
                    streamUrl = "https://multiplatform-f.akamaihd.net/i/multi/will/apple/master_1660.m3u8",
                    category = "影视频道",
                    isFavorite = false
                ),
                Channel(
                    playlistId = playlistId,
                    name = "Discovery 探索频道",
                    logoUrl = "https://tvg.m3u.cl/logos/discovery.png",
                    streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                    category = "纪录片频道",
                    isFavorite = true
                ),
                Channel(
                    playlistId = playlistId,
                    name = "湖南卫视 HD",
                    logoUrl = "https://tvg.m3u.cl/logos/hunantv.png",
                    streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    category = "卫视频道",
                    isFavorite = false
                ),
                Channel(
                    playlistId = playlistId,
                    name = "东方卫视 HD",
                    logoUrl = "https://tvg.m3u.cl/logos/dongfang.png",
                    streamUrl = "https://res.cloudinary.com/dannykeane/video/upload/sp_full_hd/q_80/v1/sample-videos/sintel.m3u8",
                    category = "卫视频道",
                    isFavorite = false
                ),
                Channel(
                    playlistId = playlistId,
                    name = "上海经典高清 (PHP测试)",
                    logoUrl = "https://tvg.m3u.cl/logos/dongfang.png",
                    streamUrl = "http://192.168.31.107:5080/shanghai.php?id=mdy",
                    category = "本地测试",
                    isFavorite = true
                ),
                Channel(
                    playlistId = playlistId,
                    name = "经典动漫大放送",
                    logoUrl = "https://tvg.m3u.cl/logos/anime.png",
                    streamUrl = "https://test-streams.mux.dev/pts_live/pts_live.m3u8",
                    category = "少儿频道",
                    isFavorite = false
                )
            )

            channelDao.insertChannels(presetChannels)

            // Insert default app settings
            val currentSettings = appSettingsDao.getSettings()
            if (currentSettings == null) {
                appSettingsDao.saveSettings(AppSettings())
            }
        }
    }

    // Import playlist from Web URL
    suspend fun importM3uFromUrl(name: String, url: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                val content = response.body?.string() ?: return@withContext Result.failure(Exception("Response body is empty"))
                
                val playlistId = playlistDao.insertPlaylist(
                    Playlist(name = name, url = url, content = content)
                )

                val channels = M3uParser.parse(content, playlistId)
                if (channels.isNotEmpty()) {
                    channelDao.insertChannels(channels)
                    Result.success(playlistId)
                } else {
                    Result.failure(Exception("无法解析任何频道，请检查M3U文件格式"))
                }
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error fetching M3U: ${e.message}")
            Result.failure(e)
        }
    }

    // Import playlist directly from raw text content
    suspend fun importM3uFromContent(name: String, content: String): Long = withContext(Dispatchers.IO) {
        val playlistId = playlistDao.insertPlaylist(
            Playlist(name = name, url = "local://raw_${System.currentTimeMillis()}", content = content)
        )
        val channels = M3uParser.parse(content, playlistId)
        if (channels.isNotEmpty()) {
            channelDao.insertChannels(channels)
        }
        playlistId
    }

    // EPG auto loading or auto generation
    suspend fun getEpgProgramsForChannel(channelName: String): Flow<List<EPGProgram>> {
        // Trigger simulated generation if empty
        withContext(Dispatchers.IO) {
            val simulated = EpgParser.generateSimulatedEpg(channelName)
            epgDao.insertPrograms(simulated)
        }
        return epgDao.getProgramsForChannelFlow(channelName, System.currentTimeMillis() - 4 * 3600 * 1000) // Keep past 4 hours
    }

    // Remote push server lifecycle
    suspend fun startPushServer(
        onPlaylistPushed: (name: String, url: String, content: String) -> Unit,
        onDirectPlayPushed: (url: String) -> Unit
    ): String {
        val settings = appSettingsDao.getSettings() ?: AppSettings()
        remotePushServer?.stop()
        remotePushServer = RemotePushServer(
            context = context,
            port = settings.remotePushPort,
            onPlaylistPushed = onPlaylistPushed,
            onDirectPlayPushed = onDirectPlayPushed
        )
        remotePushServer?.start()
        return "http://${remotePushServer?.getDeviceIpAddress() ?: "127.0.0.1"}:${settings.remotePushPort}"
    }

    fun stopPushServer() {
        remotePushServer?.stop()
        remotePushServer = null
    }

    fun getPushServerAddress(): String {
        val server = remotePushServer ?: return "未启动"
        return "http://${server.getDeviceIpAddress()}:8080"
    }

    // Background cache management & size computation
    suspend fun getCacheSizeString(): String = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val mediaCacheDir = File(cacheDir, "media_cache")
        var totalBytes = getFolderSize(cacheDir)
        if (mediaCacheDir.exists()) {
            totalBytes += getFolderSize(mediaCacheDir)
        }

        val mb = totalBytes / (1024.0 * 1024.0)
        if (mb < 1.0) {
            String.format("%.2f KB", totalBytes / 1024.0)
        } else {
            String.format("%.2f MB", mb)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        deleteDir(context.cacheDir)
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            val files = file.listFiles() ?: return 0
            for (f in files) {
                size += getFolderSize(f)
            }
        } else {
            size = file.length()
        }
        return size
    }

    private fun deleteDir(file: File): Boolean {
        if (file.isDirectory) {
            val children = file.list() ?: return false
            for (i in children.indices) {
                val success = deleteDir(File(file, children[i]))
                if (!success) {
                    return false
                }
            }
        }
        return file.delete()
    }

    // Cloud Sync simulation
    suspend fun syncBackupToCloud(account: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Simulate a cloud request delay
            Thread.sleep(1500)
            val playlists = playlistDao.getAllPlaylists()
            val history = playbackHistoryDao.getHistoryFlow().firstOrNull() ?: emptyList()
            val settings = appSettingsDao.getSettings() ?: AppSettings()

            // In real app we could post to standard JSON server.
            // Here, we save to a simulated "cloud sync storage" in shared preferences
            val sharedPrefs = context.getSharedPreferences("cloud_sync_sim", Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()
            editor.putString("${account}_sync_time", System.currentTimeMillis().toString())
            editor.putInt("${account}_playlists_count", playlists.size)
            editor.putInt("${account}_channels_count", database.channelDao().getAllChannelsFlow().firstOrNull()?.size ?: 0)
            editor.putString("${account}_theme", settings.theme)
            editor.apply()

            Result.success("备份成功！已向云端同步了 ${playlists.size} 个播放列表及系统设置。")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncRestoreFromCloud(account: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Thread.sleep(1500)
            val sharedPrefs = context.getSharedPreferences("cloud_sync_sim", Context.MODE_PRIVATE)
            val syncTime = sharedPrefs.getString("${account}_sync_time", null)
            if (syncTime == null) {
                return@withContext Result.failure(Exception("找不到该账号的云端备份数据，请检查拼写或先在另一台设备上备份！"))
            }

            // Restore basic settings
            val savedTheme = sharedPrefs.getString("${account}_theme", "Dark") ?: "Dark"
            val currentSettings = appSettingsDao.getSettings() ?: AppSettings()
            appSettingsDao.saveSettings(currentSettings.copy(theme = savedTheme))

            Result.success("同步完成！成功从云端恢复了播放器自定义设置。")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
