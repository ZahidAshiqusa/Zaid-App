package com.zaidashiq.chat.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaidashiq.chat.R
import com.zaidashiq.chat.data.model.ChatMessageItem
import com.zaidashiq.chat.data.model.GiphyItem
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

    val listState = rememberLazyListState()
    val plusRotation by animateFloatAsState(
        targetValue = if (isPlusMenuOpen) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "plus_rotation"
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
            // Chat Top Header matching Screen 2 of attached mockup
            Surface(
                color = SurfacePureWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
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
                        size = 40.dp
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recipientName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (uiState.isLinked) stringResource(R.string.chat_online) else "Baileys standby",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isLinked) WhatsAppGreen else TextSecondary
                        )
                    }

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

            // Active Download/Send Progress Indicator Bar
            AnimatedVisibility(visible = uiState.isProcessingTool) {
                Surface(color = FloatingMenuTeal) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = OnlineMint,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = uiState.toolStatusText ?: "Processing media…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextOnDark
                        )
                    }
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
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (uiState.activeChatMessages.isEmpty() && !uiState.isLoadingChat) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp, start = 20.dp, end = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = SurfacePureWhite.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Tap the + button below to send Video Notes, Voice Notes, Fake Location, Button Messages, Songs, Videos, TikTok, Facebook, or Instagram media directly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(14.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(uiState.activeChatMessages, key = { it.id.ifBlank { "${it.timestamp}_${it.text.hashCode()}" } }) { msg ->
                    MessageBubbleRow(message = msg)
                }
            }

            // Bottom Composer Bar matching Screen 2 of attached mockup
            Surface(
                color = SurfacePureWhite,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular Emerald + Button
                    IconButton(
                        onClick = { isPlusMenuOpen = !isPlusMenuOpen },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(FloatingMenuTeal)
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
                                color = TextSecondary
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceSubtleGray,
                            unfocusedContainerColor = SurfaceSubtleGray,
                            focusedBorderColor = DeepEmerald.copy(alpha = 0.5f),
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
                        IconButton(
                            onClick = {
                                val textToSend = messageInput.trim()
                                messageInput = ""
                                isPlusMenuOpen = false
                                onSendText(textToSend)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(WhatsAppGreen)
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send message",
                                tint = TextOnDark,
                                modifier = Modifier.size(20.dp)
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

        // Floating Emerald-Teal + Power Menu anchored right above the + button (Screen 2 + all 10 tools!)
        AnimatedVisibility(
            visible = isPlusMenuOpen,
            enter = fadeIn() + scaleIn(
                initialScale = 0.85f,
                transformOrigin = TransformOrigin(0.1f, 1f),
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut() + scaleOut(
                targetScale = 0.9f,
                transformOrigin = TransformOrigin(0.1f, 1f)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, bottom = 72.dp, end = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = FloatingMenuTeal,
                modifier = Modifier
                    .widthIn(min = 248.dp, max = 286.dp)
                    .heightIn(max = 420.dp)
                    .shadow(12.dp, RoundedCornerShape(18.dp))
                    .testTag("chat_plus_popup_menu")
            ) {
                Column(
                    modifier = Modifier
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
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                        PlusPopupMenuRow(
                            icon = icon,
                            label = label,
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
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(TextOnDark.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TextOnDark,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = TextOnDark,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MessageBubbleRow(message: ChatMessageItem) {
    val isMe = message.fromMe
    val bubbleColor = if (isMe) OutgoingMintBubble else IncomingWhiteBubble
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMe) 16.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 16.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 84.dp, max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.mediaType != "text" && message.mediaType.isNotBlank()) {
                    val (badgeIcon, badgeLabel) = when (message.mediaType.lowercase()) {
                        "ptv" -> Icons.Rounded.Videocam to "Video Note"
                        "audio", "voice", "song" -> Icons.Rounded.MusicNote to "Audio File"
                        "video", "tiktok", "facebook" -> Icons.Rounded.PlayCircle to "Video File"
                        "image", "instagram" -> Icons.Rounded.Image to "Media File"
                        "gif", "giphy" -> Icons.Rounded.GifBox to "GIF"
                        "location" -> Icons.Rounded.LocationOn to "Live Location"
                        "buttons" -> Icons.Rounded.SmartButton to "Interactive Buttons"
                        else -> Icons.Rounded.AttachFile to message.mediaType.uppercase()
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DeepEmerald.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
