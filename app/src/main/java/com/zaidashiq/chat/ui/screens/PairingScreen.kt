package com.zaidashiq.chat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaidashiq.chat.R
import com.zaidashiq.chat.data.network.BackendConfig
import com.zaidashiq.chat.ui.components.PulsingStatusDot
import com.zaidashiq.chat.ui.theme.*
import com.zaidashiq.chat.ui.viewmodel.MainUiState

@Composable
fun PairingScreen(
    uiState: MainUiState,
    onGenerateCode: (String) -> Unit,
    onContinueToHome: () -> Unit,
    onOpenConnectionSettings: () -> Unit
) {
    var phoneInput by remember { mutableStateOf(uiState.linkedPhone) }
    var copiedFeedback by remember { mutableStateOf(false) }
    var heroEntered by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        heroEntered = true
    }

    LaunchedEffect(uiState.isLinked) {
        if (uiState.isLinked) {
            onContinueToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBase)
    ) {
        // Full-bleed luxury obsidian-emerald ambient artwork
        Image(
            painter = painterResource(id = R.drawable.img_bg_splash_ambient),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Smooth atmospheric gradient overlay so cards and typography gleam clearly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBase.copy(alpha = 0.34f),
                            ObsidianBase.copy(alpha = 0.54f),
                            ObsidianBase.copy(alpha = 0.90f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Luxury Glass Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GlassCardSurface.copy(alpha = 0.80f))
                            .border(1.2.dp, NeonMintAccent.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubble,
                            contentDescription = stringResource(R.string.app_name),
                            tint = NeonMintAccent,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextOnDark,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Private Stealth Companion",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMintAccent.copy(alpha = 0.85f)
                        )
                    }
                }

                IconButton(
                    onClick = onOpenConnectionSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlassCardSurface.copy(alpha = 0.72f))
                        .border(1.dp, GlassBorder, CircleShape)
                        .testTag("pairing_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsEthernet,
                        contentDescription = stringResource(R.string.menu_connection_settings),
                        tint = TextOnDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(
                visible = heroEntered,
                enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) + slideInVertically(
                    initialOffsetY = { -16 },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.pairing_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextOnDark,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pairing_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextOnDarkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            // Flagship Frosted Obsidian-Glass Pairing Card
            AnimatedVisibility(
                visible = heroEntered,
                enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) + slideInVertically(
                    initialOffsetY = { 24 },
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = GlassCardSurface.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, GlassBorderEmerald.copy(alpha = 0.75f)),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pulsing Backend Status Pill
                        val pillColor = if (uiState.backendHealthy) {
                            SoftSagePill.copy(alpha = 0.90f)
                        } else {
                            ErrorCoral.copy(alpha = 0.14f)
                        }
                        val pillBorder = if (uiState.backendHealthy) {
                            NeonMintAccent.copy(alpha = 0.35f)
                        } else {
                            ErrorCoral.copy(alpha = 0.35f)
                        }
                        val pillTextColor = if (uiState.backendHealthy) NeonMintAccent else ErrorCoral
                        val dotColor = if (uiState.backendHealthy) OnlineMint else ErrorCoral

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillColor)
                                .border(1.dp, pillBorder, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PulsingStatusDot(
                                color = dotColor,
                                isPulsing = uiState.backendHealthy,
                                size = 8.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    uiState.backendHealthy -> stringResource(R.string.service_online)
                                    !BackendConfig.isRailwayConfigured -> stringResource(R.string.service_not_available)
                                    !uiState.bannerNotice.isNullOrBlank() -> stringResource(R.string.service_not_available)
                                    else -> stringResource(R.string.service_connecting)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = pillTextColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(22.dp))

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } },
                            label = { Text(stringResource(R.string.pairing_phone_label)) },
                            placeholder = { Text(stringResource(R.string.pairing_phone_hint), color = TextSecondary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.PhoneAndroid,
                                    contentDescription = null,
                                    tint = NeonMintAccent
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pairing_phone_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = GlassCardElevated,
                                unfocusedContainerColor = ObsidianSurface,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = NeonMintAccent,
                                unfocusedBorderColor = GlassBorder,
                                focusedLabelColor = NeonMintAccent,
                                unfocusedLabelColor = TextSecondary,
                                cursorColor = NeonMintAccent
                            )
                        )

                        if (!uiState.pairingError.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = uiState.pairingError,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorCoral,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = { onGenerateCode(phoneInput) },
                            enabled = !uiState.isRequestingCode && phoneInput.filter { it.isDigit() }.length >= 7,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("generate_pairing_code_button"),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGlow,
                                contentColor = ObsidianBase,
                                disabledContainerColor = GlassCardElevated,
                                disabledContentColor = TextSecondary
                            )
                        ) {
                            if (uiState.isRequestingCode) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = ObsidianBase,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Requesting Baileys Code…", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.pairing_generate_btn),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 8-Digit Pairing Code Reveal
                        AnimatedVisibility(
                            visible = !uiState.pairingCode.isNullOrBlank(),
                            enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) + slideInVertically(
                                initialOffsetY = { 16 },
                                animationSpec = tween(240, easing = FastOutSlowInEasing)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "YOUR WHATSAPP PAIRING CODE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextOnDarkMuted,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(DeepGlassTeal, SoftSagePill)
                                            )
                                        )
                                        .border(1.5.dp, NeonMintAccent.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                                        .padding(vertical = 20.dp, horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = uiState.pairingCode.orEmpty(),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp,
                                        letterSpacing = 4.sp,
                                        color = NeonMintAccent,
                                        modifier = Modifier.testTag("pairing_code_display")
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(uiState.pairingCode.orEmpty().replace("-", "")))
                                        copiedFeedback = true
                                    },
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, NeonMintAccent.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("copy_pairing_code_button")
                                ) {
                                    Icon(
                                        imageVector = if (copiedFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                        contentDescription = stringResource(R.string.pairing_copy_btn),
                                        tint = NeonMintAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (copiedFeedback) stringResource(R.string.pairing_copied_toast) else stringResource(R.string.pairing_copy_btn),
                                        color = NeonMintAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (uiState.isWaitingForLink) {
                                    Spacer(Modifier.height(14.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = NeonMintAccent,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.pairing_waiting_status),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NeonMintAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.pairing_instructions),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // Frosted Glass Open Messenger Pill
            OutlinedButton(
                onClick = onContinueToHome,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, GlassBorderEmerald),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = GlassCardSurface.copy(alpha = 0.72f),
                    contentColor = NeonMintAccent
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("open_messenger_workspace_button")
            ) {
                Text(
                    text = "Open Messenger Interface",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonMintAccent,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = NeonMintAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
