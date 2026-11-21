package com.zaidashiq.chat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaidashiq.chat.ui.theme.*
import com.zaidashiq.chat.ui.viewmodel.ChatToolType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated deep-emerald aurora mesh backdrop for hero & app bar surfaces.
 */
@Composable
fun AnimatedAuroraEmeraldBackground(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_emerald")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_drift"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_glow"
    )

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(DeepEmerald, EmeraldHeader, DeepGlassTeal),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
        )

        val orb1X = size.width * (0.20f + 0.12f * sin(drift))
        val orb1Y = size.height * (0.25f + 0.10f * sin(drift * 0.7f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonMintAccent.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(orb1X, orb1Y),
                radius = size.maxDimension * 0.48f * glowPulse
            ),
            radius = size.maxDimension * 0.48f * glowPulse,
            center = Offset(orb1X, orb1Y)
        )

        val orb2X = size.width * (0.85f - 0.10f * sin(drift + 1.4f))
        val orb2Y = size.height * (0.78f - 0.12f * sin(drift * 0.9f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(OnlineMint.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(orb2X, orb2Y),
                radius = size.maxDimension * 0.42f
            ),
            radius = size.maxDimension * 0.42f,
            center = Offset(orb2X, orb2Y)
        )
    }
}

/**
 * Animated radar-pulse dot for connection status indicators (not user presence).
 */
@Composable
fun PulsingStatusDot(
    color: Color,
    isPulsing: Boolean,
    size: Dp = 9.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_dot_pulse")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_scale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )

    Box(
        modifier = Modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        if (isPulsing) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = ringAlpha))
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Live 5-bar bouncing audio equalizer used when fetching/sending a YouTube Song (.play / Song).
 */
@Composable
fun AnimatedAudioEqualizer(
    barColor: Color = NeonMintAccent,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_eq")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eq_phase"
    )

    val offsets = listOf(0f, 1.1f, 2.2f, 3.3f, 4.4f)
    Canvas(modifier = modifier.size(width = 30.dp, height = 22.dp)) {
        val barCount = offsets.size
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        offsets.forEachIndexed { index, shift ->
            val factor = 0.25f + 0.75f * ((sin(phase + shift) + 1f) / 2f)
            val barHeight = size.height * factor
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(index * (barWidth + gap), top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * Animated pulsing video/media orbital loader used when fetching/sending YouTube Video or Reels.
 */
@Composable
fun AnimatedVideoPulseBadge(
    isSong: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "media_badge_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_scale"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(WhatsAppGreen, EmeraldGlow)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSong) {
            AnimatedAudioEqualizer(barColor = TextOnDark)
        } else {
            Icon(
                imageVector = Icons.Rounded.OndemandVideo,
                contentDescription = null,
                tint = TextOnDark,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

/**
 * Sleek animated loading card shown while fetching & sending a YouTube Song (.play),
 * YouTube Video, or social media file directly to WhatsApp.
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
                "Converting to high-quality MP3…",
                "Sending direct song file…"
            )
            isVideo -> listOf(
                "Resolving HD video stream…",
                "Downloading media on Railway…",
                "Sending direct video file…"
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
            delay(2200L)
            stepIndex = (stepIndex + 1) % stepLabels.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "media_shimmer")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_sweep"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar_glow"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("media_sending_loading_card"),
        shape = RoundedCornerShape(20.dp),
        color = DeepEmerald,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(DeepEmerald, FloatingMenuTeal, DeepGlassTeal)
                    )
                )
                .border(
                    width = 1.dp,
                    color = NeonMintAccent.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVideoPulseBadge(isSong = isSong)

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
                            color = TextOnDark.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "DIRECT MEDIA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
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
                    Text(
                        text = stepLabels[stepIndex].ifBlank { statusText ?: "Sending direct media…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextOnDarkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = NeonMintAccent,
                    trackColor = TextOnDark.copy(alpha = 0.16f),
                    strokeWidth = 2.5.dp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Animated neon-mint shimmer progress bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            ) {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    cornerRadius = radius
                )
                val bandWidth = size.width * 0.42f
                val left = (shimmerX * size.width) - bandWidth / 2f
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonMintAccent.copy(alpha = glowAlpha),
                            OnlineMint,
                            NeonMintAccent.copy(alpha = glowAlpha),
                            Color.Transparent
                        ),
                        startX = left,
                        endX = left + bandWidth
                    ),
                    topLeft = Offset(left.coerceAtLeast(0f), 0f),
                    size = Size(
                        width = bandWidth.coerceAtMost(size.width),
                        height = size.height
                    ),
                    cornerRadius = radius
                )
            }
        }
    }
}
