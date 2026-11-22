package com.zaidashiq.chat.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    recipientPhone: String,
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
    onSendFakeLocation: (Double, Double, Int, String, () -> Unit) -> Unit,
    onLookupNumber: (String) -> Unit,
    onClearNumberLookup: () -> Unit,
    onLoadOlderMessages: () -> Unit
) {
    var messageInput by remember { mutableStateOf("") }
    var isPlusMenuOpen by remember { mutableStateOf(false) }
    var activeToolSheet by remember { mutableStateOf<ChatToolType?>(null) }
    var showNumberDetailsSheet by remember { mutableStateOf(false) }

    val isSongLink by remember(messageInput) {
        derivedStateOf {
            com.zaidashiq.chat.ui.viewmodel.isDirectSongLink(messageInput)
        }
    }
    val isPlayCommand by remember(messageInput, isSongLink) {
        derivedStateOf {
            val trimmed = messageInput.trimStart()
            isSongLink ||
                trimmed.startsWith(".play", ignoreCase = true) ||
                trimmed.startsWith(".song", ignoreCase = true) ||
                trimmed.startsWith(".video", ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()
    val plusRotation by animateFloatAsState(
        targetValue = if (isPlusMenuOpen) 45f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "plus_rotation"
    )
    val plusBgColor by animateColorAsState(
        targetValue = if (isPlusMenuOpen) NeonMintAccent else EmeraldGlow,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "plus_bg"
    )

    LaunchedEffect(uiState.activeChatMessages.size) {
        if (uiState.activeChatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.activeChatMessages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBase)
            .imePadding()
    ) {
        // Full-bleed bespoke obsidian-emerald geometric silk chat wallpaper
        Image(
            painter = painterResource(id = R.drawable.img_bg_chat_wallpaper),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Subtle dark veil ensuring crisp message contrast across the entire chat canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBase.copy(alpha = 0.28f))
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Frosted Obsidian-Glass Chat Top Header: Receiver WhatsApp Last Seen Time + Number Details Button
            Surface(
                color = ObsidianSurface.copy(alpha = 0.92f),
                shadowElevation = 6.dp
            ) {
                Column {
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
                                tint = NeonMintAccent,
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

                        // Receiver Name + Receiver WhatsApp Last Seen time (own presence stays permanently hidden/offline)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recipientName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!recipientJid.endsWith("@g.us") && uiState.activeChatLastSeen.isNotBlank()) {
                                Text(
                                    text = uiState.activeChatLastSeen,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonMintAccent.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("chat_receiver_last_seen")
                                )
                            }
                        }

                        // Dedicated Number Details Button (Truecaller + SIM Database + WhatsApp Profile)
                        IconButton(
                            onClick = {
                                isPlusMenuOpen = false
                                showNumberDetailsSheet = true
                            },
                            modifier = Modifier.testTag("chat_number_details_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonSearch,
                                contentDescription = stringResource(R.string.tool_number_details),
                                tint = NeonMintAccent
                            )
                        }

                        IconButton(
                            onClick = { activeToolSheet = ChatToolType.VOICE_NOTE },
                            modifier = Modifier.testTag("chat_voice_shortcut_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Voice Note Shortcut",
                                tint = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = { activeToolSheet = ChatToolType.VIDEO_NOTE },
                            modifier = Modifier.testTag("chat_video_shortcut_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Video Note Shortcut",
                                tint = TextPrimary
                            )
                        }
                    }
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.7f), thickness = 0.8.dp)
                }
            }

            // Messages List Canvas (media processing & sending loaders render directly inside the chat stream)
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
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedButton(
                            onClick = onLoadOlderMessages,
                            enabled = !uiState.isLoadingOlderChats,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, GlassBorderEmerald),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = GlassCardSurface.copy(alpha = 0.82f),
                                contentColor = NeonMintAccent
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("chat_load_older_button")
                        ) {
                            if (uiState.isLoadingOlderChats) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = NeonMintAccent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Loading older messages…", style = MaterialTheme.typography.labelSmall, color = NeonMintAccent)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = NeonMintAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Load Older Messages", style = MaterialTheme.typography.labelSmall, color = NeonMintAccent)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Surface(
                            color = DateChipBeige,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(0.8.dp, GlassBorder)
                        ) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextOnDarkMuted,
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
                                color = GlassCardSurface.copy(alpha = 0.86f),
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.7f)),
                                shadowElevation = 4.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(SoftSagePill)
                                            .border(1.dp, NeonMintAccent.copy(alpha = 0.35f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = NeonMintAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Paste any YouTube, Spotify, or SoundCloud link — or type .play <song name> — to download & send direct MP3 audio right inside chat, or tap + for all tools.",
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

            // Live ".play" / Song Link Direct Media Mode Indicator Pill
            AnimatedVisibility(
                visible = isPlayCommand,
                enter = expandVertically(tween(180, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(160, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(DeepGlassTeal, FloatingMenuTeal))
                        )
                        .border(0.5.dp, NeonMintAccent.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
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
                        text = if (isSongLink) {
                            stringResource(R.string.chat_song_link_badge)
                        } else {
                            stringResource(R.string.chat_play_mode_badge)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextOnDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Floating Obsidian-Glass Bottom Composer Bar
            Surface(
                color = ObsidianSurface.copy(alpha = 0.94f),
                shadowElevation = 8.dp
            ) {
                Column {
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.65f), thickness = 0.8.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 9.dp),
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
                                tint = ObsidianBase,
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
                                focusedContainerColor = GlassCardElevated,
                                unfocusedContainerColor = GlassCardSurface,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = if (isPlayCommand) NeonMintAccent else EmeraldGlow.copy(alpha = 0.7f),
                                unfocusedBorderColor = GlassBorder,
                                cursorColor = NeonMintAccent
                            )
                        )

                        Spacer(Modifier.width(8.dp))

                        if (messageInput.isBlank()) {
                            IconButton(
                                onClick = {
                                    isPlusMenuOpen = false
                                    activeToolSheet = ChatToolType.VOICE_NOTE
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(GlassCardElevated)
                                    .border(1.dp, GlassBorder, CircleShape)
                                    .testTag("chat_mic_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = stringResource(R.string.tool_voice_note),
                                    tint = NeonMintAccent,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        } else {
                            val sendContainerColor by animateColorAsState(
                                targetValue = if (isPlayCommand) NeonMintAccent else EmeraldGlow,
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
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
                                    .clip(CircleShape)
                                    .background(sendContainerColor)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlayCommand) Icons.Rounded.MusicNote else Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = if (isPlayCommand) "Send song directly" else "Send message",
                                    tint = ObsidianBase,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
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
                    .background(ObsidianBase.copy(alpha = 0.35f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { isPlusMenuOpen = false }
            )
        }

        // Floating Obsidian-Emerald Glass + Power Menu (calm fade + smooth slide)
        AnimatedVisibility(
            visible = isPlusMenuOpen,
            enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) + slideInVertically(
                initialOffsetY = { 24 },
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(140, easing = FastOutSlowInEasing)) + slideOutVertically(
                targetOffsetY = { 16 },
                animationSpec = tween(150, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, bottom = 76.dp, end = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = GlassCardSurface,
                modifier = Modifier
                    .widthIn(min = 256.dp, max = 294.dp)
                    .heightIn(max = 456.dp)
                    .shadow(18.dp, RoundedCornerShape(24.dp))
                    .border(1.dp, NeonMintAccent.copy(alpha = 0.32f), RoundedCornerShape(24.dp))
                    .testTag("chat_plus_popup_menu")
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0A2B27), GlassCardSurface, ObsidianSurface)
                            )
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    // Quick Number Details item right inside + menu
                    PlusPopupMenuRow(
                        icon = Icons.Rounded.PersonSearch,
                        label = stringResource(R.string.tool_number_details),
                        subtitle = "Truecaller & SIM DB",
                        testTag = "plus_tool_number_details",
                        onClick = {
                            isPlusMenuOpen = false
                            showNumberDetailsSheet = true
                        }
                    )
                    HorizontalDivider(
                        color = GlassBorder.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

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
                                color = GlassBorder.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        PlusPopupMenuRow(
                            icon = icon,
                            label = label,
                            subtitle = when (toolType) {
                                ChatToolType.SONG -> "MP3 + buttons (.play / link)"
                                ChatToolType.VIDEO -> "MP4 + buttons (.video)"
                                else -> null
                            },
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

    // Number Details Bottom Sheet (Truecaller + SIM Database + WhatsApp Profile)
    if (showNumberDetailsSheet) {
        NumberDetailsSheet(
            initialNumber = recipientPhone.ifBlank { recipientJid.substringBefore("@") },
            isLoading = uiState.isLookingUpNumber,
            result = uiState.numberLookupResult,
            errorMessage = uiState.numberLookupError,
            onSearch = onLookupNumber,
            onDismiss = {
                showNumberDetailsSheet = false
                onClearNumberLookup()
            }
        )
    }

    // Active Tool Bottom Sheets (close immediately on action so loading renders inside chat)
    when (val tool = activeToolSheet) {
        ChatToolType.VIDEO_NOTE -> {
            VideoNoteSheet(
                isBusy = false,
                onDismiss = { activeToolSheet = null },
                onVideoSelected = { uri ->
                    activeToolSheet = null
                    onSendVideoNoteUri(uri) {}
                }
            )
        }

        ChatToolType.VOICE_NOTE -> {
            VoiceNoteSheet(
                isBusy = false,
                onDismiss = { activeToolSheet = null },
                onAudioUriSelected = { uri ->
                    activeToolSheet = null
                    onSendVoiceNoteUri(uri) {}
                },
                onRecordedVoiceReady = { file ->
                    activeToolSheet = null
                    onSendRecordedVoice(file) {}
                }
            )
        }

        ChatToolType.FAKE_LOCATION -> {
            FakeLocationSheet(
                isBusy = false,
                onDismiss = { activeToolSheet = null },
                onSendLocation = { lat, lng, dur, label ->
                    activeToolSheet = null
                    onSendFakeLocation(lat, lng, dur, label) {}
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
                isBusy = false,
                activeQuery = "",
                statusText = null,
                giphyResults = uiState.giphyResults,
                isSearchingGiphy = uiState.isSearchingGiphy,
                onDismiss = { activeToolSheet = null },
                onSubmit = { query, caption ->
                    activeToolSheet = null
                    onDownloadAndSend(tool, query, caption) {}
                },
                onSearchGiphy = onSearchGiphy,
                onSelectGiphy = { gif ->
                    activeToolSheet = null
                    onSendGiphy(gif) {}
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
                .size(38.dp)
                .clip(CircleShape)
                .background(SoftSagePill)
                .border(1.dp, NeonMintAccent.copy(alpha = 0.30f), CircleShape),
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
        enter = fadeIn(tween(170, easing = FastOutSlowInEasing)) + slideInVertically(
            initialOffsetY = { 12 },
            animationSpec = tween(170, easing = FastOutSlowInEasing)
        )
    ) {
        MessageBubbleRow(message = message)
    }
}

@Composable
private fun MessageBubbleRow(message: ChatMessageItem) {
    val isMe = message.fromMe
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isMe) 20.dp else 5.dp,
        bottomEnd = if (isMe) 5.dp else 20.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 90.dp, max = 308.dp)
                .shadow(4.dp, bubbleShape)
                .clip(bubbleShape)
                .then(
                    if (isMe) {
                        Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(OutgoingBubbleStart, OutgoingBubbleEnd)
                                )
                            )
                            .border(1.dp, NeonMintAccent.copy(alpha = 0.28f), bubbleShape)
                    } else {
                        Modifier
                            .background(IncomingWhiteBubble)
                            .border(1.dp, GlassBorder.copy(alpha = 0.85f), bubbleShape)
                    }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                val isProcessingMedia = message.pending && message.mediaType != "text" && message.mediaType.isNotBlank()

                if (message.mediaType != "text" && message.mediaType.isNotBlank()) {
                    val (badgeIcon, badgeLabel) = when (message.mediaType.lowercase()) {
                        "ptv" -> Icons.Rounded.Videocam to "Video Note"
                        "voice" -> Icons.Rounded.Mic to "Voice Note"
                        "audio", "song" -> Icons.Rounded.MusicNote to "Direct Audio MP3"
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
                            .background(
                                if (isMe) ObsidianBase.copy(alpha = 0.26f) else SoftSagePill.copy(alpha = 0.85f)
                            )
                            .border(
                                0.8.dp,
                                NeonMintAccent.copy(alpha = if (isMe) 0.35f else 0.28f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isProcessingMedia) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = NeonMintAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(badgeIcon, contentDescription = null, tint = NeonMintAccent, modifier = Modifier.size(15.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isProcessingMedia) "Processing $badgeLabel" else badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMintAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextOnDark
                    )
                }

                if (isProcessingMedia) {
                    Spacer(Modifier.height(9.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(50))
                            .testTag("in_chat_media_processing_loader"),
                        color = NeonMintAccent,
                        trackColor = ObsidianBase.copy(alpha = 0.35f)
                    )
                }

                if (message.buttons.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    message.buttons.forEach { btnLabel ->
                        Surface(
                            color = ObsidianBase.copy(alpha = 0.32f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.8.dp, NeonMintAccent.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = btnLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonMintAccent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatChatTime(message.timestamp),
                        fontSize = 11.sp,
                        color = if (isMe) TextOnDarkMuted else TextSecondary
                    )
                    if (isMe) {
                        Spacer(Modifier.width(4.dp))
                        // Authentic WhatsApp delivery ticks:
                        // pending / status <= 1 -> Clock (sending)
                        // status == 2 -> Single gray tick (sent to server; receiver is offline!)
                        // status == 3 -> Double gray tick (delivered to receiver device)
                        // status >= 4 -> Double blue tick (read/played)
                        val (tickIcon, tickTint, tickDesc) = when {
                            message.pending || message.status <= 1 -> Triple(
                                Icons.Rounded.Schedule,
                                TextOnDarkMuted,
                                "Sending"
                            )
                            message.status == 2 -> Triple(
                                Icons.Rounded.Check,
                                TextOnDarkMuted,
                                "Sent (receiver offline)"
                            )
                            message.status == 3 -> Triple(
                                Icons.Rounded.DoneAll,
                                TextOnDarkMuted,
                                "Delivered"
                            )
                            else -> Triple(
                                Icons.Rounded.DoneAll,
                                ReadTickBlue,
                                "Read"
                            )
                        }
                        Icon(
                            imageVector = tickIcon,
                            contentDescription = tickDesc,
                            tint = tickTint,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
