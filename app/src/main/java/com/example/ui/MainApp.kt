package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.model.Channel
import com.example.data.model.Playlist
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainApp(viewModel: IPTVViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeTheme = settings.theme
    val bgImage = settings.customBackgroundUrl

    MyApplicationTheme(themeName = activeTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Optional custom background image with blur overlay
            if (bgImage.isNotEmpty()) {
                val imageModel: Any = if (bgImage == "preset_cosmic") {
                    com.example.R.drawable.ambient_cosmic_bg_1783800183475
                } else {
                    bgImage
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = "自定义背景",
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(20.dp),
                    contentScale = ContentScale.Crop
                )
                // Dark tint overlay to maintain text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            } else {
                // Subtle ambient grid gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                )
                            )
                        )
                )
            }

            var isFullscreen by remember { mutableStateOf(false) }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth > 720.dp // Big Screen TV Box (usually 10-15+ inch adaptive)

                if (isWideScreen) {
                    TVBoxAdaptiveLayout(
                        viewModel = viewModel,
                        isFullscreen = isFullscreen,
                        onFullscreenChange = { isFullscreen = it }
                    )
                } else {
                    MobileAdaptiveLayout(
                        viewModel = viewModel,
                        isFullscreen = isFullscreen,
                        onFullscreenChange = { isFullscreen = it }
                    )
                }
            }

            // Global Voice Assistant overlay (hidden in fullscreen)
            if (!isFullscreen) {
                VoiceAssistantFloatingPanel(viewModel = viewModel)
            }
        }
    }
}

