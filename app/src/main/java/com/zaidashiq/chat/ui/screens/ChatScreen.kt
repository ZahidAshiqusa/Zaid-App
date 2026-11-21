package com.zaidashiq.chat.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaidashiq.chat.R
import com.zaidashiq.chat.data.model.ChatMessageItem
import com.zaidashiq.chat.data.model.GiphyItem
import com.zaidashiq.chat.ui.components.MediaSendingLoadingCard
import com.zaidashiq.chat.ui.theme.*
import com.zaidashiq.chat.ui.viewmodel.ChatToolType
import com.zaidashiq.chat.ui.viewmodel.MainUiState
import java.io.File

@Composable
fun ChatScreen(
    recipientJid: String,
    recipientName: String,
    uiState: MainUiState,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onOpenButtonComposer: () -> Unit,
    onDownloadAndSend: (ChatToolType, String, String, () -> Unit) -> Unit,
    onSearchGiphy: (String) -> Unit,
    onSendGiphy: (GiphyItem, () -> Unit) -> Unit,
    onSendVideoNoteUri: (Uri, () -> Unit) -> Unit,
    onSendVoiceNoteUri: (Uri, () -> Unit) -> Unit,
    onSendRecordedVoice: (File, () -> Unit) -> Unit,
    onSendFakeLocation: (Double, Double, Int, String, () -> Unit) -> Unit
) {
    var messageInput by remember { mutableStateOf("") }
    var isPlusMenuOpen by remember { mutableStateOf(false) }
    var activeToolSheet by remember { mutableStateOf<ChatToolType?>(null) }

    val isPlayCommand by remember(messageInput) {
        derivedStateOf {
            messageInput.trimStart().startsWith(".play", ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()
    val plusRotation by animateFloatAsState(
        targetValue = if (isPlusMenuOpen) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "plus_rotation"
    )
    val plusBgColor by animateColorAsState(
        targetValue = if (isPlusMenuOpen) DeepEmerald else FloatingMenuTeal,
        animationSpec = tween(220),
        label = "plus_bg"
    )
    val sendBtnScale by animateFloatAsState(
        targetValue = if (messageInput.isNotBlank()) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "send_scale"
    )

    LaunchedEffect(uiState.activeChatMessages.size) {
        if (uiState.activeChatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.activeChatMessages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatCanvasBeige)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sleek Stealth Chat Top Header (NO "online" or "last seen" presence!)
            Surface(
                color = SurfacePureWhite,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBackIosNew,
                            contentDescription = "Back",
                            tint = DeepEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    ContactAvatar(
                        name = recipientName,
                        isGroup = recipientJid.endsWith("@g.us"),
                        showOnlineDot = false,
                        size = 42.dp
                    )

                    Spacer(Modifier.width(12.dp))

                    // Strictly no online/last-seen presence shown — clean identity only
                    Text(
                        text = recipientName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { activeToolSheet = ChatToolType.VOICE_NOTE },
                        modifier = Modifier.testTag("chat_voice_shortcut_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Voice Note Shortcut",
                            tint = DeepEmerald
                        )
                    }
                    IconButton(
                        onClick = { activeToolSheet = ChatToolType.VIDEO_NOTE },
                        modifier = Modifier.testTag("chat_video_shortcut_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = "Video Note Shortcut",
                            tint = DeepEmerald
                        )
                    }
                }
            }

            // Animated YouTube Song / Video Direct-Send Loading Card
            AnimatedVisibility(
                visible = uiState.isProcessingTool,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    MediaSendingLoadingCard(
                        tool = uiState.activeMediaTool,
                        query = uiState.activeMediaQuery,
                        statusText = uiState.toolStatusText
                    )
                }
            }

            // Messages List Canvas
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { isPlusMenuOpen = false }
                    .testTag("chat_messages_list"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = DateChipBeige,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (uiState.activeChatMessages.isEmpty() && !uiState.isLoadingChat) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 36.dp, start = 16.dp, end = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = SurfacePureWhite.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(20.dp),
                                shadowElevation = 2.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(SoftSageTint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = DeepEmerald,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "Type .play <song name> to send an MP3 directly (no text chat), or tap + for YouTube Video, TikTok, Instagram, Facebook & Voice Notes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    items = uiState.activeChatMessages,
                    key = { it.id.ifBlank { "${it.timestamp}_${it.text.hashCode()}" } }
                ) { msg ->
                    AnimatedMessageBubbleRow(message = msg)
                }
            }

            // Live Animated ".play" Direct Song Mode Indicator Pill
            AnimatedVisibility(
                visible = isPlayCommand,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(DeepEmerald, FloatingMenuTeal))
                        )
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                        .testTag("chat_play_command_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = NeonMintAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.chat_play_mode_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextOnDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Bottom Composer Bar
            Surface(
                color = SurfacePureWhite,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated Circular Emerald + Button
                    IconButton(
                        onClick = { isPlusMenuOpen = !isPlusMenuOpen },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(plusBgColor)
                            .testTag("chat_plus_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Open tools menu",
                            tint = TextOnDark,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(plusRotation)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.chat_input_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceSubtleGray,
                            unfocusedContainerColor = SurfaceSubtleGray,
                            focusedBorderColor = if (isPlayCommand) OnlineMint else DeepEmerald.copy(alpha = 0.5f),
                            unfocusedBorderColor = DividerSubtle
                        )
                    )

                    Spacer(Modifier.width(6.dp))

                    if (messageInput.isBlank()) {
                        IconButton(
                            onClick = {
                                isPlusMenuOpen = false
                                activeToolSheet = ChatToolType.VOICE_NOTE
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.tool_voice_note),
                                tint = DeepEmerald,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    } else {
                        val sendContainerColor by animateColorAsState(
                            targetValue = if (isPlayCommand) DeepEmerald else WhatsAppGreen,
                            animationSpec = tween(220),
                            label = "send_btn_color"
                        )
                        IconButton(
                            onClick = {
                                val textToSend = messageInput.trim()
                                messageInput = ""
                                isPlusMenuOpen = false
                                onSendText(textToSend)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .scale(sendBtnScale)
                                .clip(CircleShape)
                                .background(sendContainerColor)
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = if (isPlayCommand) Icons.Rounded.MusicNote else Icons.AutoMirrored.Rounded.Send,
                                contentDescription = if (isPlayCommand) "Send song directly" else "Send message",
                                tint = if (isPlayCommand) NeonMintAccent else TextOnDark,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }
        }

        // Scrim when floating + menu is open
        if (isPlusMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { isPlusMenuOpen = false }
            )
        }

        // Floating Animated Emerald-Teal + Power Menu
        AnimatedVisibility(
            visible = isPlusMenuOpen,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.82f,
                transformOrigin = TransformOrigin(0.08f, 1f),
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(0.08f, 1f)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, bottom = 74.dp, end = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = FloatingMenuTeal,
                modifier = Modifier
                    .widthIn(min = 252.dp, max = 290.dp)
                    .heightIn(max = 432.dp)
                    .shadow(16.dp, RoundedCornerShape(22.dp))
                    .border(1.dp, NeonMintAccent.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                    .testTag("chat_plus_popup_menu")
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(listOf(DeepEmerald, FloatingMenuTeal))
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    val toolItems = listOf(
                        Triple(ChatToolType.VIDEO_NOTE, Icons.Rounded.Videocam, stringResource(R.string.tool_video_note)),
                        Triple(ChatToolType.VOICE_NOTE, Icons.Rounded.Mic, stringResource(R.string.tool_voice_note)),
                        Triple(ChatToolType.FAKE_LOCATION, Icons.Rounded.LocationOff, stringResource(R.string.tool_fake_location)),
                        Triple(ChatToolType.BUTTON_MESSAGES, Icons.Rounded.SmartButton, stringResource(R.string.tool_button_messages)),
                        Triple(ChatToolType.SONG, Icons.Rounded.MusicNote, stringResource(R.string.tool_song)),
                        Triple(ChatToolType.VIDEO, Icons.Rounded.OndemandVideo, stringResource(R.string.tool_video)),
                        Triple(ChatToolType.TIKTOK, Icons.Rounded.MusicVideo, stringResource(R.string.tool_tiktok)),
                        Triple(ChatToolType.FACEBOOK, Icons.Rounded.Facebook, stringResource(R.string.tool_facebook)),
                        Triple(ChatToolType.INSTAGRAM, Icons.Rounded.PhotoCameraBack, stringResource(R.string.tool_instagram)),
                        Triple(ChatToolType.DOWNLOADER, Icons.Rounded.DownloadForOffline, stringResource(R.string.tool_downloader))
                    )

                    toolItems.forEachIndexed { idx, (toolType, icon, label) ->
                        if (idx == 4) {
                            HorizontalDivider(
                                color = TextOnDark.copy(alpha = 0.16f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        PlusPopupMenuRow(
                            icon = icon,
                            label = label,
                            subtitle = if (toolType == ChatToolType.SONG) "or type .play" else null,
                            testTag = "plus_tool_${toolType.platformKey}",
                            onClick = {
                                isPlusMenuOpen = false
                                if (toolType == ChatToolType.BUTTON_MESSAGES) {
                                    onOpenButtonComposer()
                                } else {
                                    activeToolSheet = toolType
                                    if (toolType == ChatToolType.DOWNLOADER) {
                                        onSearchGiphy("")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Active Tool Bottom Sheets
    when (val tool = activeToolSheet) {
        ChatToolType.VIDEO_NOTE -> {
            VideoNoteSheet(
                isBusy = uiState.isProcessingTool,
                onDismiss = { activeToolSheet = null },
                onVideoSelected = { uri ->
                    onSendVideoNoteUri(uri) { activeToolSheet = null }
                }
            )
        }

        ChatToolType.VOICE_NOTE -> {
            VoiceNoteSheet(
                isBusy = uiState.isProcessingTool,
                onDismiss = { activeToolSheet = null },
                onAudioUriSelected = { uri ->
                    onSendVoiceNoteUri(uri) { activeToolSheet = null }
                },
                onRecordedVoiceReady = { file ->
                    onSendRecordedVoice(file) { activeToolSheet = null }
                }
            )
        }

        ChatToolType.FAKE_LOCATION -> {
            FakeLocationSheet(
                isBusy = uiState.isProcessingTool,
                onDismiss = { activeToolSheet = null },
                onSendLocation = { lat, lng, dur, label ->
                    onSendFakeLocation(lat, lng, dur, label) { activeToolSheet = null }
                }
            )
        }

        ChatToolType.SONG,
        ChatToolType.VIDEO,
        ChatToolType.TIKTOK,
        ChatToolType.FACEBOOK,
        ChatToolType.INSTAGRAM,
        ChatToolType.DOWNLOADER -> {
            MediaDownloadSheet(
                tool = tool,
                isBusy = uiState.isProcessingTool,
                activeQuery = uiState.activeMediaQuery,
                statusText = uiState.toolStatusText,
                giphyResults = uiState.giphyResults,
                isSearchingGiphy = uiState.isSearchingGiphy,
                onDismiss = { activeToolSheet = null },
                onSubmit = { query, caption ->
                    onDownloadAndSend(tool, query, caption) { activeToolSheet = null }
                },
                onSearchGiphy = onSearchGiphy,
                onSelectGiphy = { gif ->
                    onSendGiphy(gif) { activeToolSheet = null }
                }
            )
        }

        else -> Unit
    }
}

@Composable
private fun PlusPopupMenuRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(TextOnDark.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = NeonMintAccent,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = TextOnDark,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextOnDarkMuted
                )
            }
        }
    }
}

@Composable
private fun AnimatedMessageBubbleRow(message: ChatMessageItem) {
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
        )
    ) {
        MessageBubbleRow(message = message)
    }
}

@Composable
private fun MessageBubbleRow(message: ChatMessageItem) {
    val isMe = message.fromMe
    val bubbleColor = if (isMe) OutgoingMintBubble else IncomingWhiteBubble
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMe) 18.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 18.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            shadowElevation = 1.5.dp,
            modifier = Modifier.widthIn(min = 84.dp, max = 304.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                if (message.mediaType != "text" && message.mediaType.isNotBlank()) {
                    val (badgeIcon, badgeLabel) = when (message.mediaType.lowercase()) {
                        "ptv" -> Icons.Rounded.Videocam to "Video Note"
                        "audio", "voice", "song" -> Icons.Rounded.MusicNote to "Direct Audio MP3"
                        "video", "tiktok", "facebook" -> Icons.Rounded.PlayCircle to "Direct Video MP4"
                        "image", "instagram" -> Icons.Rounded.Image to "Direct Media"
                        "gif", "giphy" -> Icons.Rounded.GifBox to "GIF"
                        "location" -> Icons.Rounded.LocationOn to "Live Location"
                        "buttons" -> Icons.Rounded.SmartButton to "Interactive Buttons"
                        else -> Icons.Rounded.AttachFile to message.mediaType.uppercase()
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeepEmerald.copy(alpha = 0.11f))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(badgeIcon, contentDescription = null, tint = DeepEmerald, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(badgeLabel, style = MaterialTheme.typography.labelSmall, color = DeepEmerald, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }

                if (message.buttons.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    message.buttons.forEach { btnLabel ->
                        Surface(
                            color = SurfacePureWhite,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = btnLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = DeepEmerald,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatChatTime(message.timestamp),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    if (isMe) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (message.pending) Icons.Rounded.Schedule else Icons.Rounded.DoneAll,
                            contentDescription = null,
                            tint = if (message.pending) TextSecondary else ReadTickBlue,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
