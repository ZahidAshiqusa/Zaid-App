package com.zaidashiq.chat.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaidashiq.chat.R
import com.zaidashiq.chat.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Flagship Obsidian-Emerald fading-up startup splash screen with bespoke studio backdrop
 * and developer attribution.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val logoAlpha = remember { Animatable(0f) }
    val logoOffsetY = remember { Animatable(24f) }

    val creditAlpha = remember { Animatable(0f) }
    val creditOffsetY = remember { Animatable(28f) }

    val phoneAlpha = remember { Animatable(0f) }
    val phoneOffsetY = remember { Animatable(20f) }

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        }
        launch {
            logoOffsetY.animateTo(0f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        }

        delay(240L)
        launch {
            creditAlpha.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        }
        launch {
            creditOffsetY.animateTo(0f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        }

        delay(220L)
        launch {
            phoneAlpha.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        }
        launch {
            phoneOffsetY.animateTo(0f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        }

        delay(1500L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBase)
            .testTag("startup_splash_screen")
    ) {
        // Full-bleed luxury ambient obsidian-emerald artwork
        Image(
            painter = painterResource(id = R.drawable.img_bg_splash_ambient),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Deep vignette veil for effortless contrast & gallery depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBase.copy(alpha = 0.26f),
                            ObsidianBase.copy(alpha = 0.18f),
                            ObsidianBase.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 32.dp)
        ) {
            // Center brand emblem with double obsidian-glass emerald halo
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        translationY = logoOffsetY.value
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    NeonMintAccent.copy(alpha = 0.22f),
                                    GlassCardSurface.copy(alpha = 0.55f)
                                )
                            )
                        )
                        .border(1.2.dp, NeonMintAccent.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(DeepEmerald, FloatingMenuTeal)
                                )
                            )
                            .border(1.5.dp, NeonMintAccent.copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubble,
                            contentDescription = stringResource(R.string.app_name),
                            tint = NeonMintAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextOnDark,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(EmeraldGlow, NeonMintAccent)
                            )
                        )
                )
            }

            // Bottom fading-up developer attribution block
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.graphicsLayer {
                        alpha = creditAlpha.value
                        translationY = creditOffsetY.value
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "This App is Built By",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextOnDarkMuted,
                        letterSpacing = 0.8.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Zaid Ashiq \uD83C\uDDF5\uD83C\uDDF0",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextOnDark,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_author_name")
                    )
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = GlassCardSurface.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = phoneAlpha.value
                            translationY = phoneOffsetY.value
                        }
                        .border(1.dp, NeonMintAccent.copy(alpha = 0.42f), RoundedCornerShape(50))
                ) {
                    Text(
                        text = "+923299931199",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonMintAccent,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .testTag("splash_author_phone")
                    )
                }
            }
        }
    }
}
