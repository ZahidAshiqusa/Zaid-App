package com.zaidashiq.chat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import com.zaidashiq.chat.ui.theme.*
import com.zaidashiq.chat.ui.viewmodel.StatusUploadProgress
import com.zaidashiq.chat.ui.viewmodel.UploadStage
import java.util.Locale

@Composable
fun StatusUploadProgressCard(
    progress: StatusUploadProgress,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = progress.isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "status_upload_pulse")

        // Breathing halo scale & alpha for the leading media icon
        val haloScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.42f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "halo_scale"
        )
        val haloAlpha by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "halo_alpha"
        )

        // Pulsing brightness & sweep across the progress bar to indicate active network/server processing
        val barPulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_pulse_alpha"
        )
        val shimmerSweep by infiniteTransition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1350, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bar_shimmer_sweep"
        )

        val animatedFraction by animateFloatAsState(
            targetValue = when (progress.stage) {
                UploadStage.PREPARING -> 0.12f
                UploadStage.UPLOADING -> (progress.fraction ?: 0.35f).coerceIn(0.08f, 0.96f)
                UploadStage.PROCESSING -> 1.0f
                UploadStage.COMPLETED -> 1.0f
                UploadStage.FAILED -> (progress.fraction ?: 0.3f).coerceIn(0.1f, 1f)
                UploadStage.IDLE -> 0f
            },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "upload_progress_fraction"
        )

        val accentColor = when (progress.stage) {
            UploadStage.COMPLETED -> WhatsAppGreen
            UploadStage.FAILED -> ErrorCoral
            else -> DeepEmerald
        }

        val mediaIcon = when (progress.mediaType) {
            "video" -> Icons.Rounded.Videocam
            "voice" -> Icons.Rounded.GraphicEq
            "image" -> Icons.Rounded.Image
            else -> Icons.Rounded.CloudUpload
        }

        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("status_upload_progress_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing icon indicator
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (progress.isActive) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .scale(haloScale)
                                    .clip(CircleShape)
                                    .background(OnlineMint.copy(alpha = haloAlpha))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (progress.stage) {
                                    UploadStage.COMPLETED -> Icons.Rounded.CheckCircle
                                    UploadStage.FAILED -> Icons.Rounded.ErrorOutline
                                    else -> mediaIcon
                                },
                                contentDescription = progress.mediaLabel,
                                tint = accentColor,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = progress.mediaLabel.ifBlank { "Status Upload" },
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (progress.stage) {
                                    UploadStage.PREPARING -> "Preparing…"
                                    UploadStage.UPLOADING -> if (progress.totalBytes > 0L) "${progress.percent}%" else "Live"
                                    UploadStage.PROCESSING -> "Processing"
                                    UploadStage.COMPLETED -> "Done"
                                    UploadStage.FAILED -> "Failed"
                                    UploadStage.IDLE -> ""
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = buildString {
                                append(progress.statusMessage)
                                if (progress.totalBytes > 0L && progress.stage == UploadStage.UPLOADING) {
                                    append(" • ")
                                    append(formatByteSize(progress.bytesUploaded))
                                    append(" / ")
                                    append(formatByteSize(progress.totalBytes))
                                } else if (progress.fileName.isNotBlank() && progress.stage == UploadStage.PROCESSING) {
                                    append(" • ")
                                    append(progress.fileName)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!progress.isActive) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).testTag("dismiss_status_progress_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Custom Pulse-Animated Progress Bar Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .testTag("status_upload_progress_bar")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = CornerRadius(size.height / 2f, size.height / 2f)

                        // Subtle track background
                        drawRoundRect(
                            color = SoftSagePill.copy(alpha = 0.65f),
                            size = size,
                            cornerRadius = radius
                        )

                        val fillWidth = (size.width * animatedFraction).coerceAtLeast(size.height)
                        val activeAlpha = if (progress.isActive) barPulseAlpha else 1.0f

                        // Base gradient fill with breathing pulse alpha
                        val fillBrush = when (progress.stage) {
                            UploadStage.FAILED -> Brush.horizontalGradient(
                                listOf(ErrorCoral.copy(alpha = 0.85f), ErrorCoral)
                            )
                            UploadStage.COMPLETED -> Brush.horizontalGradient(
                                listOf(DeepEmerald, OnlineMint)
                            )
                            else -> Brush.horizontalGradient(
                                listOf(
                                    DeepEmerald.copy(alpha = activeAlpha),
                                    WhatsAppGreen.copy(alpha = activeAlpha),
                                    OnlineMint.copy(alpha = activeAlpha)
                                )
                            )
                        }

                        drawRoundRect(
                            brush = fillBrush,
                            size = Size(fillWidth, size.height),
                            cornerRadius = radius
                        )

                        // Luminous pulse wave sweeping across active fill during upload/processing
                        if (progress.isActive) {
                            val sweepCenter = size.width * shimmerSweep
                            val sweepHalfWidth = size.width * 0.22f
                            val waveBrush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.55f),
                                    Color.Transparent
                                ),
                                startX = sweepCenter - sweepHalfWidth,
                                endX = sweepCenter + sweepHalfWidth
                            )
                            drawRoundRect(
                                brush = waveBrush,
                                topLeft = Offset.Zero,
                                size = Size(fillWidth, size.height),
                                cornerRadius = radius
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
