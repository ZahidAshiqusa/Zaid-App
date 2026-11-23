package com.zaidashiq.chat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaidashiq.chat.R
import com.zaidashiq.chat.data.model.*
import com.zaidashiq.chat.ui.components.AnimatedAuroraEmeraldBackground
import com.zaidashiq.chat.ui.theme.*
import com.zaidashiq.chat.ui.viewmodel.HomeTab
import com.zaidashiq.chat.ui.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: MainUiState,
    onSelectTab: (HomeTab) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadOlderChats: () -> Unit,
    onOpenChat: (jid: String, name: String, phone: String) -> Unit,
    onOpenVault: () -> Unit,
    onOpenAutoStatus: () -> Unit,
    onOpenFullPp: () -> Unit,
    onOpenChannelReact: () -> Unit,
    onOpenPrivacyAccount: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenPairing: () -> Unit,
    onUploadTextStatus: (String, String) -> Unit,
    onUploadMediaStatus: (type: String, uri: Uri, caption: String, mime: String) -> Unit,
    onDismissStatusProgress: () -> Unit,
    onForwardStatus: (String) -> Unit,
    onClearAllChats: () -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showTextStatusDialog by remember { mutableStateOf(false) }

    val statusPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadMediaStatus("image", uri, "", "image/jpeg")
        }
    }

    val statusVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadMediaStatus("video", uri, "", "video/mp4")
        }
    }

    val statusVoicePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onUploadMediaStatus("voice", uri, "", "audio/ogg")
        }
    }

    val filteredChats by remember(uiState.chats, uiState.contacts, uiState.searchQuery) {
        derivedStateOf {
            val q = uiState.searchQuery.trim().lowercase()
            val merged = if (uiState.chats.isNotEmpty()) {
                uiState.chats
            } else {
                uiState.contacts.map { c ->
                    ChatSummaryItem(
                        number = c.number,
                        cleanNumber = c.number,
                        jid = c.jid,
                        contactName = c.name,
                        lastMessage = "Tap to start chatting"
                    )
                }
            }
            if (q.isEmpty()) merged
            else merged.filter {
                it.resolvedName.lowercase().contains(q) ||
                    it.resolvedPhone.contains(q) ||
                    it.lastMessage.lowercase().contains(q)
            }
        }
    }

    val totalUnread = remember(uiState.chats) {
        uiState.chats.sumOf { it.unreadCount }
    }

    Scaffold(
        containerColor = ObsidianBase,
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState.selectedTab == HomeTab.CHATS || uiState.selectedTab == HomeTab.GROUPS,
                enter = fadeIn(tween(180, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(150, easing = FastOutSlowInEasing))
            ) {
                FloatingActionButton(
                    onClick = { showNewChatDialog = true },
                    containerColor = EmeraldGlow,
                    contentColor = ObsidianBase,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .border(1.2.dp, NeonMintAccent.copy(alpha = 0.65f), CircleShape)
                        .testTag("home_new_chat_fab")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = stringResource(R.string.action_new_chat)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ObsidianSurface, ObsidianBase, ObsidianBase)
                    )
                )
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Luxury Silk-Obsidian Curved Header + 5 Floating Glass Icon Tabs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                    .border(
                        width = 1.dp,
                        color = GlassBorderEmerald.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
                    )
            ) {
                AnimatedAuroraEmeraldBackground(
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    // Top Action Bar: Camera | Brand Title/Search | Search + 3-dot Menu
                    if (uiState.isSearchActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onToggleSearch(false) }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search", tint = NeonMintAccent)
                            }
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = onSearchChange,
                                placeholder = { Text("Search chats, contacts, groups…", color = TextOnDarkMuted) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = TextOnDark,
                                    unfocusedTextColor = TextOnDark,
                                    cursorColor = NeonMintAccent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_search_input")
                            )
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = TextOnDark)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    statusPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianBase.copy(alpha = 0.42f))
                                    .border(1.dp, GlassBorderEmerald.copy(alpha = 0.55f), CircleShape)
                                    .testTag("home_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = stringResource(R.string.action_camera),
                                    tint = NeonMintAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextOnDark,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (uiState.isLinked && uiState.linkedPhone.isNotBlank()) {
                                        "Linked • +${uiState.linkedPhone}"
                                    } else {
                                        "Tap menu to link WhatsApp"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonMintAccent.copy(alpha = 0.85f)
                                )
                            }

                            IconButton(
                                onClick = { onToggleSearch(true) },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianBase.copy(alpha = 0.42f))
                                    .border(1.dp, GlassBorder.copy(alpha = 0.65f), CircleShape)
                                    .testTag("home_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.action_search),
                                    tint = TextOnDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Box {
                                IconButton(
                                    onClick = { overflowExpanded = true },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(ObsidianBase.copy(alpha = 0.42f))
                                        .border(1.dp, GlassBorder.copy(alpha = 0.65f), CircleShape)
                                        .testTag("home_overflow_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = stringResource(R.string.action_more_options),
                                        tint = TextOnDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = overflowExpanded,
                                    onDismissRequest = { overflowExpanded = false },
                                    containerColor = GlassCardElevated,
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.65f))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_privacy_account), color = NeonMintAccent, fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = NeonMintAccent) },
                                        onClick = {
                                            overflowExpanded = false
                                            onOpenPrivacyAccount()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_vault), color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Rounded.Shield, contentDescription = null, tint = NeonMintAccent) },
                                        onClick = {
                                            overflowExpanded = false
                                            onOpenVault()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_auto_status), color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = NeonMintAccent) },
                                        onClick = {
                                            overflowExpanded = false
                                            onOpenAutoStatus()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_full_pp), color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = NeonMintAccent) },
                                        onClick = {
                                            overflowExpanded = false
                                            onOpenFullPp()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_channel_react), color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Rounded.Campaign, contentDescription = null, tint = NeonMintAccent) },
                                        onClick = {
                                            overflowExpanded = false
                                            onOpenChannelReact()
                                        }
                                    )
                                    HorizontalDivider(color = GlassBorder)
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_clear_old_chats), color = ErrorCoral, fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = ErrorCoral) },
                                        onClick = {
                                            overflowExpanded = false
                                            onClearAllChats()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_connection_settings), color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Rounded.SettingsEthernet, contentDescription = null, tint = TextOnDarkMuted) },
                                        onClick = {
                                            overflowExpanded = false
                                            onOpenConnection()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Link / Pair Phone Number", color = NeonMintAccent, fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = EmeraldGlow) },
                                        onClick = {
                                            overflowExpanded = false
                                            onOpenPairing()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 5 Glassmorphic Circular Icon Tabs (CHATS, STATUS, CALLS, GROUPS, CHANNELS)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeTabButton(
                            tab = HomeTab.CHATS,
                            icon = Icons.Rounded.ChatBubble,
                            selected = uiState.selectedTab == HomeTab.CHATS,
                            badgeCount = totalUnread,
                            onClick = { onSelectTab(HomeTab.CHATS) }
                        )
                        HomeTabButton(
                            tab = HomeTab.STATUS,
                            icon = Icons.Rounded.DonutLarge,
                            selected = uiState.selectedTab == HomeTab.STATUS,
                            onClick = { onSelectTab(HomeTab.STATUS) }
                        )
                        HomeTabButton(
                            tab = HomeTab.CALLS,
                            icon = Icons.Rounded.Call,
                            selected = uiState.selectedTab == HomeTab.CALLS,
                            onClick = { onSelectTab(HomeTab.CALLS) }
                        )
                        HomeTabButton(
                            tab = HomeTab.GROUPS,
                            icon = Icons.Rounded.Groups,
                            selected = uiState.selectedTab == HomeTab.GROUPS,
                            onClick = { onSelectTab(HomeTab.GROUPS) }
                        )
                        HomeTabButton(
                            tab = HomeTab.CHANNELS,
                            icon = Icons.Rounded.DynamicFeed,
                            selected = uiState.selectedTab == HomeTab.CHANNELS,
                            onClick = { onSelectTab(HomeTab.CHANNELS) }
                        )
                    }
                }
            }

            // Sleek Obsidian-Emerald Connection Banner when backend isn't reachable or not paired yet
            if (!uiState.bannerNotice.isNullOrBlank() || !uiState.isLinked) {
                Surface(
                    color = SoftSageTint,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = if (!uiState.isLinked) onOpenPairing else onOpenConnection)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (!uiState.isLinked) Icons.Rounded.LinkOff else Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = NeonMintAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = uiState.bannerNotice
                                ?: "WhatsApp not linked yet — tap here to pair with 8-digit code",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextOnDark,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = NeonMintAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Active Status Upload Progress Banner when user uploads from Camera shortcut on another tab
            if (uiState.selectedTab != HomeTab.STATUS && uiState.statusUploadProgress.isVisible) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    StatusUploadProgressCard(
                        progress = uiState.statusUploadProgress,
                        onDismiss = onDismissStatusProgress
                    )
                }
            }

            // Smooth Animated Tab Body Content
            AnimatedContent(
                targetState = uiState.selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    (slideInHorizontally(
                        initialOffsetX = { if (forward) it / 5 else -it / 5 },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(200))) togetherWith (slideOutHorizontally(
                        targetOffsetX = { if (forward) -it / 5 else it / 5 },
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(150)))
                },
                modifier = Modifier.fillMaxSize(),
                label = "home_tab_content"
            ) { currentTab ->
                when (currentTab) {
                    HomeTab.CHATS -> ChatsTabBody(
                        chats = filteredChats,
                        isLoading = uiState.isRefreshingHome,
                        isLoadingOlder = uiState.isLoadingOlderChats,
                        onLoadOlderChats = onLoadOlderChats,
                        onOpenChat = onOpenChat,
                        onNewChat = { showNewChatDialog = true }
                    )

                    HomeTab.STATUS -> StatusTabBody(
                        statuses = uiState.capturedStatuses,
                        uploadProgress = uiState.statusUploadProgress,
                        onDismissUploadProgress = onDismissStatusProgress,
                        onUploadTextClick = { showTextStatusDialog = true },
                        onUploadPhotoClick = {
                            statusPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onUploadVideoClick = {
                            statusVideoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onUploadVoiceClick = {
                            statusVoicePicker.launch(arrayOf("audio/*"))
                        },
                        onForwardStatus = onForwardStatus,
                        onOpenAutoStatus = onOpenAutoStatus
                    )

                    HomeTab.CALLS -> CallsTabBody(
                        calls = uiState.calls,
                        onOpenChat = onOpenChat
                    )

                    HomeTab.GROUPS -> GroupsTabBody(
                        groups = uiState.groups,
                        searchQuery = uiState.searchQuery,
                        onRefresh = onRefresh,
                        onOpenGroupChat = { group ->
                            onOpenChat(group.jid, group.name, group.jid)
                        }
                    )

                    HomeTab.CHANNELS -> ChannelsTabBody(
                        channels = uiState.channels,
                        onManageRules = onOpenChannelReact
                    )
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewChatDialog(
            contacts = uiState.contacts,
            onDismiss = { showNewChatDialog = false },
            onStartChat = { phone, name ->
                showNewChatDialog = false
                val digits = phone.filter { it.isDigit() }
                if (digits.isNotBlank()) {
                    onOpenChat("$digits@s.whatsapp.net", name.ifBlank { "+$digits" }, digits)
                }
            }
        )
    }

    if (showTextStatusDialog) {
        TextStatusDialog(
            onDismiss = { showTextStatusDialog = false },
            onPost = { text, hex ->
                showTextStatusDialog = false
                onUploadTextStatus(text, hex)
            }
        )
    }
}

@Composable
private fun HomeTabButton(
    tab: HomeTab,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val circleBg by animateColorAsState(
        targetValue = if (selected) EmeraldGlow else ObsidianBase.copy(alpha = 0.45f),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "tab_bg_${tab.name}"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) ObsidianBase else TextOnDarkMuted,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "tab_tint_${tab.name}"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("home_tab_${tab.name.lowercase()}")
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(circleBg)
                    .border(
                        width = 1.dp,
                        color = if (selected) NeonMintAccent else GlassBorderEmerald.copy(alpha = 0.45f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp)
                )
            }
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = (-2).dp)
                        .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                        .clip(CircleShape)
                        .background(NeonMintAccent)
                        .border(1.5.dp, ObsidianBase, CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = ObsidianBase,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) NeonMintAccent else TextOnDarkMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 18.dp else 0.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(50))
                .background(NeonMintAccent)
        )
    }
}

