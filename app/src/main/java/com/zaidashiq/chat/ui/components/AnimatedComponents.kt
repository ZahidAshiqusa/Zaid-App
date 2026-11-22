package com.zaidashiq.chat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaidashiq.chat.R
import com.zaidashiq.chat.ui.theme.*
import com.zaidashiq.chat.ui.viewmodel.ChatToolType
import kotlinx.coroutines.delay

/**
 * Luxury obsidian-emerald silk wave backdrop for headers and hero banners.
 * Uses bespoke generated artwork veiled with a soft obsidian-jade gradient for crisp text legibility.
 */
@Composable
fun AnimatedAuroraEmeraldBackground(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(ObsidianSurface)) {
        Image(
            painter = painterResource(id = R.drawable.img_bg_hero_obsidian),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBase.copy(alpha = 0.42f),
                            DeepGlassTeal.copy(alpha = 0.56f),
                            ObsidianSurface.copy(alpha = 0.88f)
                        )
                    )
                )
        )
    }
}

/**
 * Subtle, professional connection indicator dot with gentle breathing opacity.
 */
@Composable
fun PulsingStatusDot(
    color: Color,
    isPulsing: Boolean,
    size: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "subtle_dot_breath")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .alpha(if (isPulsing) alpha else 1f)
            .background(color)
    )
}

/**
 * Modern obsidian-glass Material 3 loading card shown while fetching & sending a
 * YouTube Song (.play), YouTube Video Song (with buttons), or direct social media file.
 */
@Composable
fun MediaSendingLoadingCard(
    tool: ChatToolType?,
    query: String,
    statusText: String?,
    modifier: Modifier = Modifier
) {
    val isSong = tool == ChatToolType.SONG || tool == ChatToolType.VOICE_NOTE
    val isVideo = tool == ChatToolType.VIDEO || tool == ChatToolType.VIDEO_NOTE ||
        tool == ChatToolType.TIKTOK || tool == ChatToolType.FACEBOOK || tool == ChatToolType.INSTAGRAM

    val stepLabels = remember(isSong, isVideo) {
        when {
            isSong -> listOf(
                "Searching YouTube audio…",
                "Preparing MP3 & interactive buttons…",
                "Delivering directly to WhatsApp…"
            )
            isVideo -> listOf(
                "Resolving HD video stream…",
                "Preparing video & interactive buttons…",
                "Delivering directly to WhatsApp…"
            )
            else -> listOf(
                "Resolving direct media…",
                "Preparing file stream…",
                "Delivering directly to WhatsApp…"
            )
        }
    }

    var stepIndex by remember(query, tool) { mutableIntStateOf(0) }
    LaunchedEffect(query, tool) {
        while (true) {
            delay(2400L)
            stepIndex = (stepIndex + 1) % stepLabels.size
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("media_sending_loading_card"),
        shape = RoundedCornerShape(18.dp),
        color = GlassCardSurface,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(DeepGlassTeal, GlassCardElevated)
                    )
                )
                .border(
                    width = 1.dp,
                    color = NeonMintAccent.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonMintAccent.copy(alpha = 0.14f))
                        .border(1.dp, NeonMintAccent.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isSong -> Icons.Rounded.MusicNote
                            isVideo -> Icons.Rounded.OndemandVideo
                            else -> Icons.Rounded.CloudDownload
                        },
                        contentDescription = null,
                        tint = NeonMintAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (tool) {
                                ChatToolType.SONG -> "YouTube Song"
                                ChatToolType.VIDEO -> "YouTube Video"
                                else -> tool?.title ?: "Direct Media"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonMintAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = NeonMintAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (tool == ChatToolType.SONG || tool == ChatToolType.VIDEO) "WITH BUTTONS" else "DIRECT MEDIA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextOnDark,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (query.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = query,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextOnDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(2.dp))
                    AnimatedContent(
                        targetState = stepLabels[stepIndex].ifBlank { statusText ?: "Sending direct media…" },
                        transitionSpec = {
                            fadeIn(tween(240, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(180, easing = FastOutSlowInEasing))
                        },
                        label = "media_step_label"
                    ) { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextOnDarkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = NeonMintAccent,
                    trackColor = TextOnDark.copy(alpha = 0.16f),
                    strokeWidth = 2.2.dp
                )
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50)),
                color = NeonMintAccent,
                trackColor = TextOnDark.copy(alpha = 0.14f)
            )
        }
    }
}
