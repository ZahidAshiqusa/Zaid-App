package com.zaidashiq.chat.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zaidashiq.chat.R
import com.zaidashiq.chat.ui.theme.*

@Composable
fun ButtonComposerScreen(
    recipientName: String,
    isSending: Boolean,
    onBack: () -> Unit,
    onSend: (String, List<String>) -> Unit
) {
    var messageBody by remember { mutableStateOf("") }
    var buttons by remember {
        mutableStateOf(listOf("Theek Hai", "Nahi", "Main Aaoon Ga"))
    }

    Scaffold(
        containerColor = SurfaceSubtleGray,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceSubtleGray)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = { onSend(messageBody, buttons) },
                    enabled = !isSending && messageBody.isNotBlank() && buttons.any { it.isNotBlank() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("button_composer_send_button"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FloatingMenuTeal,
                        contentColor = TextOnDark,
                        disabledContainerColor = FloatingMenuTeal.copy(alpha = 0.45f),
                        disabledContentColor = TextOnDark.copy(alpha = 0.8f)
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TextOnDark,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Sending…", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text(
                            text = stringResource(R.string.button_composer_send),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .widthIn(max = 600.dp)
        ) {
            // < Back action matching Screen 3 of mockup
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .minimumInteractiveComponentSize()
                    .padding(end = 12.dp)
                    .testTag("button_composer_back_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.button_composer_back),
                    tint = DeepEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.button_composer_back),
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepEmerald
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.button_composer_title),
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Text(
                text = "To $recipientName",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.button_composer_field_label),
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(8.dp))

            // Rounded white message card matching Screen 3
            OutlinedTextField(
                value = messageBody,
                onValueChange = { messageBody = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.button_composer_field_hint),
                        color = TextPrimary.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("button_composer_message_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfacePureWhite,
                    unfocusedContainerColor = SurfacePureWhite,
                    focusedBorderColor = DeepEmerald,
                    unfocusedBorderColor = DividerSubtle
                )
            )

            Spacer(Modifier.height(22.dp))

            // Interactive button pills with trash delete icons matching Screen 3
            buttons.forEachIndexed { index, label ->
                val isPrimaryPill = index == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { newVal ->
                            buttons = buttons.toMutableList().also { it[index] = newVal }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = DeepEmerald,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        ),
                        placeholder = {
                            Text(
                                text = "Button ${index + 1}",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = TextSecondary
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("button_option_input_$index"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isPrimaryPill) SoftSagePill else SurfacePureWhite,
                            unfocusedContainerColor = if (isPrimaryPill) SoftSagePill else SurfacePureWhite,
                            focusedBorderColor = DeepEmerald,
                            unfocusedBorderColor = DeepEmerald.copy(alpha = 0.65f)
                        )
                    )

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (buttons.size > 1) {
                                buttons = buttons.toMutableList().also { it.removeAt(index) }
                            }
                        },
                        enabled = buttons.size > 1,
                        modifier = Modifier.testTag("delete_button_option_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove button",
                            tint = if (buttons.size > 1) TextSecondary else TextSecondary.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // Dashed "+ Add Button" box matching Screen 3 (up to 5 WhatsApp buttons)
            if (buttons.size < 5) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            buttons = buttons + ""
                        }
                        .testTag("add_button_option_row"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRoundRect(
                            color = DeepEmerald.copy(alpha = 0.55f),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AddCircleOutline,
                            contentDescription = stringResource(R.string.button_composer_add),
                            tint = DeepEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.button_composer_add),
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepEmerald
                        )
                    }
                }
            }
        }
    }
}
