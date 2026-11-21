package com.zaidashiq.chat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.zaidashiq.chat.data.model.GiphyItem
import com.zaidashiq.chat.data.network.BackendConfig
import com.zaidashiq.chat.ui.theme.*
import com.zaidashiq.chat.ui.viewmodel.ChatToolType
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDownloadSheet(
    tool: ChatToolType,
    isBusy: Boolean,
    giphyResults: List<GiphyItem>,
    isSearchingGiphy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (query: String, caption: String) -> Unit,
    onSearchGiphy: (String) -> Unit,
    onSelectGiphy: (GiphyItem) -> Unit
) {
    var inputQuery by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Link/Search, 1 = Giphy (Downloader only)
    var giphyQuery by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfacePureWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SoftSageTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (tool) {
                            ChatToolType.SONG -> Icons.Rounded.MusicNote
                            ChatToolType.VIDEO -> Icons.Rounded.OndemandVideo
                            ChatToolType.TIKTOK -> Icons.Rounded.MusicVideo
                            ChatToolType.FACEBOOK -> Icons.Rounded.Facebook
                            ChatToolType.INSTAGRAM -> Icons.Rounded.PhotoCameraBack
                            else -> Icons.Rounded.DownloadForOffline
                        },
                        contentDescription = tool.title,
                        tint = DeepEmerald
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${tool.title} Direct Sender",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Downloads on Railway & sends actual media file (never the link)",
                        style = MaterialTheme.typography.bodySmall,
                        color = WhatsAppGreen
                    )
                }
            }

            if (tool == ChatToolType.DOWNLOADER) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSubTab == 0,
                        onClick = { selectedSubTab = 0 },
                        label = { Text("Any Media Link") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedSubTab == 1,
                        onClick = {
                            selectedSubTab = 1
                            if (giphyResults.isEmpty()) onSearchGiphy(giphyQuery)
                        },
                        label = { Text("Giphy GIFs") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (tool == ChatToolType.DOWNLOADER && selectedSubTab == 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = giphyQuery,
                        onValueChange = { giphyQuery = it },
                        placeholder = { Text("Search Giphy GIFs…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("giphy_search_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { onSearchGiphy(giphyQuery) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = DeepEmerald),
                        modifier = Modifier.size(52.dp).testTag("giphy_search_button")
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search GIFs", tint = TextOnDark)
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (!BackendConfig.isGiphyConfigured && giphyResults.isEmpty()) {
                    Text(
                        text = "Add GIPHY_API_KEY in AI Studio Secrets (or Railway Variables) to browse & send GIFs directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else if (isSearchingGiphy) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DeepEmerald)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().height(230.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(giphyResults, key = { it.id }) { gif ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceSubtleGray)
                                    .clickable(enabled = !isBusy) { onSelectGiphy(gif) }
                            ) {
                                AsyncImage(
                                    model = gif.previewUrl,
                                    contentDescription = gif.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    label = {
                        Text(
                            when (tool) {
                                ChatToolType.SONG -> "Song name or YouTube URL"
                                ChatToolType.VIDEO -> "Video title or YouTube URL"
                                ChatToolType.TIKTOK -> "Paste TikTok video URL"
                                ChatToolType.FACEBOOK -> "Paste Facebook video / Reel URL"
                                ChatToolType.INSTAGRAM -> "Paste Instagram Reel / Post URL"
                                else -> "Paste TikTok, IG, FB, YouTube, or media URL"
                            }
                        )
                    },
                    placeholder = { Text(tool.hint) },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { pasted -> inputQuery = pasted.trim() }
                        }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste link", tint = DeepEmerald)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("media_tool_url_input")
                )

                if (tool != ChatToolType.SONG) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Caption (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { onSubmit(inputQuery, caption) },
                    enabled = !isBusy && inputQuery.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("media_tool_send_button"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = FloatingMenuTeal)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextOnDark, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Fetching & Sending Media…")
                    } else {
                        Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download & Send ${tool.title}")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoNoteSheet(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onVideoSelected: (Uri) -> Unit
) {
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onVideoSelected(uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfacePureWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SoftSageTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Videocam, contentDescription = null, tint = DeepEmerald, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Send Circular Video Note", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pick any short video from your device using the zero-permission Android Photo Picker. Baileys sends it as a native WhatsApp round Video Note (PTV).",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("pick_video_note_button"),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = FloatingMenuTeal)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextOnDark, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Uploading Video Note…")
                } else {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose Video for Video Note")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNoteSheet(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onAudioUriSelected: (Uri) -> Unit,
    onRecordedVoiceReady: (File) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onAudioUriSelected(uri)
    }

    fun startRecordingInternal() {
        try {
            val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(64000)
            mr.setAudioSamplingRate(44100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            outputFile = file
            elapsedSec = 0
            isRecording = true
            permissionDenied = false
        } catch (_: Exception) {
            isRecording = false
        }
    }

    fun stopRecordingInternal(send: Boolean) {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        } finally {
            recorder = null
            isRecording = false
        }
        val file = outputFile
        if (send && file != null && file.exists()) {
            onRecordedVoiceReady(file)
        } else {
            file?.delete()
        }
        outputFile = null
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecordingInternal() else permissionDenied = true
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000L)
            elapsedSec++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) stopRecordingInternal(send = false)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isRecording) stopRecordingInternal(send = false)
            onDismiss()
        },
        containerColor = SurfacePureWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("WhatsApp Voice Note (PTT)", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) ErrorCoral else FloatingMenuTeal)
                    .clickable(enabled = !isBusy) {
                        if (isRecording) {
                            stopRecordingInternal(send = true)
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) startRecordingInternal()
                            else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    .testTag("record_voice_note_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Rounded.Send else Icons.Rounded.Mic,
                    contentDescription = if (isRecording) "Stop and Send Voice Note" else "Start Recording",
                    tint = TextOnDark,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isRecording) {
                    "Recording… ${elapsedSec / 60}:${(elapsedSec % 60).toString().padStart(2, '0')} (Tap to Send)"
                } else {
                    "Tap microphone to record live voice note"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isRecording) ErrorCoral else TextSecondary,
                fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Normal
            )

            if (permissionDenied) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Microphone permission was denied. You can still pick an audio file below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorCoral
                )
            }

            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                enabled = !isBusy && !isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pick_audio_voice_note_button"),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Rounded.AudioFile, contentDescription = null, tint = DeepEmerald)
                Spacer(Modifier.width(8.dp))
                Text("Or Pick Audio File as Voice Note", color = DeepEmerald)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeLocationSheet(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSendLocation: (lat: Double, lng: Double, durationSec: Int, label: String) -> Unit
) {
    val presets = listOf(
        Triple("Lahore, PK", 31.5204, 74.3587),
        Triple("Karachi, PK", 24.8607, 67.0011),
        Triple("Islamabad, PK", 33.6844, 73.0479),
        Triple("Dubai, UAE", 25.2048, 55.2708),
        Triple("London, UK", 51.5074, -0.1278)
    )
    var selectedLabel by remember { mutableStateOf("Lahore, PK") }
    var latText by remember { mutableStateOf("31.5204") }
    var lngText by remember { mutableStateOf("74.3587") }
    var durationSec by remember { mutableIntStateOf(900) } // 15m default

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfacePureWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Send Fake Live Location", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                text = "Sends a native WhatsApp Live Location pin with custom GPS coordinates.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(12.dp))
            Text("Quick City Presets", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.take(3).forEach { (label, lat, lng) ->
                    FilterChip(
                        selected = selectedLabel == label,
                        onClick = {
                            selectedLabel = label
                            latText = lat.toString()
                            lngText = lng.toString()
                        },
                        label = { Text(label.substringBefore(","), maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.drop(3).forEach { (label, lat, lng) ->
                    FilterChip(
                        selected = selectedLabel == label,
                        onClick = {
                            selectedLabel = label
                            latText = lat.toString()
                            lngText = lng.toString()
                        },
                        label = { Text(label.substringBefore(","), maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        selectedLabel = "Custom Pin"
                    },
                    label = { Text("Latitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("fake_location_lat_input")
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = {
                        lngText = it
                        selectedLabel = "Custom Pin"
                    },
                    label = { Text("Longitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("fake_location_lng_input")
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Live Duration", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(900 to "15 min", 3600 to "1 hour", 28800 to "8 hours").forEach { (sec, lbl) ->
                    FilterChip(
                        selected = durationSec == sec,
                        onClick = { durationSec = sec },
                        label = { Text(lbl) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lng = lngText.toDoubleOrNull()
                    if (lat != null && lng != null) {
                        onSendLocation(lat, lng, durationSec, selectedLabel)
                    }
                },
                enabled = !isBusy && latText.toDoubleOrNull() != null && lngText.toDoubleOrNull() != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("fake_location_send_button"),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = FloatingMenuTeal)
            ) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send Fake Location")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
