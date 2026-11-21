package com.zaidashiq.chat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    onOpenChat: (jid: String, name: String, phone: String) -> Unit,
    onOpenVault: () -> Unit,
    onOpenAutoStatus: () -> Unit,
    onOpenFullPp: () -> Unit,
    onOpenChannelReact: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenPairing: () -> Unit,
    onUploadTextStatus: (String, String) -> Unit,
    onUploadMediaStatus: (type: String, uri: Uri, caption: String, mime: String) -> Unit,
    onDismissStatusProgress: () -> Unit,
    onForwardStatus: (String) -> Unit
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
        containerColor = SurfacePureWhite,
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState.selectedTab == HomeTab.CHATS || uiState.selectedTab == HomeTab.GROUPS,
                enter = scaleIn(spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { showNewChatDialog = true },
                    containerColor = WhatsAppGreen,
                    contentColor = TextOnDark,
                    shape = CircleShape,
                    modifier = Modifier
                        .navigationBarsPadding()
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
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Animated Aurora-Emerald Header + 5 Icon Tabs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            ) {
                AnimatedAuroraEmeraldBackground(
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                // Top Action Bar: Camera | Title/Search | Search + 3-dot Menu
                if (uiState.isSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onToggleSearch(false) }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search", tint = TextOnDark)
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
                                cursorColor = OnlineMint,
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
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                statusPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("home_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = stringResource(R.string.action_camera),
                                tint = TextOnDark
                            )
                        }

                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = TextOnDark,
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
                                color = TextOnDarkMuted
                            )
                        }

                        IconButton(
                            onClick = { onToggleSearch(true) },
                            modifier = Modifier.testTag("home_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.action_search),
                                tint = TextOnDark
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { overflowExpanded = true },
                                modifier = Modifier.testTag("home_overflow_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_more_options),
                                    tint = TextOnDark
                                )
                            }

                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false },
                                containerColor = SurfacePureWhite
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_vault)) },
                                    leadingIcon = { Icon(Icons.Rounded.Shield, contentDescription = null, tint = DeepEmerald) },
                                    onClick = {
                                        overflowExpanded = false
                                        onOpenVault()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_auto_status)) },
                                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = DeepEmerald) },
                                    onClick = {
                                        overflowExpanded = false
                                        onOpenAutoStatus()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_full_pp)) },
                                    leadingIcon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = DeepEmerald) },
                                    onClick = {
                                        overflowExpanded = false
                                        onOpenFullPp()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_channel_react)) },
                                    leadingIcon = { Icon(Icons.Rounded.Campaign, contentDescription = null, tint = DeepEmerald) },
                                    onClick = {
                                        overflowExpanded = false
                                        onOpenChannelReact()
                                    }
                                )
                                HorizontalDivider(color = DividerSubtle)
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_connection_settings)) },
                                    leadingIcon = { Icon(Icons.Rounded.SettingsEthernet, contentDescription = null, tint = DeepEmerald) },
                                    onClick = {
                                        overflowExpanded = false
                                        onOpenConnection()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Link / Pair Phone Number") },
                                    leadingIcon = { Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = WhatsAppGreen) },
                                    onClick = {
                                        overflowExpanded = false
                                        onOpenPairing()
                                    }
                                )
                            }
                        }
                    }
                }

                // 5 Circular Icon Tabs matching Screen 1 (CHATS, STATUS, CALLS, GROUPS, CHANNELS)
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

            // Connection Banner when backend isn't reachable or not paired yet
            if (!uiState.bannerNotice.isNullOrBlank() || !uiState.isLinked) {
                Surface(
                    color = SoftSageTint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = if (!uiState.isLinked) onOpenPairing else onOpenConnection)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (!uiState.isLinked) Icons.Rounded.LinkOff else Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = DeepEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = uiState.bannerNotice
                                ?: "WhatsApp not linked yet — tap here to pair with 8-digit code",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = DeepEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Active Status Upload Progress Banner when user uploads from Camera shortcut on another tab
            if (uiState.selectedTab != HomeTab.STATUS && uiState.statusUploadProgress.isVisible) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                        animationSpec = tween(220)
                    ) + fadeIn(tween(200))) togetherWith (slideOutHorizontally(
                        targetOffsetX = { if (forward) -it / 5 else it / 5 },
                        animationSpec = tween(180)
                    ) + fadeOut(tween(150)))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfacePureWhite),
                label = "home_tab_content"
            ) { currentTab ->
                when (currentTab) {
                    HomeTab.CHATS -> ChatsTabBody(
                        chats = filteredChats,
                        isLoading = uiState.isRefreshingHome,
                        onRefresh = onRefresh,
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
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 0.96f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "tab_scale_${tab.name}"
    )
    val circleBg by animateColorAsState(
        targetValue = if (selected) SurfacePureWhite else TextOnDark.copy(alpha = 0.14f),
        animationSpec = tween(220),
        label = "tab_bg_${tab.name}"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) DeepEmerald else TextOnDark,
        animationSpec = tween(220),
        label = "tab_tint_${tab.name}"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("home_tab_${tab.name.lowercase()}")
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .scale(iconScale)
                    .clip(CircleShape)
                    .background(circleBg),
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
                        .background(OnlineMint)
                        .border(1.5.dp, DeepEmerald, CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = TextOnDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) TextOnDark else TextOnDarkMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 16.dp else 0.dp)
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
    onRefresh: () -> Unit,
    onOpenChat: (String, String, String) -> Unit,
    onNewChat: () -> Unit
) {
    if (isLoading && chats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DeepEmerald)
        }
        return
    }

    if (chats.isEmpty()) {
        EmptyStateCard(
            icon = Icons.Rounded.Forum,
            title = "No synced chats yet",
            subtitle = "Once linked via Baileys, incoming chats appear here automatically — or tap the green chat button to message any WhatsApp number right away.",
            actionLabel = "Start New Chat",
            onAction = onNewChat
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_chats_list"),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        items(chats, key = { it.resolvedJid.ifBlank { it.resolvedPhone } }) { chat ->
            ChatListRow(
                chat = chat,
                onClick = { onOpenChat(chat.resolvedJid, chat.resolvedName, chat.resolvedPhone) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                color = DividerSubtle,
                thickness = 0.7.dp
            )
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = DeepEmerald)
                    Spacer(Modifier.width(6.dp))
                    Text("Sync Chats from Railway", color = DeepEmerald)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatChatTime(chat.lastTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chat.unreadCount > 0) WhatsAppGreen else TextSecondary
                )
            }
            Spacer(Modifier.height(3.dp))
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
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
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
                            .background(WhatsAppGreen)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextOnDark,
                            fontWeight = FontWeight.Bold
                        )
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = SoftSageTint)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Post WhatsApp Status", style = MaterialTheme.typography.titleMedium, color = DeepEmerald)
                        TextButton(onClick = onOpenAutoStatus) {
                            Text("Auto-React", color = DeepEmerald)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
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
            items(statuses, key = { it.id }) { st ->
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite)
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
                                contentColor = DeepEmerald
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
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) SurfacePureWhite else SurfacePureWhite.copy(alpha = 0.55f),
        modifier = modifier.height(64.dp).testTag("status_upload_chip_${label.lowercase()}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) DeepEmerald else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(4.dp))
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
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(calls, key = { it.id }) { call ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChat(call.callerJid, call.callerName, call.callerNumber) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    tint = DeepEmerald
                )
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
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        items(filtered, key = { it.jid }) { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGroupChat(group) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(name = group.name, isGroup = true, showOnlineDot = false, size = 50.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${group.participantsCount} members" + if (group.description.isNotBlank()) " • ${group.description}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
            HorizontalDivider(modifier = Modifier.padding(start = 80.dp, end = 16.dp), color = DividerSubtle)
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = SoftSageTint)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Channel Auto-React Engine", style = MaterialTheme.typography.titleMedium, color = DeepEmerald)
                        Text(
                            "Automatically react with custom emojis on new posts in your WhatsApp Channels.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onManageRules,
                        colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Manage")
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
            items(channels, key = { it.id.ifBlank { it.jid } }) { ch ->
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite)
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
                                color = TextSecondary
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
        Color(0xFF0D6E61),
        Color(0xFF1E88E5),
        Color(0xFF6D4C41),
        Color(0xFF5E35B1),
        Color(0xFF00897B),
        Color(0xFFD81B60)
    )
    val bgColor = palette[kotlin.math.abs(name.hashCode()) % palette.size]
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
            .background(bgColor.copy(alpha = 0.16f))
            .border(1.dp, bgColor.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isGroup) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = bgColor,
                modifier = Modifier.size(size * 0.52f)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = bgColor,
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
                .size(68.dp)
                .clip(CircleShape)
                .background(SoftSageTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DeepEmerald, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
            ) {
                Text(actionLabel)
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
        title = { Text("Start New WhatsApp Chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' } },
                    label = { Text("Phone number with country code") },
                    placeholder = { Text("e.g. 923001234567") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("new_chat_phone_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (contacts.isNotEmpty()) {
                    Text("Synced Contacts", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(contacts.take(15)) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStartChat(c.number, c.name) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald),
                modifier = Modifier.testTag("new_chat_confirm_button")
            ) {
                Text("Open Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
        title = { Text("Post Text Status") },
        text = {
            OutlinedTextField(
                value = statusText,
                onValueChange = { statusText = it },
                placeholder = { Text("Write your WhatsApp status…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onPost(statusText, "#075E54") },
                enabled = statusText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
            ) {
                Text("Upload Status")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatChatTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val normalized = if (timestampMs < 1_000_000_000_000L) timestampMs * 1000L else timestampMs
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(normalized))
}
