package com.zaidashiq.chat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ZaidChatLightColorScheme = lightColorScheme(
    primary = DeepEmerald,
    onPrimary = TextOnDark,
    primaryContainer = FloatingMenuTeal,
    onPrimaryContainer = TextOnDark,
    secondary = WhatsAppGreen,
    onSecondary = TextOnDark,
    secondaryContainer = SoftSagePill,
    onSecondaryContainer = DeepEmerald,
    tertiary = OnlineMint,
    onTertiary = TextOnDark,
    background = SurfacePureWhite,
    onBackground = TextPrimary,
    surface = SurfacePureWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSubtleGray,
    onSurfaceVariant = TextSecondary,
    outline = DividerSubtle,
    error = ErrorCoral,
    onError = TextOnDark
)

val ZaidChatShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZaidAshiqChatTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ZaidChatLightColorScheme,
        typography = ZaidChatTypography,
        shapes = ZaidChatShapes,
        content = content
    )
}
