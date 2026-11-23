package com.zaidashiq.chat.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zaidashiq.chat.data.model.BotCommandItem
import com.zaidashiq.chat.ui.theme.*

private val DEFAULT_BOT_COMMANDS = listOf(
    BotCommandItem(".play", ".play <song name or link>", "Play Song (MP3)", "Media", "Downloads & sends direct YouTube/Spotify MP3 + interactive buttons"),
    BotCommandItem(".video", ".video <video title or link>", "Video Song (MP4)", "Media", "Downloads & sends direct YouTube MP4 video + interactive buttons"),
    BotCommandItem(".tiktok", ".tiktok <url>", "TikTok HD Video", "Social", "Downloads watermark-free HD TikTok video straight into chat"),
    BotCommandItem(".fb", ".fb <url>", "Facebook Video", "Social", "Downloads Facebook Reels & watch videos straight into chat"),
    BotCommandItem(".ig", ".ig <url>", "Instagram Reel / Post", "Social", "Downloads Instagram Reels & photos straight into chat"),
    BotCommandItem(".dl", ".dl <any url>", "Universal Downloader & GIFs", "Media", "Auto-detects platform and sends audio/video/GIF directly"),
    BotCommandItem(".number", ".number <phone>", "Number Details Lookup", "Tools", "Truecaller + SIM Database + WhatsApp Profile lookup & report"),
    BotCommandItem(".buttons", "Interactive Buttons Composer", "Interactive Buttons", "WhatsApp", "Send custom WhatsApp message with clickable reply buttons"),
    BotCommandItem(".ptv", "Circular Video Note", "Video Note (PTV)", "WhatsApp", "Send round circular WhatsApp video note"),
    BotCommandItem(".ptt", "Voice Note Recorder", "Voice Note (PTT)", "WhatsApp", "Record or pick audio and send as voice note"),
    BotCommandItem(".loc", "Fake Live Location", "Fake Location", "WhatsApp", "Send custom GPS coordinates or live location pin"),
    BotCommandItem(".fdp", "Full Resolution DP", "Full Uncropped DP (.fdp)", "Account", "Set 100% same-resolution uncropped WhatsApp DP"),
    BotCommandItem(".ghost", "Ghost & Privacy Suite", "Ghost Privacy & Account", "Account", "Freeze online, ghost status view, name/bio, privacy & backups"),
    BotCommandItem(".menu", ".menu", "Send Bot Menu to Chat", "Tools", "Sends formatted command menu directly to this chat")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotCommandsSheet(
    remoteCommands: List<BotCommandItem>,
    onSelectCommand: (BotCommandItem) -> Unit,
    onDismiss: () -> Unit
) {
    val allCommands = remember(remoteCommands) {
        if (remoteCommands.isNotEmpty()) {
            val existing = remoteCommands.map { it.command.lowercase() }.toSet()
            remoteCommands + DEFAULT_BOT_COMMANDS.filter { it.command.lowercase() !in existing }
        } else {
            DEFAULT_BOT_COMMANDS
        }
    }
    val categories = remember(allCommands) {
        listOf("All") + allCommands.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    var selectedCategory by remember { mutableStateOf("All") }

    val filtered = remember(allCommands, selectedCategory) {
        if (selectedCategory == "All") allCommands
        else allCommands.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .testTag("bot_commands_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SoftSagePill)
                        .border(1.dp, NeonMintAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = "Bot Commands",
                        tint = NeonMintAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "All Bot Commands & Integrations",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any command to launch or execute directly in chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val selected = cat == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                text = cat,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
                            selected = selected,
                            borderColor = GlassBorder,
                            selectedBorderColor = NeonMintAccent
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filtered.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = GlassCardSurface,
                        border = BorderStroke(0.8.dp, GlassBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelectCommand(item) }
                            .testTag("bot_cmd_${item.command.removePrefix(".")}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SoftSagePill)
                                    .border(1.dp, NeonMintAccent.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconForCommand(item.command),
                                    contentDescription = item.title,
                                    tint = NeonMintAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = ObsidianBase.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.6.dp, NeonMintAccent.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = item.command,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonMintAccent,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = NeonMintAccent.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun iconForCommand(cmd: String): ImageVector = when (cmd.lowercase()) {
    ".play", ".song" -> Icons.Rounded.MusicNote
    ".video" -> Icons.Rounded.OndemandVideo
    ".tiktok", ".tt" -> Icons.Rounded.MusicVideo
    ".fb" -> Icons.Rounded.Facebook
    ".ig" -> Icons.Rounded.PhotoCameraBack
    ".dl" -> Icons.Rounded.DownloadForOffline
    ".number", ".sim", ".tc" -> Icons.Rounded.PersonSearch
    ".buttons" -> Icons.Rounded.SmartButton
    ".ptv" -> Icons.Rounded.Videocam
    ".ptt" -> Icons.Rounded.Mic
    ".loc" -> Icons.Rounded.LocationOff
    ".fdp" -> Icons.Rounded.AccountCircle
    ".ghost" -> Icons.Rounded.VisibilityOff
    else -> Icons.Rounded.AutoAwesome
}
