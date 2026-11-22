package com.zaidashiq.chat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ZaidChatObsidianColorScheme = darkColorScheme(
    primary = EmeraldGlow,
    onPrimary = ObsidianBase,
    primaryContainer = SoftSagePill,
    onPrimaryContainer = NeonMintAccent,
    secondary = OnlineMint,
    onSecondary = ObsidianBase,
    secondaryContainer = GlassCardElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonMintAccent,
    onTertiary = ObsidianBase,
    background = ObsidianBase,
    onBackground = TextPrimary,
    surface = GlassCardSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassCardElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBase,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = GlassCardSurface,
    surfaceContainerHigh = GlassCardElevated,
    surfaceContainerHighest = Color(0xFF1C323D),
    outline = GlassBorder,
    outlineVariant = DividerSubtle,
    error = ErrorCoral,
    onError = ObsidianBase
)

private fun Color(argb: Long) = androidx.compose.ui.graphics.Color(argb)

val ZaidChatShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun ZaidAshiqChatTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ZaidChatObsidianColorScheme,
        typography = ZaidChatTypography,
        shapes = ZaidChatShapes,
        content = content
    )
}
