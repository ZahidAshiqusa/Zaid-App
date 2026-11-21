package com.zaidashiq.chat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaidashiq.chat.R
import com.zaidashiq.chat.data.network.BackendConfig
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
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(uiState.isLinked) {
        if (uiState.isLinked) {
            onContinueToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfacePureWhite)
    ) {
        // Deep Emerald Curved Hero Backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DeepEmerald, EmeraldHeader)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(TextOnDark.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubble,
                            contentDescription = stringResource(R.string.app_name),
                            tint = OnlineMint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextOnDark
                    )
                }

                IconButton(
                    onClick = onOpenConnectionSettings,
                    modifier = Modifier.testTag("pairing_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsEthernet,
                        contentDescription = stringResource(R.string.menu_connection_settings),
                        tint = TextOnDark
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = stringResource(R.string.pairing_title),
                style = MaterialTheme.typography.headlineLarge,
                color = TextOnDark,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.pairing_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextOnDarkMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Main Pairing Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = SurfacePureWhite),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Railway Backend Status Pill
                    val pillColor = if (uiState.backendHealthy) SoftSageTint else ErrorCoral.copy(alpha = 0.1f)
                    val pillTextColor = if (uiState.backendHealthy) DeepEmerald else ErrorCoral
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.backendHealthy) OnlineMint else ErrorCoral)
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
                            color = pillTextColor
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } },
                        label = { Text(stringResource(R.string.pairing_phone_label)) },
                        placeholder = { Text(stringResource(R.string.pairing_phone_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = null,
                                tint = DeepEmerald
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pairing_phone_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = DividerSubtle
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

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { onGenerateCode(phoneInput) },
                        enabled = !uiState.isRequestingCode && phoneInput.filter { it.isDigit() }.length >= 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("generate_pairing_code_button"),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepEmerald,
                            contentColor = TextOnDark
                        )
                    ) {
                        if (uiState.isRequestingCode) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = TextOnDark,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Requesting Baileys Code…")
                        } else {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pairing_generate_btn),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    // 8-Digit Pairing Code Reveal
                    AnimatedVisibility(
                        visible = !uiState.pairingCode.isNullOrBlank(),
                        enter = fadeIn() + scaleIn()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "YOUR WHATSAPP PAIRING CODE",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(SoftSageTint)
                                    .border(1.5.dp, DeepEmerald, RoundedCornerShape(18.dp))
                                    .padding(vertical = 18.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.pairingCode.orEmpty(),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                    letterSpacing = 4.sp,
                                    color = DeepEmerald,
                                    modifier = Modifier.testTag("pairing_code_display")
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(uiState.pairingCode.orEmpty().replace("-", "")))
                                    copiedFeedback = true
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("copy_pairing_code_button")
                            ) {
                                Icon(
                                    imageVector = if (copiedFeedback) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.pairing_copy_btn),
                                    tint = DeepEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (copiedFeedback) stringResource(R.string.pairing_copied_toast) else stringResource(R.string.pairing_copy_btn),
                                    color = DeepEmerald
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
                                        color = WhatsAppGreen,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.pairing_waiting_status),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DeepEmerald,
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

            Spacer(Modifier.height(18.dp))

            // Quick open Messenger button so user can also inspect the UI / offline state anytime
            TextButton(
                onClick = onContinueToHome,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("open_messenger_workspace_button")
            ) {
                Text(
                    text = "Open Messenger Interface",
                    style = MaterialTheme.typography.labelLarge,
                    color = DeepEmerald
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = DeepEmerald,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