@Composable
private fun ChatsTabBody(
    chats: List<ChatSummaryItem>,
    isLoading: Boolean,
    isLoadingOlder: Boolean,
    onLoadOlderChats: () -> Unit,
    onOpenChat: (String, String, String) -> Unit,
    onNewChat: () -> Unit
) {
    if (isLoading && chats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NeonMintAccent)
        }
        return
    }

    if (chats.isEmpty()) {
        EmptyStateCard(
            icon = Icons.Rounded.Forum,
            title = "No synced chats yet",
            subtitle = "Once linked via Baileys, incoming & older chats appear here automatically — or tap below to sync older chats or start a new chat.",
            actionLabel = "Load Older Chats",
            onAction = onLoadOlderChats
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_chats_list"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = chats,
            key = { idx, item -> "chat_${idx}_${item.resolvedJid.ifBlank { item.resolvedPhone }}" }
        ) { _, chat ->
            ChatListRow(
                chat = chat,
                onClick = { onOpenChat(chat.resolvedJid, chat.resolvedName, chat.resolvedPhone) }
            )
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = onLoadOlderChats,
                    enabled = !isLoadingOlder,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, GlassBorderEmerald),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = GlassCardSurface,
                        contentColor = NeonMintAccent
                    ),
                    modifier = Modifier.testTag("home_load_older_chats_button")
                ) {
                    if (isLoadingOlder) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NeonMintAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Loading older chats…", color = NeonMintAccent)
                    } else {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonMintAccent)
                        Spacer(Modifier.width(6.dp))
                        Text("Load Older Chats", color = NeonMintAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListRow(
    chat: ChatSummaryItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = GlassCardSurface.copy(alpha = 0.84f),
        border = BorderStroke(0.8.dp, GlassBorder.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(
                name = chat.resolvedName,
                isGroup = chat.isGroup,
                showOnlineDot = false,
                size = 52.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.resolvedName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatChatTime(chat.lastTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (chat.unreadCount > 0) NeonMintAccent else TextSecondary,
                        fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (chat.lastMessageType != "text" && chat.lastMessageType.isNotBlank()) {
                        val icon = when (chat.lastMessageType.lowercase()) {
                            "image" -> Icons.Rounded.Image
                            "video", "ptv" -> Icons.Rounded.Videocam
                            "audio", "voice" -> Icons.Rounded.Mic
                            else -> Icons.Rounded.AttachFile
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = NeonMintAccent.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = chat.lastMessage.ifBlank { "Tap to open chat" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                                .clip(CircleShape)
                                .background(EmeraldGlow)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = ObsidianBase,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTabBody(
    statuses: List<CapturedStatusItem>,
    uploadProgress: com.zaidashiq.chat.ui.viewmodel.StatusUploadProgress,
    onDismissUploadProgress: () -> Unit,
    onUploadTextClick: () -> Unit,
    onUploadPhotoClick: () -> Unit,
    onUploadVideoClick: () -> Unit,
    onUploadVoiceClick: () -> Unit,
    onForwardStatus: (String) -> Unit,
    onOpenAutoStatus: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = GlassCardSurface,
                border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.65f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Post WhatsApp Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onOpenAutoStatus) {
                            Text("Auto-React", color = NeonMintAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusQuickChip("Text", Icons.Rounded.Edit, Modifier.weight(1f), !uploadProgress.isActive, onUploadTextClick)
                        StatusQuickChip("Photo", Icons.Rounded.Image, Modifier.weight(1f), !uploadProgress.isActive, onUploadPhotoClick)
                        StatusQuickChip("Video", Icons.Rounded.Videocam, Modifier.weight(1f), !uploadProgress.isActive, onUploadVideoClick)
                        StatusQuickChip("Voice", Icons.Rounded.Mic, Modifier.weight(1f), !uploadProgress.isActive, onUploadVoiceClick)
                    }
                }
            }
        }

        if (uploadProgress.isVisible) {
            item {
                StatusUploadProgressCard(
                    progress = uploadProgress,
                    onDismiss = onDismissUploadProgress
                )
            }
        }

        item {
            Text(
                text = "Captured Contacts' Statuses (${statuses.size})",
                style = MaterialTheme.typography.titleSmall,
                color = TextOnDarkMuted,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

        if (statuses.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.DonutLarge,
                    title = "No captured statuses yet",
                    subtitle = "Incoming statuses viewed by your Baileys session appear here so you can forward them to your DM in one tap.",
                    actionLabel = "Configure Auto-Status",
                    onAction = onOpenAutoStatus
                )
            }
        } else {
            itemsIndexed(
                items = statuses,
                key = { idx, st -> "st_${idx}_${st.id}" }
            ) { _, st ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GlassCardSurface,
                    border = BorderStroke(0.8.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(name = st.senderName.ifBlank { st.senderNumber }, isGroup = false, showOnlineDot = false, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = st.senderName.ifBlank { "+${st.senderNumber}" },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = st.caption.ifBlank { "${st.mediaType.uppercase()} Status" },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = { onForwardStatus(st.id) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SoftSagePill,
                                contentColor = NeonMintAccent
                            )
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "Save to DM", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusQuickChip(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) GlassCardElevated else GlassCardElevated.copy(alpha = 0.45f),
        border = BorderStroke(0.8.dp, if (enabled) GlassBorderEmerald.copy(alpha = 0.55f) else GlassBorder),
        modifier = modifier.height(66.dp).testTag("status_upload_chip_${label.lowercase()}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) NeonMintAccent else TextSecondary,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) TextPrimary else TextSecondary
            )
        }
    }
}

@Composable
private fun CallsTabBody(
    calls: List<CallRecordItem>,
    onOpenChat: (String, String, String) -> Unit
) {
    if (calls.isEmpty()) {
        EmptyStateCard(
            icon = Icons.Rounded.Call,
            title = "No recent calls",
            subtitle = "Voice and video call activity detected on your linked WhatsApp device will appear here.",
            actionLabel = null,
            onAction = null
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = calls,
            key = { idx, call -> "call_${idx}_${call.id}" }
        ) { _, call ->
            Surface(
                onClick = { onOpenChat(call.callerJid, call.callerName, call.callerNumber) },
                shape = RoundedCornerShape(18.dp),
                color = GlassCardSurface,
                border = BorderStroke(0.8.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(name = call.callerName, isGroup = false, showOnlineDot = false, size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(call.callerName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "${call.status.replaceFirstChar { it.uppercase() }} • ${formatChatTime(call.timestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (call.status == "missed") ErrorCoral else TextSecondary
                        )
                    }
                    Icon(
                        imageVector = if (call.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = null,
                        tint = NeonMintAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupsTabBody(
    groups: List<GroupItem>,
    searchQuery: String,
    onRefresh: () -> Unit,
    onOpenGroupChat: (GroupItem) -> Unit
) {
    val filtered = remember(groups, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) groups else groups.filter { it.name.lowercase().contains(q) }
    }
    if (filtered.isEmpty()) {
        EmptyStateCard(
            icon = Icons.Rounded.Groups,
            title = "No WhatsApp groups loaded",
            subtitle = "Pair your WhatsApp number on Railway and tap Sync to browse and chat in your WhatsApp groups.",
            actionLabel = "Refresh Groups",
            onAction = onRefresh
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = filtered,
            key = { idx, group -> "grp_${idx}_${group.jid}" }
        ) { _, group ->
            Surface(
                onClick = { onOpenGroupChat(group) },
                shape = RoundedCornerShape(18.dp),
                color = GlassCardSurface,
                border = BorderStroke(0.8.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(name = group.name, isGroup = true, showOnlineDot = false, size = 50.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            group.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${group.participantsCount} members" + if (group.description.isNotBlank()) " • ${group.description}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = NeonMintAccent)
                }
            }
        }
    }
}

@Composable
private fun ChannelsTabBody(
    channels: List<ChannelItem>,
    onManageRules: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = GlassCardSurface,
                border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Channel Auto-React Engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonMintAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Instantly react with custom emojis on new posts in your WhatsApp Channels.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onManageRules,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGlow,
                            contentColor = ObsidianBase
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Manage", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (channels.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Campaign,
                    title = "No channels configured",
                    subtitle = "Add a WhatsApp Channel link in Channel Auto-React Rules to monitor and react automatically.",
                    actionLabel = "Add Channel Rule",
                    onAction = onManageRules
                )
            }
        } else {
            itemsIndexed(
                items = channels,
                key = { idx, ch -> "ch_${idx}_${ch.id.ifBlank { ch.jid }}" }
            ) { _, ch ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GlassCardSurface,
                    border = BorderStroke(0.8.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(name = ch.name, isGroup = true, showOnlineDot = false, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                text = "Reactions: ${ch.emojis.joinToString(" ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonMintAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactAvatar(
    name: String,
    isGroup: Boolean,
    showOnlineDot: Boolean = false,
    size: androidx.compose.ui.unit.Dp
) {
    val palette = listOf(
        Color(0xFF34F5B5),
        Color(0xFF38BDF8),
        Color(0xFFA78BFA),
        Color(0xFFF472B6),
        Color(0xFFFBBF24),
        Color(0xFF2DD4BF)
    )
    val accentColor = palette[kotlin.math.abs(name.hashCode()) % palette.size]
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f),
                        GlassCardElevated
                    )
                )
            )
            .border(1.2.dp, accentColor.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isGroup) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(size * 0.52f)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(SoftSagePill)
                .border(1.dp, NeonMintAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = NeonMintAccent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGlow,
                    contentColor = ObsidianBase
                )
            ) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NewChatDialog(
    contacts: List<ContactItem>,
    onDismiss: () -> Unit,
    onStartChat: (phone: String, name: String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassCardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Start New WhatsApp Chat", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' } },
                    label = { Text("Phone number with country code") },
                    placeholder = { Text("e.g. 923001234567") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("new_chat_phone_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (contacts.isNotEmpty()) {
                    Text("Synced Contacts", style = MaterialTheme.typography.labelMedium, color = NeonMintAccent)
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(contacts.take(15)) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onStartChat(c.number, c.name) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                                Text("+${c.number}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStartChat(phone, name) },
                enabled = phone.filter { it.isDigit() }.length >= 7,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGlow,
                    contentColor = ObsidianBase
                ),
                modifier = Modifier.testTag("new_chat_confirm_button")
            ) {
                Text("Open Chat", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun TextStatusDialog(
    onDismiss: () -> Unit,
    onPost: (String, String) -> Unit
) {
    var statusText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassCardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Post Text Status", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = statusText,
                onValueChange = { statusText = it },
                placeholder = { Text("Write your WhatsApp status…") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onPost(statusText, "#075E54") },
                enabled = statusText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGlow,
                    contentColor = ObsidianBase
                )
            ) {
                Text("Upload Status", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

fun formatChatTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val normalized = if (timestampMs < 1_000_000_000_000L) timestampMs * 1000L else timestampMs
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(normalized))
}
