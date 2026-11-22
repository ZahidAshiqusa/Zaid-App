package com.zaidashiq.chat.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ForwardToInbox
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zaidashiq.chat.data.model.AutoReactConfigPayload
import com.zaidashiq.chat.data.model.VaultSettingsPayload
import com.zaidashiq.chat.data.network.BackendConfig
import com.zaidashiq.chat.ui.components.AnimatedAuroraEmeraldBackground
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
        containerColor = ObsidianBase,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            ) {
                AnimatedAuroraEmeraldBackground(modifier = Modifier.matchParentSize())
                TopAppBar(
                    title = {
                        Text(
                            title,
                            color = TextOnDark,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("tool_back_button")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = NeonMintAccent)
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
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
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh vault", tint = NeonMintAccent)
            }
            if (uiState.vaultItems.isNotEmpty()) {
                IconButton(onClick = onClearVault) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear vault", tint = ErrorCoral)
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
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = GlassCardSurface,
                    border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.65f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Protection Toggles", style = MaterialTheme.typography.titleMedium, color = NeonMintAccent, fontWeight = FontWeight.Bold)
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
                    color = TextOnDarkMuted
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
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = GlassCardSurface,
                        border = BorderStroke(0.8.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = item.senderName.ifBlank { "+${item.senderNumber}" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NeonMintAccent,
                                    fontWeight = FontWeight.SemiBold
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
                                Icon(Icons.AutoMirrored.Rounded.ForwardToInbox, contentDescription = "Forward to DM", tint = NeonMintAccent)
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
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = GlassCardSurface,
                border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.65f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow("Master Auto-Status Engine", "Run automatically on Railway 24/7", enabled) { enabled = it }
                    ToggleRow("Auto-View Statuses", "Silently mark contacts' statuses as viewed", autoView) { autoView = it }
                    ToggleRow("Auto-React on Statuses", "Send an instant reaction emoji", autoReact) { autoReact = it }
                    ToggleRow("Auto-Reply on Statuses", "Reply automatically with a custom message", autoReply) { autoReply = it }

                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Reaction Emoji") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Auto-Reply Message") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(4.dp))
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGlow,
                            contentColor = ObsidianBase
                        )
                    ) {
                        Text("Save Auto-Status Settings", fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current
    var targetType by remember { mutableStateOf("personal") }
    var selectedGroupJid by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedResolution by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
            pickedResolution = try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    "${opts.outWidth} × ${opts.outHeight} px • 100% Same Aspect (.fdp)"
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    ToolScaffold(title = "Full DP (.fdp 100% Uncropped)", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = GlassCardSurface,
                border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "100% Same-Resolution Full DP (.fdp)",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonMintAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Applies your exact full picture directly via WhatsApp's w:profile:picture IQ protocol — zero square cropping and zero side bars.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))

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
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ObsidianSurface)
                            .border(1.dp, GlassBorderEmerald.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pickedUri != null) {
                            AsyncImage(
                                model = pickedUri,
                                contentDescription = "Selected full resolution profile picture",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                tint = NeonMintAccent,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    if (!pickedResolution.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = SoftSagePill,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, NeonMintAccent.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = pickedResolution.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonMintAccent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        border = BorderStroke(1.dp, GlassBorderEmerald),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("full_pp_pick_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = NeonMintAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Photo (Original Resolution)", color = NeonMintAccent)
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            pickedUri?.let { uri -> onApply(targetType, selectedGroupJid, uri) }
                        },
                        enabled = pickedUri != null && !uiState.isProcessingTool,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("full_pp_apply_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGlow,
                            contentColor = ObsidianBase,
                            disabledContainerColor = GlassCardElevated,
                            disabledContentColor = TextSecondary
                        )
                    ) {
                        if (uiState.isProcessingTool) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ObsidianBase, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Applying Full .fdp…", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Set 100% Full Uncropped DP", fontWeight = FontWeight.Bold)
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
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = GlassCardSurface,
                    border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.65f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Instant Fast Channel Auto-React", style = MaterialTheme.typography.titleMedium, color = NeonMintAccent, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Paste any WhatsApp Channel invite link (whatsapp.com/channel/...) or @newsletter JID. Auto-follows and reacts instantaneously to every new post.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = channelUrl,
                            onValueChange = { channelUrl = it },
                            label = { Text("Channel Invite URL or Newsletter JID") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("channel_rule_url_input")
                        )
                        OutlinedTextField(
                            value = channelName,
                            onValueChange = { channelName = it },
                            label = { Text("Channel Label (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = emojis,
                            onValueChange = { emojis = it },
                            label = { Text("Reaction Emojis (space separated)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGlow,
                                contentColor = ObsidianBase
                            )
                        ) {
                            Text("Save Channel Auto-React Rule", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(uiState.channels, key = { it.id.ifBlank { it.jid } }) { rule ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GlassCardSurface,
                    border = BorderStroke(0.8.dp, GlassBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(rule.emojis.joinToString(" "), style = MaterialTheme.typography.bodyMedium, color = NeonMintAccent)
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
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = GlassCardSurface,
                border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.65f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Railway Baileys Bridge", style = MaterialTheme.typography.titleMedium, color = NeonMintAccent, fontWeight = FontWeight.Bold)

                    StatusInfoRow(
                        label = "Backend Service",
                        value = if (BackendConfig.isRailwayConfigured) BackendConfig.rawRailwayUrl else "Service Not Available",
                        ok = BackendConfig.isRailwayConfigured
                    )
                    StatusInfoRow(
                        label = "Backend Health",
                        value = if (uiState.backendHealthy) "Online (${uiState.backendUptime.ifBlank { "Active" }})" else "Service Not Available",
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
                    StatusInfoRow(
                        label = "Timezone & Region",
                        value = BackendConfig.timezone,
                        ok = true
                    )
                    StatusInfoRow(
                        label = "PostgreSQL (DATABASE_URL)",
                        value = "Managed server-side in Railway Variables (never embedded in APK)",
                        ok = uiState.backendHealthy
                    )

                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onRefreshStatus,
                            border = BorderStroke(1.dp, GlassBorderEmerald),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Test Bridge", color = NeonMintAccent)
                        }
                        Button(
                            onClick = onTriggerCloudSync,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGlow,
                                contentColor = ObsidianBase
                            )
                        ) {
                            Text("Sync Cloud", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onRelinkPhone,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("relink_whatsapp_button"),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SoftSagePill,
                            contentColor = NeonMintAccent
                        ),
                        border = BorderStroke(1.dp, GlassBorderEmerald)
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pair / Switch WhatsApp Number", fontWeight = FontWeight.SemiBold)
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
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBase,
                checkedTrackColor = EmeraldGlow,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassCardElevated,
                uncheckedBorderColor = GlassBorder
            )
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
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(0.8.dp, GlassBorder, RoundedCornerShape(14.dp))
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
