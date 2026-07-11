package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IPTVViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = IPTVRepository(application)

    // UI States
    val playlists = repository.playlistDao.getAllPlaylistsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId = _selectedPlaylistId.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String>("全部")
    val selectedCategory = _selectedCategory.asStateFlow()

    // Combined channels based on selected playlist and category
    val currentChannels = combine(
        _selectedPlaylistId,
        _selectedCategory,
        repository.channelDao.getAllChannelsFlow()
    ) { playlistId, category, allChannels ->
        if (playlistId == null) {
            allChannels.filter { it.playlistId == 1L } // Default to preset list
        } else {
            val filtered = allChannels.filter { it.playlistId == playlistId }
            if (category == "全部") filtered else filtered.filter { it.category == category }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All categories in selected playlist
    val categories = combine(_selectedPlaylistId, repository.channelDao.getAllChannelsFlow()) { playlistId, allChannels ->
        val id = playlistId ?: 1L
        val list = allChannels.filter { it.playlistId == id }.map { it.category }.distinct()
        listOf("全部") + list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("全部"))

    val favorites = repository.channelDao.getFavoriteChannelsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = repository.playbackHistoryDao.getHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings = repository.appSettingsDao.getSettingsFlow()
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _activeChannel = MutableStateFlow<Channel?>(null)
    val activeChannel = _activeChannel.asStateFlow()

    // Multi-screen grid channels (supports up to 4 screen simultaneous watching!)
    private val _multiScreenChannels = MutableStateFlow<List<Channel>>(emptyList())
    val multiScreenChannels = _multiScreenChannels.asStateFlow()

    private val _activeEpg = MutableStateFlow<List<EPGProgram>>(emptyList())
    val activeEpg = _activeEpg.asStateFlow()

    private val _pushServerAddress = MutableStateFlow("未启动")
    val pushServerAddress = _pushServerAddress.asStateFlow()

    private val _cacheSize = MutableStateFlow("计算中...")
    val cacheSize = _cacheSize.asStateFlow()

    // Voice assistant / smart control feedback message
    private val _voiceFeedback = MutableStateFlow<String?>(null)
    val voiceFeedback = _voiceFeedback.asStateFlow()

    // PIN unlock success tracker
    private val _isParentalUnlocked = MutableStateFlow(false)
    val isParentalUnlocked = _isParentalUnlocked.asStateFlow()

    init {
        viewModelScope.launch {
            // Load preset M3U on startup if empty
            repository.loadPresetChannelsIfEmpty()
            
            // Set initial selected playlist (preset list is 1L)
            _selectedPlaylistId.value = 1L
            
            // Pre-select first channel
            val preset = repository.channelDao.getChannelsByPlaylist(1L)
            if (preset.isNotEmpty()) {
                selectChannel(preset[0])
            }

            // Start remote push server
            val address = repository.startPushServer(
                onPlaylistPushed = { name, url, content ->
                    viewModelScope.launch {
                        val playlistId = if (url.isNotEmpty()) {
                            repository.importM3uFromUrl(name, url).getOrNull()
                        } else if (content.isNotEmpty()) {
                            repository.importM3uFromContent(name, content)
                        } else {
                            null
                        }
                        if (playlistId != null) {
                            selectPlaylist(playlistId)
                        }
                    }
                },
                onDirectPlayPushed = { url ->
                    viewModelScope.launch {
                        val directChannel = Channel(
                            id = 9999L,
                            playlistId = 0L,
                            name = "远程投屏直播流",
                            logoUrl = "",
                            streamUrl = url,
                            category = "远程投屏"
                        )
                        selectChannel(directChannel)
                    }
                }
            )
            _pushServerAddress.value = address
            updateCacheSize()
        }
    }

    fun selectPlaylist(playlistId: Long) {
        _selectedPlaylistId.value = playlistId
        _selectedCategory.value = "全部"
        viewModelScope.launch {
            val channels = repository.channelDao.getChannelsByPlaylist(playlistId)
            if (channels.isNotEmpty()) {
                selectChannel(channels[0])
            }
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectChannel(channel: Channel) {
        _activeChannel.value = channel
        
        // Load EPG for this channel
        viewModelScope.launch {
            repository.getEpgProgramsForChannel(channel.name).collect { programs ->
                _activeEpg.value = programs
            }
        }

        // Add to history (breakpoint record simulation)
        viewModelScope.launch {
            val record = PlaybackHistory(
                channelId = channel.id,
                name = channel.name,
                logoUrl = channel.logoUrl,
                streamUrl = channel.streamUrl,
                playbackSpeed = settings.value.playbackSpeed
            )
            repository.playbackHistoryDao.insertHistory(record)
        }
    }

    // Toggle Favorite
    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repository.channelDao.updateFavorite(channel.id, !channel.isFavorite)
            // Update selected if it is active
            if (_activeChannel.value?.id == channel.id) {
                _activeChannel.value = _activeChannel.value?.copy(isFavorite = !channel.isFavorite)
            }
        }
    }

    // Settings actions
    fun updateTheme(newTheme: String) {
        viewModelScope.launch {
            val s = settings.value.copy(theme = newTheme)
            repository.appSettingsDao.saveSettings(s)
        }
    }

    fun updateDecoder(newDecoder: String) {
        viewModelScope.launch {
            val s = settings.value.copy(decoder = newDecoder)
            repository.appSettingsDao.saveSettings(s)
        }
    }

    fun updatePlayerEngine(newEngine: String) {
        viewModelScope.launch {
            val s = settings.value.copy(playerEngine = newEngine)
            repository.appSettingsDao.saveSettings(s)
        }
    }

    fun updateCustomBackground(url: String) {
        viewModelScope.launch {
            val s = settings.value.copy(customBackgroundUrl = url)
            repository.appSettingsDao.saveSettings(s)
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            val s = settings.value.copy(playbackSpeed = speed)
            repository.appSettingsDao.saveSettings(s)
        }
    }

    fun updateLanguage(lang: String) {
        viewModelScope.launch {
            val s = settings.value.copy(language = lang)
            repository.appSettingsDao.saveSettings(s)
        }
    }

    fun updateAutoFullscreen(enabled: Boolean) {
        viewModelScope.launch {
            val s = settings.value.copy(autoFullscreenEnabled = enabled)
            repository.appSettingsDao.saveSettings(s)
        }
    }

    fun updateMinimalistMode(enabled: Boolean) {
        viewModelScope.launch {
            val s = settings.value.copy(minimalistModeEnabled = enabled)
            repository.appSettingsDao.saveSettings(s)
        }
    }

    // Multi-screen / Grid features
    fun addToMultiScreen(channel: Channel) {
        val current = _multiScreenChannels.value.toMutableList()
        if (current.size < 4 && !current.any { it.id == channel.id }) {
            current.add(channel)
            _multiScreenChannels.value = current
        }
    }

    fun removeFromMultiScreen(channel: Channel) {
        val current = _multiScreenChannels.value.toMutableList()
        current.removeAll { it.id == channel.id }
        _multiScreenChannels.value = current
    }

    fun clearMultiScreen() {
        _multiScreenChannels.value = emptyList()
    }

    // Child Lock / Parent Regulation
    fun setChildLockPin(pin: String) {
        viewModelScope.launch {
            val s = settings.value.copy(childLockPin = pin, childLockEnabled = pin.isNotEmpty())
            repository.appSettingsDao.saveSettings(s)
            _isParentalUnlocked.value = pin.isEmpty()
        }
    }

    fun verifyChildLockPin(pin: String): Boolean {
        val actual = settings.value.childLockPin
        val isOk = pin == actual
        if (isOk) {
            _isParentalUnlocked.value = true
        }
        return isOk
    }

    fun lockParentalControls() {
        _isParentalUnlocked.value = false
    }

    fun togglePlaylistLock(playlist: Playlist, lock: Boolean) {
        viewModelScope.launch {
            val updated = playlist.copy(isLocked = lock)
            repository.playlistDao.insertPlaylist(updated)
        }
    }

    // Cache actions
    fun updateCacheSize() {
        viewModelScope.launch {
            _cacheSize.value = repository.getCacheSizeString()
        }
    }

    fun clearSystemCache() {
        viewModelScope.launch {
            repository.clearCache()
            updateCacheSize()
        }
    }

    // Simulated Cloud sync functions
    fun backupDataToCloud(account: String, onCompleted: (String) -> Unit) {
        viewModelScope.launch {
            repository.syncBackupToCloud(account).onSuccess {
                onCompleted(it)
            }.onFailure {
                onCompleted(it.message ?: "备份失败")
            }
        }
    }

    fun restoreDataFromCloud(account: String, onCompleted: (String) -> Unit) {
        viewModelScope.launch {
            repository.syncRestoreFromCloud(account).onSuccess {
                onCompleted(it)
            }.onFailure {
                onCompleted(it.message ?: "恢复失败")
            }
        }
    }

    // Import playlist locally
    fun importLocalM3u(name: String, content: String) {
        viewModelScope.launch {
            val playlistId = repository.importM3uFromContent(name, content)
            selectPlaylist(playlistId)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.playlistDao.deletePlaylist(playlist)
            repository.channelDao.deleteChannelsByPlaylist(playlist.id)
            if (_selectedPlaylistId.value == playlist.id) {
                _selectedPlaylistId.value = 1L // Fallback to preset
            }
        }
    }

    // Custom Web/Internet URL import
    fun importPlaylistFromUrl(name: String, url: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            repository.importM3uFromUrl(name, url)
                .onSuccess { playlistId ->
                    selectPlaylist(playlistId)
                    onDone(true, "导入成功")
                }
                .onFailure {
                    onDone(false, it.message ?: "网络导入失败")
                }
        }
    }

    // Voice command processing
    fun handleVoiceCommand(rawText: String) {
        val query = rawText.trim()
        if (query.isEmpty()) return

        _voiceFeedback.value = "正在识别口令: \"$query\"..."

        viewModelScope.launch {
            // Find a channel that matches
            val allChannels = repository.channelDao.getAllChannelsFlow().firstOrNull() ?: emptyList()
            val match = allChannels.find { it.name.contains(query, ignoreCase = true) }
            
            if (match != null) {
                selectChannel(match)
                _voiceFeedback.value = "✓ 已为您切换到频道: ${match.name}"
            } else if (query.contains("播放") || query.contains("切换") || query.contains("看")) {
                val targetName = query.replace("播放", "").replace("切换到", "").replace("我想看", "").trim()
                val matchPart = allChannels.find { it.name.contains(targetName, ignoreCase = true) }
                if (matchPart != null) {
                    selectChannel(matchPart)
                    _voiceFeedback.value = "✓ 智能识别: 切换到 ${matchPart.name}"
                } else {
                    _voiceFeedback.value = "❌ 未找到含有 \"$targetName\" 的频道"
                }
            } else if (query.contains("下一个")) {
                val channels = currentChannels.value
                val active = _activeChannel.value
                if (active != null && channels.isNotEmpty()) {
                    val idx = channels.indexOfFirst { it.id == active.id }
                    if (idx != -1 && idx < channels.size - 1) {
                        selectChannel(channels[idx + 1])
                        _voiceFeedback.value = "✓ 播放下一个: ${channels[idx + 1].name}"
                    } else if (channels.isNotEmpty()) {
                        selectChannel(channels[0])
                        _voiceFeedback.value = "✓ 播放第一个: ${channels[0].name}"
                    }
                }
            } else if (query.contains("上一个")) {
                val channels = currentChannels.value
                val active = _activeChannel.value
                if (active != null && channels.isNotEmpty()) {
                    val idx = channels.indexOfFirst { it.id == active.id }
                    if (idx > 0) {
                        selectChannel(channels[idx - 1])
                        _voiceFeedback.value = "✓ 播放上一个: ${channels[idx - 1].name}"
                    }
                }
            } else if (query.contains("极客") || query.contains("赛博")) {
                updateTheme("Cyberpunk")
                _voiceFeedback.value = "✓ 已为您切换至：赛博朋克 皮肤"
            } else if (query.contains("深色") || query.contains("黑色")) {
                updateTheme("Dark")
                _voiceFeedback.value = "✓ 已为您切换至：深空黑 皮肤"
            } else {
                _voiceFeedback.value = "❓ 无法识别的指令，您可以试着说 \"播放 CCTV-1\""
            }
        }
    }

    fun clearVoiceFeedback() {
        _voiceFeedback.value = null
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopPushServer()
    }
}
