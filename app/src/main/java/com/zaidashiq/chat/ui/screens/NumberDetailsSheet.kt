package com.zaidashiq.chat.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zaidashiq.chat.data.model.NumberLookupResponse
import com.zaidashiq.chat.data.model.SimRecordItem
import com.zaidashiq.chat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberDetailsSheet(
    initialNumber: String,
    isLoading: Boolean,
    result: NumberLookupResponse?,
    errorMessage: String?,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var queryInput by remember(initialNumber) {
        mutableStateOf(initialNumber.substringBefore("@").filter { it.isDigit() })
    }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(initialNumber) {
        val clean = initialNumber.substringBefore("@").filter { it.isDigit() }
        if (clean.length >= 7) {
            onSearch(clean)
        }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("number_details_sheet")
        ) {
            // Header
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
                        imageVector = Icons.Rounded.PersonSearch,
                        contentDescription = "Number Details",
                        tint = NeonMintAccent,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Number Details",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Truecaller + SIM Database + WhatsApp Profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (result != null && result.success) {
                    IconButton(
                        onClick = {
                            val summary = buildString {
                                appendLine("Number: ${result.formattedNumber}")
                                if (result.operator.isNotBlank()) appendLine("Operator: ${result.operator}")
                                if (result.truecaller.name.isNotBlank()) appendLine("Truecaller Name: ${result.truecaller.name}")
                                if (result.truecaller.address.isNotBlank()) appendLine("Truecaller Location: ${result.truecaller.address}")
                                if (result.whatsapp.about.isNotBlank()) appendLine("WhatsApp About: ${result.whatsapp.about}")
                                result.simRecords.forEachIndexed { idx, rec ->
                                    appendLine("SIM Record #${idx + 1}: ${rec.name} | CNIC: ${rec.cnic} | ${rec.address}")
                                }
                            }.trim()
                            clipboard.setText(AnnotatedString(summary))
                            copied = true
                        },
                        modifier = Modifier.testTag("number_details_copy_button")
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Copy details",
                            tint = NeonMintAccent
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Number Input + Search Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = queryInput,
                    onValueChange = { queryInput = it.filter { ch -> ch.isDigit() || ch == '+' } },
                    label = { Text("Phone Number or CNIC") },
                    placeholder = { Text("923001234567 or 03001234567", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("number_details_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GlassCardElevated,
                        unfocusedContainerColor = GlassCardSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonMintAccent,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = NeonMintAccent,
                        cursorColor = NeonMintAccent
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        copied = false
                        onSearch(queryInput)
                    },
                    enabled = !isLoading && queryInput.filter { it.isDigit() }.length >= 7,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = EmeraldGlow,
                        contentColor = ObsidianBase
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("number_details_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Fetch number details",
                        tint = ObsidianBase
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Surface(
                    color = SoftSageTint,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, GlassBorderEmerald),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50)),
                            color = NeonMintAccent,
                            trackColor = GlassCardElevated
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Querying Truecaller & SIM Database…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonMintAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = ErrorCoral.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = ErrorCoral)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorCoral
                        )
                    }
                }
            } else if (result != null && result.success) {
                // Overview Strip
                Surface(
                    color = SoftSageTint,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, GlassBorderEmerald),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (result.whatsapp.profilePicUrl.isNotBlank()) {
                            AsyncImage(
                                model = result.whatsapp.profilePicUrl,
                                contentDescription = "WhatsApp avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .border(1.2.dp, NeonMintAccent.copy(alpha = 0.5f), CircleShape)
                            )
                        } else {
                            ContactAvatar(
                                name = result.truecaller.name.ifBlank { result.whatsapp.contactName.ifBlank { result.formattedNumber } },
                                isGroup = false,
                                showOnlineDot = false,
                                size = 50.dp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = result.truecaller.name.ifBlank {
                                    result.simRecords.firstOrNull()?.name.orEmpty().ifBlank {
                                        result.whatsapp.contactName.ifBlank { result.formattedNumber }
                                    }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = buildString {
                                    append(result.formattedNumber)
                                    if (result.operator.isNotBlank()) append(" • ${result.operator}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonMintAccent,
                                fontWeight = FontWeight.Medium
                            )
                            if (result.whatsapp.about.isNotBlank()) {
                                Text(
                                    text = "About: ${result.whatsapp.about}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Truecaller Card
                DetailSectionCard(
                    title = "Truecaller Details",
                    icon = Icons.Rounded.VerifiedUser
                ) {
                    val tc = result.truecaller
                    if (!tc.found && tc.name.isBlank() && tc.address.isBlank() && tc.email.isBlank()) {
                        DetailInfoLine("Status", "No public Truecaller name found")
                        if (tc.carrier.isNotBlank()) DetailInfoLine("Carrier", tc.carrier)
                        if (tc.country.isNotBlank()) DetailInfoLine("Country", tc.country)
                    } else {
                        if (tc.name.isNotBlank()) DetailInfoLine("Name", tc.name)
                        if (tc.carrier.isNotBlank()) DetailInfoLine("Carrier", tc.carrier)
                        if (tc.country.isNotBlank()) DetailInfoLine("Country", tc.country)
                        if (tc.address.isNotBlank()) DetailInfoLine("Location", tc.address)
                        if (tc.email.isNotBlank()) DetailInfoLine("Email", tc.email)
                        if (tc.spamScore.isNotBlank()) DetailInfoLine("Spam Score", tc.spamScore)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // SIM Database Card
                DetailSectionCard(
                    title = "SIM Database (${result.simRecords.size})",
                    icon = Icons.Rounded.SimCard
                ) {
                    if (result.simRecords.isEmpty()) {
                        DetailInfoLine("Records", "No SIM database record returned for this number")
                        if (result.localNumber.isNotBlank()) DetailInfoLine("Local Format", result.localNumber)
                        if (result.operator.isNotBlank()) DetailInfoLine("Network", result.operator)
                    } else {
                        result.simRecords.forEachIndexed { index, rec ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = GlassBorder,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            SimRecordBlock(index = index + 1, record = rec)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // WhatsApp Status Card
                DetailSectionCard(
                    title = "WhatsApp Account",
                    icon = Icons.Rounded.ChatBubble
                ) {
                    DetailInfoLine(
                        "Registration",
                        if (result.whatsapp.registered) "Registered on WhatsApp" else "Not verified / Private"
                    )
                    if (result.whatsapp.contactName.isNotBlank()) {
                        DetailInfoLine("Saved Contact", result.whatsapp.contactName)
                    }
                    if (result.whatsapp.about.isNotBlank()) {
                        DetailInfoLine("Bio / About", result.whatsapp.about)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = GlassCardSurface,
        border = BorderStroke(0.8.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = NeonMintAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = NeonMintAccent,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SimRecordBlock(index: Int, record: SimRecordItem) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (record.name.isNotBlank()) DetailInfoLine("Owner #$index", record.name)
        if (record.number.isNotBlank()) DetailInfoLine("Number", record.number)
        if (record.cnic.isNotBlank()) DetailInfoLine("CNIC", record.cnic)
        if (record.network.isNotBlank()) DetailInfoLine("Network", record.network)
        if (record.address.isNotBlank()) DetailInfoLine("Address", record.address)
    }
}

@Composable
private fun DetailInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.widthIn(min = 92.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
