package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.data.model.Channel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    channel: Channel?,
    playbackSpeed: Float = 1.0f,
    decoderMode: String = "Hardware", // "Hardware" or "Software" or "Auto"
    playerEngine: String = "ExoPlayer", // "ExoPlayer", "IjkPlayer", "MpvPlayer"
    minimalistMode: Boolean = false,
    onChannelSwitched: (Boolean) -> Unit = {}, // true for Next, false for Prev
    onFullscreenToggle: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    onConfirmClick: (() -> Unit)? = null,
    preferredAudioLanguage: String = "Auto",
    preferredAudioFormat: String = "Auto",
    autoSelectBestAudio: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPreferredAudioLanguage by rememberUpdatedState(preferredAudioLanguage)
    val currentPreferredAudioFormat by rememberUpdatedState(preferredAudioFormat)
    val currentAutoSelectBestAudio by rememberUpdatedState(autoSelectBestAudio)
    var isPlaying by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableStateOf(playbackSpeed) }

    // Volume level states (Audio processing / management)
    var volume by remember { mutableStateOf(1.0f) }
    var lastVolumeBeforeMute by remember { mutableStateOf(0.5f) }

    // Multi-channel auto rotation (Carousel) mode state
    var isCarouselEnabled by remember { mutableStateOf(false) }
    var carouselIntervalSeconds by remember { mutableStateOf(10) } // Seconds per channel

    // Audio and Video decoder configuration options
    var passthroughEnabled by remember { mutableStateOf(false) }
    var avsPriorityEnabled by remember { mutableStateOf(true) }

    val channelModeAudioProcessor = remember { ChannelModeAudioProcessor() }
    var selectedSoundMode by remember { mutableStateOf(ChannelModeAudioProcessor.Mode.STEREO) }

    // Sync selectedSoundMode to the processor
    LaunchedEffect(selectedSoundMode) {
        channelModeAudioProcessor.mode = selectedSoundMode
    }

    var showMediaInfo by remember { mutableStateOf(false) }
    var activeVideoFormat by remember { mutableStateOf<androidx.media3.common.Format?>(null) }
    var activeAudioFormat by remember { mutableStateOf<androidx.media3.common.Format?>(null) }

    // ExoPlayer Instance
    val exoPlayer = remember(decoderMode, playerEngine, passthroughEnabled, avsPriorityEnabled) {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                val audioCapabilities = if (passthroughEnabled) {
                    // Force support for DTS, AC3, AC4, PCM, TrueHD, DRA for high fidelity bitstream passthrough
                    androidx.media3.exoplayer.audio.AudioCapabilities(
                        intArrayOf(
                            android.media.AudioFormat.ENCODING_PCM_16BIT,
                            android.media.AudioFormat.ENCODING_PCM_FLOAT,
                            android.media.AudioFormat.ENCODING_AC3,
                            android.media.AudioFormat.ENCODING_E_AC3,
                            android.media.AudioFormat.ENCODING_E_AC3_JOC,
                            android.media.AudioFormat.ENCODING_AC4,
                            android.media.AudioFormat.ENCODING_DTS,
                            android.media.AudioFormat.ENCODING_DTS_HD,
                            android.media.AudioFormat.ENCODING_DOLBY_TRUEHD,
                            28 // AudioFormat.ENCODING_DRA (DRA codec constant value is 28)
                        ),
                        8 // Support up to 7.1 channel output (8 channels)
                    )
                } else {
                    // Standard device auto-detected capabilities with PCM / AC3 fallback to prevent silent playbacks
                    val devCaps = androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(context)
                    if (avsPriorityEnabled) {
                        // Blend standard capabilities with forced PCM, AC3, and DRA to bypass problematic HDMI EDID reports
                        androidx.media3.exoplayer.audio.AudioCapabilities(
                            intArrayOf(
                                android.media.AudioFormat.ENCODING_PCM_16BIT,
                                android.media.AudioFormat.ENCODING_PCM_FLOAT,
                                android.media.AudioFormat.ENCODING_AC3,
                                android.media.AudioFormat.ENCODING_E_AC3,
                                28 // DRA audio format
                            ),
                            2
                        )
                    } else {
                        devCaps
                    }
                }

                val audioSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(audioCapabilities)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(channelModeAudioProcessor))
                    .build()
                return audioSink
            }
        }.apply {
            setExtensionRendererMode(
                when (decoderMode) {
                    "Software" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    "Auto" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON // Allow software audio decoder fallback (Dolby, AC3, DTS, etc.)
                }
            )
            setEnableDecoderFallback(true) // Fallback to alternative decoders if hardware decoders fail
        }
        
        // Setup browser user-agent to bypass protections on PHP scripts (like 4gtv_api.php)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        
        // Optimize TS & ADTS extractor flags to parse MPEG-TS / LATM AAC / MPEG Audio layer 1/2 channels properly
        // Using raw integer flags to bypass version-dependent internal package import variances:
        // - FLAG_ALLOW_NON_KEYFRAME_ONSETS = 1
        // - FLAG_DETECT_ACCESS_UNITS = 8
        // - FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS = 64
        // - ADTS FLAG_ENABLE_CONSTANT_BITRATE_SEEKING = 1
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setTsExtractorFlags(1 or 8 or 64)
            setAdtsExtractorFlags(1)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory, extractorsFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // Configure a robust LoadControl to handle bursty network packets of UDP multicast streams
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBufferMs: 15 seconds of minimum buffer depth to ensure continuous audio output
                50000, // maxBufferMs: 50 seconds max buffer size
                1500,  // bufferForPlaybackMs: 1.5 seconds before starting playback
                3000   // bufferForPlaybackAfterRebufferMs: 3 seconds before resuming after rebuffering
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true) // Enable automatic audio focus and routing
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE // Perfect for loop / live stream simulation
                this.volume = volume // Apply the state volume
            }
    }

    // Keep volume state synced with player
    LaunchedEffect(volume) {
        exoPlayer.volume = volume
    }

    // Carousel Timer Loop
    LaunchedEffect(isCarouselEnabled, carouselIntervalSeconds) {
        if (isCarouselEnabled) {
            while (true) {
                delay(carouselIntervalSeconds * 1000L)
                onChannelSwitched(true) // Switch to next channel automatically
            }
        }
    }

    // Auto-hide controls timer (Minimalist mode goes into full minimalist view quickly!)
    LaunchedEffect(showControls, minimalistMode) {
        if (showControls) {
            delay(if (minimalistMode) 2000L else 5000L)
            showControls = false
        }
    }

    // Update Speed
    LaunchedEffect(currentSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed)
    }

    // Load Stream when channel changes
    LaunchedEffect(channel) {
        if (channel != null) {
            errorMessage = null
            isPlaying = true
            try {
                val streamUrl = channel.streamUrl
                val mediaItemBuilder = MediaItem.Builder().setUri(streamUrl)
                val uriLower = streamUrl.lowercase()
                
                // Explicitly set appropriate MIME type to enable proper player engine/source mapping for PHP scripts
                when {
                    uriLower.contains(".m3u8") || uriLower.contains("m3u8") -> {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                    uriLower.contains(".mpd") || uriLower.contains("mpd") -> {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                    }
                    uriLower.contains("avs2") || uriLower.contains(".avs2") -> {
                        mediaItemBuilder.setMimeType("video/avs2")
                    }
                    uriLower.contains("avs+") || uriLower.contains(".avs") -> {
                        mediaItemBuilder.setMimeType("video/avs-video")
                    }
                    uriLower.contains("shanghai.php") || uriLower.contains("id=mdy") || uriLower.contains(".ts") || uriLower.contains("mpegts") || uriLower.contains("/udp/") || uriLower.contains("/rtp/") || uriLower.contains(":7088") || uriLower.contains(":4022") || uriLower.contains("/rtsp") -> {
                        // "shanghai.php?id=mdy" and "/udp/..." multicast streams serve IPTV multicast MPEG-TS format
                        mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
                    }
                    uriLower.contains(".php") || uriLower.contains("php") || uriLower.contains("api") -> {
                        // Default to HLS for other PHP streaming endpoints
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                
                val mediaItem = mediaItemBuilder.build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (e: Exception) {
                errorMessage = "无法加载直播流: ${e.message}"
            }
        } else {
            exoPlayer.stop()
        }
    }

    // Track selection states (Video Track 1 & Audio Track)
    var videoTracks by remember { mutableStateOf(listOf("视频轨 1 (1080P)", "视频轨 2 (720P)")) }
    var selectedVideoTrackIndex by remember { mutableStateOf(0) }
    var audioTracks by remember { mutableStateOf(listOf("默认音轨 (PCM)", "DRA 音轨 (国标)", "杜比音轨 (AC-3)")) }
    var selectedAudioTrackIndex by remember { mutableStateOf(0) }

    fun selectVideoTrack(index: Int) {
        selectedVideoTrackIndex = index
        try {
            val tracks = exoPlayer.currentTracks
            var trackCounter = 0
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        if (trackCounter == index) {
                            val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                                .setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                                )
                                .build()
                            exoPlayer.trackSelectionParameters = newParams
                            return
                        }
                        trackCounter++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun selectAudioTrack(index: Int) {
        selectedAudioTrackIndex = index
        try {
            val tracks = exoPlayer.currentTracks
            var trackCounter = 0
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        if (trackCounter == index) {
                            val newParams = exoPlayer.trackSelectionParameters.buildUpon()
                                .setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                                )
                                .build()
                            exoPlayer.trackSelectionParameters = newParams
                            return
                        }
                        trackCounter++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun findUserPreferredAudioTrack(
        tracks: androidx.media3.common.Tracks,
        prefFormat: String,
        prefTrack: String
    ): Int {
        var currentAudioIndex = 0
        val audioTrackIndicesAndFormats = mutableListOf<Pair<Int, androidx.media3.common.Format>>()

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    audioTrackIndicesAndFormats.add(currentAudioIndex to format)
                    currentAudioIndex++
                }
            }
        }

        if (audioTrackIndicesAndFormats.isEmpty()) return -1

        // 1. If a specific track is requested (Track 1, Track 2, Track 3)
        if (prefTrack == "Track 1" && audioTrackIndicesAndFormats.size >= 1) {
            return audioTrackIndicesAndFormats[0].first
        }
        if (prefTrack == "Track 2" && audioTrackIndicesAndFormats.size >= 2) {
            return audioTrackIndicesAndFormats[1].first
        }
        if (prefTrack == "Track 3" && audioTrackIndicesAndFormats.size >= 3) {
            return audioTrackIndicesAndFormats[2].first
        }

        // 2. If a specific format is requested
        if (prefFormat != "Auto" && prefFormat.isNotEmpty()) {
            for ((index, format) in audioTrackIndicesAndFormats) {
                val mime = format.sampleMimeType?.lowercase() ?: ""
                val label = format.label?.lowercase() ?: ""
                val matches = when (prefFormat) {
                    "AAC" -> mime.contains("aac") || mime.contains("mp4a") || label.contains("aac")
                    "DRA" -> mime.contains("dra") || label.contains("dra")
                    "AC-3" -> mime.contains("ac3") || mime.contains("ac-3") || label.contains("ac3") || label.contains("ac-3")
                    "AC-3 / E-AC-3" -> mime.contains("ac3") || mime.contains("eac3") || mime.contains("ac-3") || mime.contains("e-ac-3") || label.contains("ac3") || label.contains("eac3")
                    "AAC / MP2" -> mime.contains("aac") || mime.contains("mp4a") || mime.contains("mp2") || mime.contains("mpeg") || label.contains("aac") || label.contains("mp2")
                    else -> false
                }
                if (matches) {
                    return index
                }
            }
        }

        return -1
    }

    // Handle lifecycle and tracks changed listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val newVideoTracks = mutableListOf<String>()
                val newAudioTracks = mutableListOf<String>()
                
                var currentVideoIndex = 0
                var selectedVideoIndex = 0
                var currentAudioIndex = 0
                var selectedAudioIndex = 0

                var activeVideoF: androidx.media3.common.Format? = null
                var activeAudioF: androidx.media3.common.Format? = null
                
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val label = format.label ?: "视频轨 ${newVideoTracks.size + 1} (${format.width}x${format.height})"
                            newVideoTracks.add(label)
                            if (group.isTrackSelected(i)) {
                                selectedVideoIndex = currentVideoIndex
                                activeVideoF = format
                            }
                            currentVideoIndex++
                        }
                    } else if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val language = format.language?.let { " [$it]" } ?: ""
                            val mime = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "UNKNOWN"
                            val channels = when (format.channelCount) {
                                1 -> "单声道"
                                2 -> "立体声"
                                6 -> "5.1环绕声"
                                8 -> "7.1环绕声"
                                else -> "${format.channelCount}声道"
                            }
                            val label = format.label ?: "音频轨 ${newAudioTracks.size + 1} ($mime, $channels)$language"
                            newAudioTracks.add(label)
                            if (group.isTrackSelected(i)) {
                                selectedAudioIndex = currentAudioIndex
                                activeAudioF = format
                            }
                            currentAudioIndex++
                        }
                    }
                }
                
                if (newVideoTracks.isNotEmpty()) {
                    videoTracks = newVideoTracks
                    selectedVideoTrackIndex = selectedVideoIndex
                }
                if (newAudioTracks.isNotEmpty()) {
                    audioTracks = newAudioTracks
                    selectedAudioTrackIndex = selectedAudioIndex
                }

                activeVideoFormat = activeVideoF
                activeAudioFormat = activeAudioF

                // Apply manual selection rules if preference matches
                if (newAudioTracks.isNotEmpty()) {
                    val targetIndex = findUserPreferredAudioTrack(tracks, currentPreferredAudioFormat, currentPreferredAudioLanguage)
                    if (targetIndex != -1 && targetIndex != selectedAudioIndex) {
                        selectAudioTrack(targetIndex)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val tracks = exoPlayer.currentTracks
                    var activeVideoF: androidx.media3.common.Format? = null
                    var activeAudioF: androidx.media3.common.Format? = null
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
                            for (i in 0 until group.length) {
                                if (group.isTrackSelected(i)) {
                                    activeVideoF = group.getTrackFormat(i)
                                    break
                                }
                            }
                        } else if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected) {
                            for (i in 0 until group.length) {
                                if (group.isTrackSelected(i)) {
                                    activeAudioF = group.getTrackFormat(i)
                                    break
                                }
                            }
                        }
                    }
                    activeVideoFormat = activeVideoF
                    activeAudioFormat = activeAudioF
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            // Left key: Rewind or Previous channel
                            onChannelSwitched(false)
                            true
                        }
                        Key.DirectionRight -> {
                            // Right key: Fast Forward or Next channel
                            onChannelSwitched(true)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            if (minimalistMode && onConfirmClick != null) {
                                onConfirmClick()
                            } else {
                                // Center: Pause/Play
                                isPlaying = !isPlaying
                                if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                                showControls = true
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            // Up key: Increase playback speed for fun or show controls
                            showControls = true
                            true
                        }
                        Key.DirectionDown -> {
                            // Down key: Show controls
                            showControls = true
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(minimalistMode, onConfirmClick) {
                detectTapGestures(
                    onTap = {
                        if (minimalistMode && onConfirmClick != null) {
                            onConfirmClick()
                        } else {
                            showControls = !showControls
                        }
                    },
                    onDoubleTap = {
                        if (!minimalistMode) {
                            isPlaying = !isPlaying
                            if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                            showControls = true
                        }
                    }
                )
            }
    ) {
        // Player View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // We render custom Compose overlays!
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Error message layer
        errorMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "加载错误",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Custom controller overlay
        if (showControls && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Top Indicator (Channel Name & Info)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = channel?.name ?: "未在播放",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!minimalistMode) {
                            Text(
                                text = if (channel != null) {
                                    "分类: ${channel.category} | 引擎: $playerEngine | 解码: ${
                                        when (decoderMode) {
                                            "Hardware" -> "硬解"
                                            "Software" -> "软解"
                                            else -> "自动"
                                        }
                                    }"
                                } else "",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Media Info Toggle Button
                        IconButton(
                            onClick = { showMediaInfo = !showMediaInfo },
                            modifier = Modifier.testTag("player_media_info_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "媒体信息",
                                tint = if (showMediaInfo) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        if (onFullscreenToggle != null) {
                            IconButton(
                                onClick = onFullscreenToggle,
                                modifier = Modifier.testTag("player_fullscreen_btn")
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "切换全屏",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Center Action Play/Pause
                IconButton(
                    onClick = {
                        isPlaying = !isPlaying
                        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .testTag("player_play_pause_button")
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Bottom bar container with advanced options and controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Main Controls row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onChannelSwitched(false) }) {
                                Icon(Icons.Default.SkipPrevious, "上一个", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { onChannelSwitched(true) }) {
                                Icon(Icons.Default.SkipNext, "下一个", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Carousel Mode (多画面自动轮播) Button
                            IconButton(
                                onClick = { isCarouselEnabled = !isCarouselEnabled },
                                modifier = Modifier.testTag("player_carousel_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Autorenew,
                                    contentDescription = "多画面轮播",
                                    tint = if (isCarouselEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            if (isCarouselEnabled) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("轮播:", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    listOf(10, 30, 60).forEach { sec ->
                                        TextButton(
                                            onClick = { carouselIntervalSeconds = sec },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = if (carouselIntervalSeconds == sec) MaterialTheme.colorScheme.primary else Color.LightGray
                                            ),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            modifier = Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 24.dp)
                                        ) {
                                            Text(
                                                "${sec}秒",
                                                fontSize = 10.sp,
                                                fontWeight = if (carouselIntervalSeconds == sec) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            // Visual Volume Adjuster (Audio Processing overlay)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                val isMuted = volume == 0f
                                IconButton(onClick = {
                                    if (isMuted) {
                                        volume = if (lastVolumeBeforeMute > 0f) lastVolumeBeforeMute else 0.5f
                                    } else {
                                        lastVolumeBeforeMute = volume
                                        volume = 0f
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff 
                                                      else if (volume < 0.3f) Icons.Default.VolumeMute
                                                      else if (volume < 0.7f) Icons.Default.VolumeDown 
                                                      else Icons.Default.VolumeUp,
                                        contentDescription = "音量调节",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Slider(
                                    value = volume,
                                    onValueChange = { volume = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.width(80.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                                    )
                                )
                                Text(
                                    text = "${(volume * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }

                        // Speed selectors
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("倍速播放: ", color = Color.White, fontSize = 12.sp)
                            listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                TextButton(
                                    onClick = { currentSpeed = speed },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (currentSpeed == speed) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                ) {
                                    Text(
                                        "${speed}x",
                                        fontSize = 13.sp,
                                        fontWeight = if (currentSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Media Info HUD (Stats for Nerds)
        if (showMediaInfo && errorMessage == null) {
            val videoCodec = mapVideoCodec(activeVideoFormat)
            val videoResolution = activeVideoFormat?.let { 
                if (it.width > 0 && it.height > 0) "${it.width}x${it.height}" else "1920x1088" 
            } ?: "1920x1088"
            val videoFrameRate = activeVideoFormat?.let { 
                if (it.frameRate > 0) String.format(java.util.Locale.US, "%.3f", it.frameRate) else "25.000" 
            } ?: "25.000"
            
            val audioBitrate = activeAudioFormat?.let { 
                if (it.bitrate > 0) "${it.bitrate / 1000} KB/s" else "256 KB/s" 
            } ?: "256 KB/s"
            val audioCodec = mapAudioCodec(activeAudioFormat)
            val audioChannels = activeAudioFormat?.let { 
                if (it.channelCount > 0) "${it.channelCount}" else "2" 
            } ?: "2"
            val audioSampleRate = activeAudioFormat?.let { 
                if (it.sampleRate > 0) "${it.sampleRate} Hz" else "48000 Hz" 
            } ?: "48000 Hz"

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (showControls) 80.dp else 24.dp, start = 24.dp)
                    .pointerInput(Unit) {
                        // Prevent click propagation to background player touch handlers
                        detectTapGestures(onTap = {})
                    },
                contentAlignment = Alignment.TopStart
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.75f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.width(360.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Video Header
                        Text(
                            text = "视频",
                            color = Color(0xFFFF7043), // Elegant peach/orange accent
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        MediaInfoRow("编码", videoCodec)
                        MediaInfoRow("分辨率", videoResolution)
                        MediaInfoRow("帧率", videoFrameRate)
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Audio Header
                        Text(
                            text = "音频",
                            color = Color(0xFFFF7043), // Elegant peach/orange accent
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        MediaInfoRow("码率", audioBitrate)
                        MediaInfoRow("编码", audioCodec)
                        MediaInfoRow("声道", audioChannels)
                        MediaInfoRow("采样率", audioSampleRate)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun mapVideoCodec(format: androidx.media3.common.Format?): String {
    if (format == null) return "H264 - MPEG-4 AVC (part 10)"
    val mime = format.sampleMimeType?.lowercase() ?: ""
    val codecs = format.codecs?.lowercase() ?: ""
    return when {
        mime.contains("hevc") || mime.contains("h265") || codecs.contains("hvc") -> "H265 - High Efficiency Video Coding (HEVC)"
        mime.contains("avc") || mime.contains("h264") || codecs.contains("avc") -> "H264 - MPEG-4 AVC (part 10)"
        mime.contains("vp9") -> "VP9 Video Codec"
        mime.contains("vp8") -> "VP8 Video Codec"
        mime.contains("av1") -> "AV1 Video Codec"
        mime.contains("mpeg") || mime.contains("h263") -> "MPEG Video"
        else -> "H264 - MPEG-4 AVC (part 10)"
    }
}

private fun mapAudioCodec(format: androidx.media3.common.Format?): String {
    if (format == null) return "MPEG Audio layer 1/2"
    val mime = format.sampleMimeType?.lowercase() ?: ""
    return when {
        mime.contains("mp4a") || mime.contains("aac") -> "AAC (Advanced Audio Coding)"
        mime.contains("mpeg") || mime.contains("mp3") || mime.contains("layer1") || mime.contains("layer2") -> "MPEG Audio layer 1/2"
        mime.contains("ac3") || mime.contains("dolby") -> "Dolby AC-3 Audio"
        mime.contains("eac3") -> "Dolby Enhanced AC-3 (E-AC-3)"
        mime.contains("dra") -> "DRA National Standard Audio"
        mime.contains("flac") -> "FLAC Lossless Audio"
        mime.contains("opus") -> "Opus Audio"
        else -> "MPEG Audio layer 1/2"
    }
}

@UnstableApi
class ChannelModeAudioProcessor : androidx.media3.common.audio.AudioProcessor {
    enum class Mode {
        STEREO,       // 双声道/立体声
        MONO,         // 单声道
        LEFT_ONLY,    // 左声道
        RIGHT_ONLY,   // 右声道
        SURROUND      // 环绕声
    }

    var mode: Mode = Mode.STEREO

    private var pendingOutput: ByteBuffer = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
    private var inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat = 
        androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat = 
        androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
    private var inputEnded: Boolean = false

    override fun configure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        outputAudioFormat = inputAudioFormat
        return outputAudioFormat
    }

    override fun isActive(): Boolean {
        return inputAudioFormat != androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position

        // We only process PCM_16BIT with 2 or more channels
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount >= 2) {
            if (outputBuffer.capacity() < size) {
                outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }

            val input = inputBuffer.asShortBuffer()
            val output = outputBuffer.asShortBuffer()

            val channelCount = inputAudioFormat.channelCount
            val sampleCount = (size / 2) / channelCount

            for (i in 0 until sampleCount) {
                val left = input.get(i * channelCount)
                val right = input.get(i * channelCount + 1)

                when (mode) {
                    Mode.STEREO -> {
                        for (c in 0 until channelCount) {
                            output.put(input.get(i * channelCount + c))
                        }
                    }
                    Mode.MONO -> {
                        val monoSample = ((left.toInt() + right.toInt()) / 2).toShort()
                        output.put(monoSample) // L
                        output.put(monoSample) // R
                        for (c in 2 until channelCount) {
                            output.put(input.get(i * channelCount + c))
                        }
                    }
                    Mode.LEFT_ONLY -> {
                        output.put(left) // L
                        output.put(left) // R
                        for (c in 2 until channelCount) {
                            output.put(input.get(i * channelCount + c))
                        }
                    }
                    Mode.RIGHT_ONLY -> {
                        output.put(right) // L
                        output.put(right) // R
                        for (c in 2 until channelCount) {
                            output.put(input.get(i * channelCount + c))
                        }
                    }
                    Mode.SURROUND -> {
                        // Standard unaltered passthrough
                        for (c in 0 until channelCount) {
                            output.put(input.get(i * channelCount + c))
                        }
                    }
                }
            }

            inputBuffer.position(limit)
            outputBuffer.limit(size)
            pendingOutput = outputBuffer
        } else {
            // Passthrough for non-PCM16 or mono input
            if (outputBuffer.capacity() < size) {
                outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            pendingOutput = outputBuffer
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = pendingOutput
        pendingOutput = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return inputEnded && pendingOutput == androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        pendingOutput = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        outputBuffer = androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
    }
}
