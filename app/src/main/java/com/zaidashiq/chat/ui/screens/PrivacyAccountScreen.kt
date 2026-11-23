package com.zaidashiq.chat.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zaidashiq.chat.data.model.GhostPrivacyPayload
import com.zaidashiq.chat.data.model.NativePrivacyPayload
import com.zaidashiq.chat.ui.theme.*
import com.zaidashiq.chat.ui.viewmodel.MainUiState

@Composable
fun PrivacyAccountScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateAbout: (String) -> Unit,
    onUpdateGhostPrivacy: (GhostPrivacyPayload) -> Unit,
    onUpdateNativePrivacy: (NativePrivacyPayload) -> Unit,
    onOpenFullDp: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenAutoStatus: () -> Unit,
    onTriggerCloudSync: () -> Unit,
    onExportFullBackup: () -> Unit,
    onClearAllChats: () -> Unit,
    onDismissBackupDialog: () -> Unit
) {
    LaunchedEffect(Unit) { onLoad() }

    val profile = uiState.accountProfile
    val ghost = uiState.ghostPrivacy
    val nativePrivacy = uiState.privacySettings

    var nameInput by remember(profile.pushName) { mutableStateOf(profile.pushName) }
    var aboutInput by remember(profile.about) { mutableStateOf(profile.about) }

    Scaffold(
        containerColor = ObsidianBase,
        topBar = {
            Surface(
                color = ObsidianSurface.copy(alpha = 0.94f),
                shadowElevation = 6.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("privacy_account_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = NeonMintAccent
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Account, Ghost Privacy & Backup",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Baileys & FM-WhatsApp Stealth Suite",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonMintAccent.copy(alpha = 0.85f)
                            )
                        }
                        if (uiState.isUpdatingSettings) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(20.dp),
                                color = NeonMintAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = onLoad,
                                modifier = Modifier.testTag("privacy_account_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh settings",
                                    tint = NeonMintAccent
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.7f), thickness = 0.8.dp)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(ObsidianSurface, ObsidianBase, ObsidianBase))
                )
                .padding(innerPadding)
                .testTag("privacy_account_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. WhatsApp Account Profile Card
            item {
                ObsidianSettingsCard(
                    title = "WhatsApp Account Profile",
                    subtitle = if (profile.phone.isNotBlank()) "+${profile.phone}" else "Linked Baileys Account",
                    icon = Icons.Rounded.ManageAccounts
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (profile.profilePicUrl.isNotBlank()) {
                            AsyncImage(
                                model = profile.profilePicUrl,
                                contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, NeonMintAccent.copy(alpha = 0.6f), CircleShape)
                            )
                        } else {
                            ContactAvatar(
                                name = profile.pushName.ifBlank { uiState.linkedPhone },
                                isGroup = false,
                                showOnlineDot = false,
                                size = 56.dp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = profile.pushName.ifBlank { "Aujla Chat App" },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = profile.about.ifBlank { "Tap below to set your Bio / About" },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = onOpenFullDp,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SoftSagePill,
                                contentColor = NeonMintAccent
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("account_open_fdp_button")
                        ) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(".fdp DP", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Change Display Name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("WhatsApp Display Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("account_name_input"),
                            colors = obsidianFieldColors()
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onUpdateName(nameInput) },
                            enabled = !uiState.isUpdatingSettings && nameInput.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGlow,
                                contentColor = ObsidianBase
                            ),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("account_save_name_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Change Bio / About
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = aboutInput,
                            onValueChange = { aboutInput = it },
                            label = { Text("WhatsApp Bio / About") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("account_about_input"),
                            colors = obsidianFieldColors()
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onUpdateAbout(aboutInput) },
                            enabled = !uiState.isUpdatingSettings && aboutInput.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGlow,
                                contentColor = ObsidianBase
                            ),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("account_save_about_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. FM-WhatsApp Ghost Privacy Mode Card
            item {
                ObsidianSettingsCard(
                    title = "FM-WhatsApp Ghost Privacy",
                    subtitle = "Stay completely invisible while using this app",
                    icon = Icons.Rounded.VisibilityOff
                ) {
                    GhostToggleRow(
                        title = "Freeze Online Presence (Always Offline)",
                        description = "Never appear online; contacts only see your real main WhatsApp Last Seen time.",
                        checked = ghost.freezeOnlinePresence,
                        testTag = "ghost_freeze_online_switch",
                        onCheckedChange = {
                            onUpdateGhostPrivacy(ghost.copy(freezeOnlinePresence = it))
                        }
                    )
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))

                    GhostToggleRow(
                        title = "Ghost Status Viewing (Hide Seen)",
                        description = "Capture & view contacts' statuses silently without appearing in their viewers list.",
                        checked = ghost.ghostStatusView,
                        testTag = "ghost_status_view_switch",
                        onCheckedChange = {
                            onUpdateGhostPrivacy(ghost.copy(ghostStatusView = it))
                        }
                    )
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))

                    GhostToggleRow(
                        title = "Hide Blue Ticks (Read Receipts)",
                        description = "Read incoming messages privately inside this app without sending blue ticks.",
                        checked = ghost.hideBlueTicks,
                        testTag = "ghost_hide_ticks_switch",
                        onCheckedChange = {
                            onUpdateGhostPrivacy(ghost.copy(hideBlueTicks = it))
                        }
                    )
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))

                    GhostToggleRow(
                        title = "Hide Typing & Recording Indicator",
                        description = "Never broadcast typing… or recording audio… status while replying.",
                        checked = ghost.hideTypingIndicator,
                        testTag = "ghost_hide_typing_switch",
                        onCheckedChange = {
                            onUpdateGhostPrivacy(ghost.copy(hideTypingIndicator = it))
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenVault,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, GlassBorderEmerald),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonMintAccent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Anti-Delete Vault", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onOpenAutoStatus,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, GlassBorderEmerald),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonMintAccent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Auto-Status", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // 3. Native WhatsApp Privacy Settings (via Baileys)
            item {
                ObsidianSettingsCard(
                    title = "WhatsApp Account Privacy",
                    subtitle = "Synced directly to your WhatsApp account via Baileys",
                    icon = Icons.Rounded.Lock
                ) {
                    PrivacyOptionSelector(
                        label = "Last Seen Visibility",
                        selectedValue = nativePrivacy.lastSeen,
                        options = listOf("all" to "Everyone", "contacts" to "Contacts", "none" to "Nobody"),
                        onSelect = { onUpdateNativePrivacy(nativePrivacy.copy(lastSeen = it)) }
                    )
                    Spacer(Modifier.height(10.dp))

                    PrivacyOptionSelector(
                        label = "Who Can See When I'm Online",
                        selectedValue = nativePrivacy.online,
                        options = listOf("match_last_seen" to "Same as Last Seen", "all" to "Everyone"),
                        onSelect = { onUpdateNativePrivacy(nativePrivacy.copy(online = it)) }
                    )
                    Spacer(Modifier.height(10.dp))

                    PrivacyOptionSelector(
                        label = "Profile Photo",
                        selectedValue = nativePrivacy.profile,
                        options = listOf("all" to "Everyone", "contacts" to "Contacts", "none" to "Nobody"),
                        onSelect = { onUpdateNativePrivacy(nativePrivacy.copy(profile = it)) }
                    )
                    Spacer(Modifier.height(10.dp))

                    PrivacyOptionSelector(
                        label = "Status Privacy",
                        selectedValue = nativePrivacy.status,
                        options = listOf("all" to "Everyone", "contacts" to "Contacts", "none" to "Nobody"),
                        onSelect = { onUpdateNativePrivacy(nativePrivacy.copy(status = it)) }
                    )
                    Spacer(Modifier.height(10.dp))

                    PrivacyOptionSelector(
                        label = "Read Receipts (Account-Wide)",
                        selectedValue = nativePrivacy.readReceipts,
                        options = listOf("none" to "Off (Hidden)", "all" to "On"),
                        onSelect = { onUpdateNativePrivacy(nativePrivacy.copy(readReceipts = it)) }
                    )
                    Spacer(Modifier.height(10.dp))

                    PrivacyOptionSelector(
                        label = "Who Can Add Me to Groups",
                        selectedValue = nativePrivacy.groupAdd,
                        options = listOf("all" to "Everyone", "contacts" to "Contacts"),
                        onSelect = { onUpdateNativePrivacy(nativePrivacy.copy(groupAdd = it)) }
                    )
                    Spacer(Modifier.height(10.dp))

                    PrivacyOptionSelector(
                        label = "Default Disappearing Messages",
                        selectedValue = nativePrivacy.defaultDisappearing.toString(),
                        options = listOf("0" to "Off", "86400" to "24h", "604800" to "7d", "7776000" to "90d"),
                        onSelect = {
                            onUpdateNativePrivacy(nativePrivacy.copy(defaultDisappearing = it.toIntOrNull() ?: 0))
                        }
                    )
                }
            }

            // 4. Local Device Chat Storage & Backups Card (NOT in cloud)
            item {
                ObsidianSettingsCard(
                    title = "Local Chat Storage & Backups",
                    subtitle = "Chats are saved locally on your device (not in cloud)",
                    icon = Icons.Rounded.PhoneAndroid
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportFullBackup,
                            enabled = !uiState.isUpdatingSettings,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGlow,
                                contentColor = ObsidianBase
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("backup_export_full_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export Backup", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onClearAllChats,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorCoral),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("backup_clear_old_chats_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear Chats", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onTriggerCloudSync,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GlassBorderEmerald),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonMintAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("backup_cloud_sync_button")
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Backup WhatsApp Pairing Session Only")
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (!uiState.exportedBackupText.isNullOrBlank()) {
        ExportedBackupDialog(
            title = uiState.exportedBackupTitle.ifBlank { "Chat Backup Export" },
            content = uiState.exportedBackupText,
            onDismiss = onDismissBackupDialog
        )
    }
}

@Composable
fun ExportedBackupDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Backup, contentDescription = null, tint = NeonMintAccent)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Surface(
                color = GlassCardSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.8.dp, GlassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 300.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(content))
                        copied = true
                    }
                ) {
                    Text(if (copied) "Copied!" else "Copy", color = NeonMintAccent, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            putExtra(Intent.EXTRA_TEXT, content)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Backup"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGlow, contentColor = ObsidianBase)
                ) {
                    Text("Share", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun ObsidianSettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = GlassCardSurface,
        border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SoftSagePill)
                        .border(1.dp, NeonMintAccent.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = NeonMintAccent, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun GhostToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBase,
                checkedTrackColor = NeonMintAccent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassCardElevated
            )
        )
    }
}

@Composable
private fun PrivacyOptionSelector(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, display) ->
                val isSelected = selectedValue.equals(value, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    label = {
                        Text(
                            text = display,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGlow,
                        selectedLabelColor = ObsidianBase,
                        containerColor = GlassCardElevated,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = GlassBorder,
                        selectedBorderColor = NeonMintAccent
                    )
                )
            }
        }
    }
}

@Composable
private fun obsidianFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = GlassCardElevated,
    unfocusedContainerColor = ObsidianBase.copy(alpha = 0.6f),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = NeonMintAccent,
    unfocusedBorderColor = GlassBorder,
    focusedLabelColor = NeonMintAccent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = NeonMintAccent
)
