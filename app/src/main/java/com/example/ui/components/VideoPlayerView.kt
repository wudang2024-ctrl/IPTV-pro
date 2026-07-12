package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
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

    // Handle lifecycle and tracks changed listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val newVideoTracks = mutableListOf<String>()
                val newAudioTracks = mutableListOf<String>()
                
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val label = format.label ?: "视频轨 ${newVideoTracks.size + 1} (${format.width}x${format.height})"
                            newVideoTracks.add(label)
                        }
                    } else if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val language = format.language?.let { " [$it]" } ?: ""
                            val label = format.label ?: "音频轨 ${newAudioTracks.size + 1}${language}"
                            newAudioTracks.add(label)
                        }
                    }
                }
                
                if (newVideoTracks.isNotEmpty()) {
                    videoTracks = newVideoTracks
                }
                if (newAudioTracks.isNotEmpty()) {
                    audioTracks = newAudioTracks
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
            .clickable {
                if (minimalistMode && onConfirmClick != null) {
                    onConfirmClick()
                } else {
                    showControls = !showControls
                }
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
                    // Advanced configurations row (声音穿透 / 国标解码与DRA音轨)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Passthrough toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "声音穿透",
                                tint = if (passthroughEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("音频输出模式: ", color = Color.LightGray, fontSize = 11.sp)
                            listOf(false to "解码输出 (PCM)", true to "穿透输出 (Passthrough/Bitstream)").forEach { (value, label) ->
                                TextButton(
                                    onClick = { passthroughEnabled = value },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (passthroughEnabled == value) MaterialTheme.colorScheme.primary else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 50.dp, minHeight = 24.dp)
                                ) {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (passthroughEnabled == value) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Codec profiles status (AVS+, AVS2, DRA, AC3, PCM)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = "音轨解码",
                                tint = if (avsPriorityEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("音频轨道解码: ", color = Color.LightGray, fontSize = 11.sp)
                            TextButton(
                                onClick = { avsPriorityEnabled = !avsPriorityEnabled },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (avsPriorityEnabled) MaterialTheme.colorScheme.primary else Color.LightGray
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.defaultMinSize(minWidth = 50.dp, minHeight = 24.dp)
                            ) {
                                Text(
                                    if (avsPriorityEnabled) "国标 AVS2/AVS+ & DRA/AC-3/PCM 双解码激活" else "基础解码引擎",
                                    fontSize = 11.sp,
                                    fontWeight = if (avsPriorityEnabled) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Video Track 1 and Audio Track Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Video tracks selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "视频轨",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("视频轨道: ", color = Color.LightGray, fontSize = 11.sp)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(videoTracks.size) { index ->
                                    val isSelected = selectedVideoTrackIndex == index
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else Color.White.copy(alpha = 0.12f)
                                            )
                                            .clickable { selectVideoTrack(index) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = videoTracks[index],
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Audio tracks selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = "音轨",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("音频轨道: ", color = Color.LightGray, fontSize = 11.sp)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(audioTracks.size) { index ->
                                    val isSelected = selectedAudioTrackIndex == index
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.secondary
                                                else Color.White.copy(alpha = 0.12f)
                                            )
                                            .clickable { selectAudioTrack(index) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = audioTracks[index],
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

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
    }
}
