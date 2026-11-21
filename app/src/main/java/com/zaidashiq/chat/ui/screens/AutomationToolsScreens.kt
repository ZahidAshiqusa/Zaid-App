package com.zaidashiq.chat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zaidashiq.chat.data.model.AutoReactConfigPayload
import com.zaidashiq.chat.data.model.VaultSettingsPayload
import com.zaidashiq.chat.data.network.BackendConfig
import com.zaidashiq.chat.ui.theme.*
import com.zaidashiq.chat.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = SurfaceSubtleGray,
        topBar = {
            TopAppBar(
                title = { Text(title, color = TextOnDark, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tool_back_button")) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextOnDark)
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepEmerald)
            )
        },
        content = content
    )
}

@Composable
fun VaultScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateSettings: (VaultSettingsPayload) -> Unit,
    onForwardItem: (String) -> Unit,
    onClearVault: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    ToolScaffold(
        title = "Anti-Delete & View-Once Vault",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh vault", tint = TextOnDark)
            }
            if (uiState.vaultItems.isNotEmpty()) {
                IconButton(onClick = onClearVault) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear vault", tint = TextOnDark)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Protection Toggles", style = MaterialTheme.typography.titleMedium, color = DeepEmerald)
                        ToggleRow(
                            title = "Anti-Delete Message Recovery",
                            subtitle = "Save messages deleted by senders",
                            checked = uiState.vaultSettings.antiDeleteEnabled,
                            onCheckedChange = {
                                onUpdateSettings(uiState.vaultSettings.copy(antiDeleteEnabled = it))
                            }
                        )
                        ToggleRow(
                            title = "View-Once Media Capture",
                            subtitle = "Automatically archive View-Once photos & videos",
                            checked = uiState.vaultSettings.viewOnceEnabled,
                            onCheckedChange = {
                                onUpdateSettings(uiState.vaultSettings.copy(viewOnceEnabled = it))
                            }
                        )
                        ToggleRow(
                            title = "Auto-Forward to Personal DM",
                            subtitle = "Send captured items straight to your own chat",
                            checked = uiState.vaultSettings.autoForwardToOwner,
                            onCheckedChange = {
                                onUpdateSettings(uiState.vaultSettings.copy(autoForwardToOwner = it))
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Recovered Items (${uiState.vaultItems.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
            }

            if (uiState.vaultItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Shield,
                        title = "Vault is empty",
                        subtitle = "Deleted messages and View-Once media intercepted by your Railway Baileys engine appear here.",
                        actionLabel = "Refresh Vault",
                        onAction = onRefresh
                    )
                }
            } else {
                items(uiState.vaultItems, key = { it.id }) { item ->
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = item.senderName.ifBlank { "+${item.senderNumber}" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DeepEmerald
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.text.ifBlank { "[${item.mediaType.uppercase()} Media]" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${item.type.uppercase()} • ${formatChatTime(item.timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            IconButton(onClick = { onForwardItem(item.id) }) {
                                Icon(Icons.Rounded.ForwardToInbox, contentDescription = "Forward to DM", tint = DeepEmerald)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutoStatusScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onSave: (AutoReactConfigPayload) -> Unit
) {
    LaunchedEffect(Unit) { onLoad() }

    var enabled by remember(uiState.autoReactConfig) { mutableStateOf(uiState.autoReactConfig.enabled) }
    var autoView by remember(uiState.autoReactConfig) { mutableStateOf(uiState.autoReactConfig.autoViewStatus) }
    var autoReact by remember(uiState.autoReactConfig) { mutableStateOf(uiState.autoReactConfig.autoReactStatus) }
    var autoReply by remember(uiState.autoReactConfig) { mutableStateOf(uiState.autoReactConfig.autoReplyStatus) }
    var emoji by remember(uiState.autoReactConfig) { mutableStateOf(uiState.autoReactConfig.emoji) }
    var replyText by remember(uiState.autoReactConfig) { mutableStateOf(uiState.autoReactConfig.replyMessage) }

    ToolScaffold(title = "Auto-Status View & React", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToggleRow("Master Auto-Status Engine", "Run automatically on Railway 24/7", enabled) { enabled = it }
                    ToggleRow("Auto-View Statuses", "Silently mark contacts' statuses as viewed", autoView) { autoView = it }
                    ToggleRow("Auto-React on Statuses", "Send an instant reaction emoji", autoReact) { autoReact = it }
                    ToggleRow("Auto-Reply on Statuses", "Reply automatically with a custom message", autoReply) { autoReply = it }

                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Reaction Emoji") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Auto-Reply Message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            onSave(
                                AutoReactConfigPayload(
                                    enabled = enabled,
                                    autoViewStatus = autoView,
                                    autoReactStatus = autoReact,
                                    autoReplyStatus = autoReply,
                                    emoji = emoji.ifBlank { "💚" },
                                    replyMessage = replyText
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_auto_status_button"),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                    ) {
                        Text("Save Auto-Status Settings")
                    }
                }
            }
        }
    }
}

@Composable
fun FullProfilePicScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onApply: (targetType: String, groupJid: String, uri: Uri) -> Unit
) {
    var targetType by remember { mutableStateOf("personal") }
    var selectedGroupJid by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    ToolScaffold(title = "Full Profile Picture Tool", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No-Crop Full HD WhatsApp DP", style = MaterialTheme.typography.titleMedium, color = DeepEmerald)
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = targetType == "personal",
                            onClick = { targetType = "personal" },
                            label = { Text("Personal DP") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = targetType == "group",
                            onClick = { targetType = "group" },
                            label = { Text("Group DP") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (targetType == "group") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = selectedGroupJid,
                            onValueChange = { selectedGroupJid = it },
                            label = { Text("Target Group JID (e.g. 12345@g.us)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(SoftSageTint),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pickedUri != null) {
                            AsyncImage(
                                model = pickedUri,
                                contentDescription = "Selected profile picture",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                tint = DeepEmerald,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("full_pp_pick_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = DeepEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Photo (Photo Picker)", color = DeepEmerald)
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            pickedUri?.let { uri -> onApply(targetType, selectedGroupJid, uri) }
                        },
                        enabled = pickedUri != null && !uiState.isProcessingTool,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("full_pp_apply_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                    ) {
                        if (uiState.isProcessingTool) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextOnDark, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Applying Full DP…")
                        } else {
                            Text("Apply Full Profile Picture")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelReactScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddRule: (url: String, name: String, emojis: String) -> Unit,
    onDeleteRule: (String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    var channelUrl by remember { mutableStateOf("") }
    var channelName by remember { mutableStateOf("") }
    var emojis by remember { mutableStateOf("❤️ 🔥 👍") }

    ToolScaffold(title = "Channel Auto-React Rules", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add New Channel Rule", style = MaterialTheme.typography.titleMedium, color = DeepEmerald)
                        OutlinedTextField(
                            value = channelUrl,
                            onValueChange = { channelUrl = it },
                            label = { Text("Channel Invite URL or Newsletter JID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("channel_rule_url_input")
                        )
                        OutlinedTextField(
                            value = channelName,
                            onValueChange = { channelName = it },
                            label = { Text("Channel Label (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = emojis,
                            onValueChange = { emojis = it },
                            label = { Text("Reaction Emojis (space separated)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (channelUrl.isNotBlank()) {
                                    onAddRule(channelUrl, channelName, emojis)
                                    channelUrl = ""
                                    channelName = ""
                                }
                            },
                            enabled = channelUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("channel_rule_add_button"),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                        ) {
                            Text("Save Channel Auto-React Rule")
                        }
                    }
                }
            }

            items(uiState.channels, key = { it.id.ifBlank { it.jid } }) { rule ->
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(rule.emojis.joinToString(" "), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { onDeleteRule(rule.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete rule", tint = ErrorCoral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionSettingsScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onRefreshStatus: () -> Unit,
    onTriggerCloudSync: () -> Unit,
    onRelinkPhone: () -> Unit
) {
    ToolScaffold(title = "Session & Connection", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Railway Baileys Bridge", style = MaterialTheme.typography.titleMedium, color = DeepEmerald)

                    StatusInfoRow(
                        label = "RAILWAY_BACKEND_URL",
                        value = if (BackendConfig.isRailwayConfigured) BackendConfig.rawRailwayUrl else "Not set in AI Studio Secrets",
                        ok = BackendConfig.isRailwayConfigured
                    )
                    StatusInfoRow(
                        label = "Backend Health",
                        value = if (uiState.backendHealthy) "Online (${uiState.backendUptime.ifBlank { "Active" }})" else "Offline / Waiting",
                        ok = uiState.backendHealthy
                    )
                    StatusInfoRow(
                        label = "Linked WhatsApp",
                        value = if (uiState.isLinked && uiState.linkedPhone.isNotBlank()) "+${uiState.linkedPhone}" else "Not Linked",
                        ok = uiState.isLinked
                    )
                    StatusInfoRow(
                        label = "Supabase Config",
                        value = if (BackendConfig.isSupabaseConfigured) "Configured via Secrets" else "Optional — Set in Secrets & Railway",
                        ok = BackendConfig.isSupabaseConfigured
                    )
                    StatusInfoRow(
                        label = "Giphy API Key",
                        value = if (BackendConfig.isGiphyConfigured) "Configured via Secrets" else "Optional — Set GIPHY_API_KEY in Secrets",
                        ok = BackendConfig.isGiphyConfigured
                    )

                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onRefreshStatus,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Test Bridge", color = DeepEmerald)
                        }
                        Button(
                            onClick = onTriggerCloudSync,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepEmerald)
                        ) {
                            Text("Sync Cloud")
                        }
                    }

                    Button(
                        onClick = onRelinkPhone,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("relink_whatsapp_button"),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = FloatingMenuTeal)
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pair / Switch WhatsApp Number")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = SurfacePureWhite, checkedTrackColor = WhatsAppGreen)
        )
    }
}

@Composable
private fun StatusInfoRow(
    label: String,
    value: String,
    ok: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceSubtleGray)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (ok) OnlineMint else WarningAmber)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}