// -------------------------------------------------------------
// TV Box Adaptive Layout (Optimize for Big Screens, D-pad, 10-15 inch)
// -------------------------------------------------------------
@Composable
fun TVBoxAdaptiveLayout(
    viewModel: IPTVViewModel,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val channels by viewModel.currentChannels.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val activeChannel by viewModel.activeChannel.collectAsStateWithLifecycle()
    val activeEpg by viewModel.activeEpg.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPlaylistImportDialog by remember { mutableStateOf(false) }
    var isMultiScreenMode by remember { mutableStateOf(false) }
    val multiScreenList by viewModel.multiScreenChannels.collectAsStateWithLifecycle()

    val isTvFullscreen = isFullscreen
    var showTvMenu by remember(settings.minimalistModeEnabled) { mutableStateOf(!settings.minimalistModeEnabled) }
    var showLeftOverlayChannels by remember { mutableStateOf(false) }

    // Automatic fullscreen after 5 seconds of active streaming
    LaunchedEffect(activeChannel) {
        if (activeChannel != null && settings.autoFullscreenEnabled) {
            onFullscreenChange(false)
            delay(5000)
            onFullscreenChange(true)
        } else {
            onFullscreenChange(false)
        }
    }

    // Fullscreen auto-triggers minimalist mode
    LaunchedEffect(isTvFullscreen) {
        if (isTvFullscreen) {
            viewModel.updateMinimalistMode(true)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: Navigation Sidebar (Playlists, Categories, Channels)
        if (showTvMenu && !isTvFullscreen) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(16.dp)
            ) {
            // App Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "星辰 IPTV 播放器",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    IconButton(
                        onClick = { showPlaylistImportDialog = true },
                        modifier = Modifier.testTag("tv_import_m3u_btn")
                    ) {
                        Icon(Icons.Default.Add, "导入", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("tv_settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, "设置", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Playlist selector row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists) { playlist ->
                    val isSelected = playlist.id == selectedPlaylistId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { viewModel.selectPlaylist(playlist.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = playlist.name,
                                fontSize = 12.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            if (playlist.isLocked) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    "Locked",
                                    tint = Color.Red,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            // Delete button for playlist source
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除此源",
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { viewModel.deletePlaylist(playlist) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category selector Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { viewModel.selectCategory(cat) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat,
                            fontSize = 11.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Channel list matching categories
            Text(
                text = "频道列表 (${channels.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(channels) { channel ->
                    val isPlayingNow = activeChannel?.id == channel.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlayingNow) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectChannel(channel) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Logo or Placeholder
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                if (channel.logoUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = channel.logoUrl,
                                        contentDescription = channel.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = Color.LightGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = channel.category,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            // Favorite Icon
                            IconButton(onClick = { viewModel.toggleFavorite(channel) }) {
                                Icon(
                                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "收藏",
                                    tint = if (channel.isFavorite) Color.Red else Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Add to Multi screen btn
                            if (isMultiScreenMode) {
                                IconButton(onClick = { viewModel.addToMultiScreen(channel) }) {
                                    Icon(Icons.Default.Grid4x4, "分屏", tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
             // Right Column: Active Video Player & EPG schedule details
        Column(modifier = Modifier.weight(1f)) {
            // Player surface
            val playerHeightModifier = if (isTvFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(380.dp)
            Box(
                modifier = playerHeightModifier
                    .background(Color.Black)
            ) {
                if (isMultiScreenMode) {
                    TVMultiScreenGrid(
                        screens = multiScreenList,
                        viewModel = viewModel,
                        onCloseMode = { isMultiScreenMode = false }
                    )
                } else {
                    VideoPlayerView(
                        channel = activeChannel,
                        playbackSpeed = settings.playbackSpeed,
                        decoderMode = settings.decoder,
                        playerEngine = settings.playerEngine,
                        minimalistMode = settings.minimalistModeEnabled || isTvFullscreen,
                        onChannelSwitched = { isNext ->
                            val list = channels
                            val active = activeChannel
                            if (active != null && list.isNotEmpty()) {
                                val currentIdx = list.indexOfFirst { it.id == active.id }
                                if (currentIdx != -1) {
                                    val nextIdx = if (isNext) {
                                        if (currentIdx == list.size - 1) 0 else currentIdx + 1
                                    } else {
                                        if (currentIdx == 0) list.size - 1 else currentIdx - 1
                                    }
                                    viewModel.selectChannel(list[nextIdx])
                                }
                            }
                        },
                        onFullscreenToggle = { onFullscreenChange(!isTvFullscreen) },
                        isFullscreen = isTvFullscreen,
                        onConfirmClick = { showLeftOverlayChannels = !showLeftOverlayChannels },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Left program selection list overlay in minimalist/fullscreen mode
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showLeftOverlayChannels,
                        enter = slideInHorizontally(initialOffsetX = { -it }),
                        exit = slideOutHorizontally(targetOffsetX = { -it }),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(320.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.92f))
                                .clickable(enabled = false) {} // block clicks
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "节目列表",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    IconButton(onClick = { showLeftOverlayChannels = false }) {
                                        Icon(Icons.Default.Close, "关闭", tint = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Channels List
                                LazyColumn(
                                    modifier = Modifier.fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(channels) { channel ->
                                        val isPlayingNow = activeChannel?.id == channel.id
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isPlayingNow) MaterialTheme.colorScheme.primaryContainer
                                                else Color.DarkGray.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.selectChannel(channel)
                                                    showLeftOverlayChannels = false
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(35.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color.Gray),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (channel.logoUrl.isNotEmpty()) {
                                                        AsyncImage(
                                                            model = channel.logoUrl,
                                                            contentDescription = channel.name,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Fit
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.Tv, null, tint = Color.LightGray)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = channel.name,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating toggle menu button if menu is hidden or in fullscreen
                    if (!showTvMenu || isTvFullscreen) {
                        IconButton(
                            onClick = {
                                onFullscreenChange(false)
                                showTvMenu = true
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .testTag("tv_floating_menu_btn")
                        ) {
                            Icon(Icons.Default.Menu, "展开菜单", tint = Color.White)
                        }
                    }

                    // Overlay toggle for multi screen
                    IconButton(
                        onClick = {
                            isMultiScreenMode = true
                            activeChannel?.let { viewModel.addToMultiScreen(it) }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Dashboard, "开启多画面", tint = Color.White)
                    }
                }
            }

            // EPG Section
            if (!isTvFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EPG 自动节目单: ${activeChannel?.name ?: "无当前播放"}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "今日 ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (activeEpg.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("无节目单信息", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeEpg) { program ->
                            val now = System.currentTimeMillis()
                            val isActive = now in program.startTime..program.endTime
                            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                ),
                                border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${timeFormatter.format(Date(program.startTime))} - ${timeFormatter.format(Date(program.endTime))}",
                                        fontSize = 13.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = program.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (program.description.isNotEmpty()) {
                                            Text(
                                                text = program.description,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("直播中", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Settings Dialog for TV
    if (showSettingsDialog) {
        IPTVConfigDialog(viewModel = viewModel, onDismiss = { showSettingsDialog = false })
    }

    // Playlist Import Dialog for TV
    if (showPlaylistImportDialog) {
        PlaylistImportDialog(viewModel = viewModel, onDismiss = { showPlaylistImportDialog = false })
    }
}

// -------------------------------------------------------------
// TV Multi-Screen Grid (Simultaneously stream up to 4 feeds)
// -------------------------------------------------------------
@Composable
fun TVMultiScreenGrid(
    screens: List<Channel>,
    viewModel: IPTVViewModel,
    onCloseMode: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("多画面同屏观察室 (支持至4分屏)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = { viewModel.clearMultiScreen() }) {
                    Text("清空", color = Color.Yellow, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onCloseMode,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("退出分屏", fontSize = 11.sp)
                }
            }
        }

        if (screens.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("在左侧频道列表中，点击【分屏】图标加入分屏播放", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (screens.size <= 2) screens.size else 2),
                modifier = Modifier.fillMaxSize()
            ) {
                items(screens) { chan ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, Color.Gray)
                    ) {
                        VideoPlayerView(
                            channel = chan,
                            playbackSpeed = 1.0f,
                            decoderMode = settings.decoder,
                            playerEngine = settings.playerEngine,
                            minimalistMode = settings.minimalistModeEnabled,
                            modifier = Modifier.fillMaxSize()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(4.dp)
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(chan.name, color = Color.White, fontSize = 10.sp, maxLines = 1)
                            Icon(
                                Icons.Default.Close,
                                "移除",
                                tint = Color.Red,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { viewModel.removeFromMultiScreen(chan) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Mobile Adaptive Layout (Optimized for Portrait/Mobile viewport)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAdaptiveLayout(
    viewModel: IPTVViewModel,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit
) {
    val activeChannel by viewModel.activeChannel.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val channels by viewModel.currentChannels.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val activeEpg by viewModel.activeEpg.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }
    val isMobileFullscreen = isFullscreen
    var showLeftOverlayChannels by remember { mutableStateOf(false) }

    LaunchedEffect(isMobileFullscreen) {
        if (isMobileFullscreen) {
            viewModel.updateMinimalistMode(true)
        }
    }

    Scaffold(
        bottomBar = {
            if (!isMobileFullscreen) {
                NavigationBar(modifier = Modifier.testTag("mobile_bottom_nav")) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Tv, "直播室") },
                        label = { Text("直播室") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Folder, "源管理") },
                        label = { Text("源管理") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Settings, "设置") },
                        label = { Text("设置") }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (isMobileFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoPlayerView(
                    channel = activeChannel,
                    playbackSpeed = settings.playbackSpeed,
                    decoderMode = settings.decoder,
                    playerEngine = settings.playerEngine,
                    minimalistMode = settings.minimalistModeEnabled || isMobileFullscreen,
                    onChannelSwitched = { isNext ->
                        val list = channels
                        val active = activeChannel
                        if (active != null && list.isNotEmpty()) {
                            val currentIdx = list.indexOfFirst { it.id == active.id }
                            if (currentIdx != -1) {
                                val nextIdx = if (isNext) {
                                    if (currentIdx == list.size - 1) 0 else currentIdx + 1
                                } else {
                                    if (currentIdx == 0) list.size - 1 else currentIdx - 1
                                }
                                viewModel.selectChannel(list[nextIdx])
                            }
                        }
                    },
                    onFullscreenToggle = { onFullscreenChange(false) },
                    isFullscreen = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Live Player fixed on top for tab 0
                if (selectedTab == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Color.Black)
                    ) {
                        VideoPlayerView(
                            channel = activeChannel,
                            playbackSpeed = settings.playbackSpeed,
                            decoderMode = settings.decoder,
                            playerEngine = settings.playerEngine,
                            minimalistMode = settings.minimalistModeEnabled,
                            onChannelSwitched = { isNext ->
                                val list = channels
                                val active = activeChannel
                                if (active != null && list.isNotEmpty()) {
                                    val currentIdx = list.indexOfFirst { it.id == active.id }
                                    if (currentIdx != -1) {
                                        val nextIdx = if (isNext) {
                                            if (currentIdx == list.size - 1) 0 else currentIdx + 1
                                        } else {
                                            if (currentIdx == 0) list.size - 1 else currentIdx - 1
                                        }
                                        viewModel.selectChannel(list[nextIdx])
                                    }
                                }
                            },
                            onFullscreenToggle = { onFullscreenChange(true) },
                            isFullscreen = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

            when (selectedTab) {
                0 -> {
                    // Channels & EPG Tabs
                    var subTab by remember { mutableStateOf(0) } // 0 Channels, 1 EPG
                    TabRow(selectedTabIndex = subTab) {
                        Tab(selected = subTab == 0, onClick = { subTab = 0 }) {
                            Text("直播频道", modifier = Modifier.padding(12.dp))
                        }
                        Tab(selected = subTab == 1, onClick = { subTab = 1 }) {
                            Text("节目单 (EPG)", modifier = Modifier.padding(12.dp))
                        }
                    }

                    if (subTab == 0) {
                        // Category pills selector
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = cat == selectedCategory
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectCategory(cat) },
                                    label = { Text(cat) }
                                )
                            }
                        }

                        // Channel Lists
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(channels) { channel ->
                                val isSelected = activeChannel?.id == channel.id
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectChannel(channel) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(45.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.DarkGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (channel.logoUrl.isNotEmpty()) {
                                                AsyncImage(
                                                    model = channel.logoUrl,
                                                    contentDescription = channel.name,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(Icons.Default.Tv, null, tint = Color.LightGray)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = channel.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = channel.category,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        IconButton(onClick = { viewModel.toggleFavorite(channel) }) {
                                            Icon(
                                                imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "收藏",
                                                tint = if (channel.isFavorite) Color.Red else Color.LightGray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // EPG Program Schedule
                        if (activeEpg.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("加载 EPG 节目单中...", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(activeEpg) { program ->
                                    val now = System.currentTimeMillis()
                                    val isActive = now in program.startTime..program.endTime
                                    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${timeFormatter.format(Date(program.startTime))} - ${timeFormatter.format(Date(program.endTime))}",
                                                fontSize = 13.sp,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = program.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                                )
                                                if (program.description.isNotEmpty()) {
                                                    Text(
                                                        text = program.description,
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
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

                1 -> {
                    // Source Playlist Management
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("M3U 播放源列表", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = { showImportDialog = true },
                                modifier = Modifier.testTag("mobile_import_btn")
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("新建源")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(playlists) { pl ->
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pl.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Text(pl.url, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Row {
                                            IconButton(onClick = { viewModel.selectPlaylist(pl.id) }) {
                                                Icon(Icons.Default.PlayArrow, "载入此源", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { viewModel.deletePlaylist(pl) }) {
                                                Icon(Icons.Default.Delete, "删除", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Remote Push Assistant Panel
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Laptop, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("TV远程推送助手", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "确保您的设备连接到同一局域网Wifi，然后在手机/电脑浏览器中访问：",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val pushAddr by viewModel.pushServerAddress.collectAsStateWithLifecycle()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                ) {
                                    Text(
                                        text = pushAddr,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Player Settings Skin and Speed etc
                    PlayerSettingsView(viewModel = viewModel)
                }
            }
        }
    }
}

    if (showImportDialog) {
        PlaylistImportDialog(viewModel = viewModel, onDismiss = { showImportDialog = false })
    }
}



// -------------------------------------------------------------
// Player Settings Subview
// -------------------------------------------------------------
@Composable
fun PlayerSettingsView(viewModel: IPTVViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cacheSizeStr by viewModel.cacheSize.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("播放器与界面高度定制", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        // Theme and Skin
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("主题皮肤选择", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                
                val skins = listOf("Dark", "Classic TV", "Cyberpunk", "OLED Black", "Forest Green")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(skins) { skin ->
                        val isSelected = settings.theme == skin
                        Button(
                            onClick = { viewModel.updateTheme(skin) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = when (skin) {
                                    "Dark" -> "深空极简"
                                    "Classic TV" -> "复古电视"
                                    "Cyberpunk" -> "霓虹赛博"
                                    "OLED Black" -> "极致纯黑"
                                    "Forest Green" -> "森林护眼"
                                    else -> skin
                                },
                                fontSize = 12.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Custom backdrop wallpaper
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("自定义背景壁纸", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                var bgInput by remember { mutableStateOf(settings.customBackgroundUrl) }
                OutlinedTextField(
                    value = bgInput,
                    onValueChange = { bgInput = it },
                    placeholder = { Text("输入背景图片链接 (URL)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.updateCustomBackground(bgInput) }) {
                        Text("应用背景", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            bgInput = "preset_cosmic"
                            viewModel.updateCustomBackground("preset_cosmic")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("极光星辰壁纸", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            bgInput = ""
                            viewModel.updateCustomBackground("")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("重置壁纸", fontSize = 12.sp)
                    }
                }
            }
        }

        // Decoder mode and Cache limit
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("性能与后台缓存机制", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                Text("播放核心引擎 (Player Core)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val engines = listOf("ExoPlayer", "IjkPlayer", "MpvPlayer")
                    engines.forEach { eng ->
                        val isSelected = settings.playerEngine == eng
                        Button(
                            onClick = { viewModel.updatePlayerEngine(eng) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("engine_btn_$eng")
                        ) {
                            Text(
                                text = when (eng) {
                                    "ExoPlayer" -> "EXO 核心"
                                    "IjkPlayer" -> "Ijk 核心"
                                    "MpvPlayer" -> "Mpv 核心"
                                    else -> eng
                                },
                                fontSize = 11.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("解码工作模式 (Decoder Mode)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val decoders = listOf("Hardware", "Software", "Auto")
                    decoders.forEach { dec ->
                        val isSelected = settings.decoder == dec
                        Button(
                            onClick = { viewModel.updateDecoder(dec) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("decoder_btn_$dec")
                        ) {
                            Text(
                                text = when (dec) {
                                    "Hardware" -> "硬件解码"
                                    "Software" -> "软件解码"
                                    "Auto" -> "自动混合"
                                    else -> dec
                                },
                                fontSize = 11.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("缓存清理管理", fontSize = 13.sp)
                        Text("当前缓存占用: $cacheSizeStr", fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(
                        onClick = { viewModel.clearSystemCache() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("一键清理缓存", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Voice Assistant Floating Button & Modal (Voice command simulator)
// -------------------------------------------------------------
@Composable
fun VoiceAssistantFloatingPanel(viewModel: IPTVViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    val voiceFeedback by viewModel.voiceFeedback.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Voice assistant trigger button (FAB style)
        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 60.dp) // Offset above bottom navbar
                .testTag("voice_mic_btn"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Mic, "语音控制")
        }
    }

    if (showDialog) {
        var commandText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                viewModel.clearVoiceFeedback()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, "AI", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("智能语音控制助手")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "在电视大屏或手机上，你可以输入或点击下方预设的语音指令，控制频道切换和皮肤更换！",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        placeholder = { Text("说点什么，比如：播放 CCTV-1") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Feedbacks
                    voiceFeedback?.let { feedback ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = feedback,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Presets
                    Text("您还可以直接点击下方快捷指令试一试：", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    val presets = listOf("CCTV-1", "CCTV-6", "下一个频道", "切换赛博朋克主题", "切换深色主题")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { preset ->
                            SuggestionChip(
                                onClick = {
                                    commandText = preset
                                    viewModel.handleVoiceCommand(preset)
                                },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (commandText.isNotEmpty()) {
                        viewModel.handleVoiceCommand(commandText)
                    }
                }) {
                    Text("执行口令")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    viewModel.clearVoiceFeedback()
                }) {
                    Text("关闭")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Playlist Import Dialog Component
// -------------------------------------------------------------
@Composable
fun PlaylistImportDialog(viewModel: IPTVViewModel, onDismiss: () -> Unit) {
    var listName by remember { mutableStateOf("自选超清直播源") }
    var listUrl by remember { mutableStateOf("") }
    var listContent by remember { mutableStateOf("") }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入自定义 M3U 播放源") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("播放列表名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = listUrl,
                    onValueChange = { listUrl = it },
                    label = { Text("M3U 订阅链接 (URL)") },
                    placeholder = { Text("http://example.com/playlist.m3u") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("或者在下方粘贴本地 M3U 数据：", fontSize = 11.sp, color = Color.Gray)

                OutlinedTextField(
                    value = listContent,
                    onValueChange = { listContent = it },
                    label = { Text("M3U 本地源文件内容") },
                    placeholder = { Text("#EXTM3U\n#EXTINF:-1,CCTV1\nhttp://...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6
                )

                resultMsg?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (listUrl.isNotEmpty()) {
                        isImporting = true
                        viewModel.importPlaylistFromUrl(listName, listUrl) { ok, msg ->
                            isImporting = false
                            if (ok) {
                                onDismiss()
                            } else {
                                resultMsg = msg
                            }
                        }
                    } else if (listContent.isNotEmpty()) {
                        viewModel.importLocalM3u(listName, listContent)
                        onDismiss()
                    } else {
                        resultMsg = "请提供 M3U 链接或本地内容文件！"
                    }
                },
                enabled = !isImporting
            ) {
                Text(if (isImporting) "正在导入..." else "确认导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// -------------------------------------------------------------
// IPTV Config Dialog (Theme, Decoder, speed, cache etc.)
// -------------------------------------------------------------
@Composable
fun IPTVConfigDialog(viewModel: IPTVViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("星辰IPTV 播放器配置中心") },
        text = {
            Box(
                modifier = Modifier
                    .width(450.dp)
                    .height(350.dp)
            ) {
                PlayerSettingsView(viewModel = viewModel)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        content = { content() }
    )
}
