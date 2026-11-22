package com.zaidashiq.chat.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.zaidashiq.chat.data.local.SessionPreferences
import com.zaidashiq.chat.data.model.*
import com.zaidashiq.chat.data.network.BackendConfig
import com.zaidashiq.chat.data.network.RailwayApiService
import com.zaidashiq.chat.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ChatRepository(
    private val context: Context,
    private val sessionPreferences: SessionPreferences
) {
    private var cachedApi: RailwayApiService? = null

    private suspend fun api(): RailwayApiService {
        if (!BackendConfig.isRailwayConfigured) {
            throw IllegalStateException("Service Not Available")
        }
        return cachedApi ?: RetrofitClient.create(sessionPreferences.getOrCreateDeviceId()).also {
            cachedApi = it
        }
    }

    private inline fun <T> safeCall(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun checkHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        safeCall { api().checkHealth() }
    }

    suspend fun getBotStatus(): Result<BotStatusResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getBotStatus() }
    }

    suspend fun requestPairingCode(phoneNumber: String): Result<PairResponse> = withContext(Dispatchers.IO) {
        safeCall { api().requestPairingCode(PairRequest(phoneNumber = phoneNumber)) }
    }

    suspend fun getChats(): Result<ChatsListResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getChatsList() }
    }

    suspend fun getContacts(): Result<ContactsListResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getContacts() }
    }

    suspend fun getChatMessages(numberOrJid: String): Result<ChatViewResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getChatMessages(numberOrJid) }
    }

    suspend fun getChatPresence(jid: String): Result<PresenceResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getChatPresence(jid) }
    }

    suspend fun loadOlderChats(
        jid: String = "",
        oldestMsgId: String = "",
        oldestTimestamp: Long = 0L,
        fromMe: Boolean = false
    ): Result<LoadOlderChatsResponse> = withContext(Dispatchers.IO) {
        safeCall {
            api().loadOlderChats(
                LoadOlderChatsRequest(
                    jid = jid,
                    oldestMsgId = oldestMsgId,
                    oldestTimestamp = oldestTimestamp,
                    fromMe = fromMe
                )
            )
        }
    }

    suspend fun lookupNumber(number: String): Result<NumberLookupResponse> = withContext(Dispatchers.IO) {
        safeCall { api().lookupNumber(number) }
    }

    suspend fun getGroups(): Result<GroupsListResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getGroups() }
    }

    suspend fun getChannels(): Result<ChannelsListResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getChannels() }
    }

    suspend fun getRecentCalls(): Result<CallsListResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getRecentCalls() }
    }

    suspend fun sendText(recipient: String, text: String): Result<SendTextResponse> = withContext(Dispatchers.IO) {
        safeCall { api().sendTextMessage(SendTextRequest(recipient = recipient, text = text)) }
    }

    suspend fun downloadAndSend(
        recipient: String,
        platform: String,
        query: String,
        caption: String = "",
        withButtons: Boolean = true
    ): Result<DownloadAndSendResponse> = withContext(Dispatchers.IO) {
        safeCall {
            api().downloadAndSendMedia(
                DownloadAndSendRequest(
                    recipient = recipient,
                    platform = platform,
                    query = query,
                    caption = caption,
                    withButtons = withButtons
                )
            )
        }
    }

    suspend fun searchGiphy(query: String): Result<GiphySearchResponse> = withContext(Dispatchers.IO) {
        safeCall { api().searchGiphy(query) }
    }

    suspend fun sendMediaFromUri(
        recipient: String,
        type: String,
        uri: Uri,
        fallbackMime: String
    ): Result<SendMediaResponse> = withContext(Dispatchers.IO) {
        safeCall {
            val mime = context.contentResolver.getType(uri) ?: fallbackMime
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not read selected file")
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            api().sendMediaNote(
                SendMediaRequest(
                    recipient = recipient,
                    type = type,
                    mediaData = b64,
                    mimetype = mime
                )
            )
        }
    }

    suspend fun sendVoiceFile(
        recipient: String,
        file: File
    ): Result<SendMediaResponse> = withContext(Dispatchers.IO) {
        safeCall {
            val bytes = file.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            api().sendMediaNote(
                SendMediaRequest(
                    recipient = recipient,
                    type = "voice",
                    mediaData = b64,
                    mimetype = "audio/mp4"
                )
            )
        }
    }

    suspend fun sendFakeLocation(
        recipient: String,
        latitude: Double,
        longitude: Double,
        durationSeconds: Int
    ): Result<SendLocationResponse> = withContext(Dispatchers.IO) {
        safeCall {
            api().sendFakeLocation(
                SendLocationRequest(
                    recipient = recipient,
                    latitude = latitude,
                    longitude = longitude,
                    durationSeconds = durationSeconds
                )
            )
        }
    }

    suspend fun sendButtonMessage(
        recipient: String,
        body: String,
        buttonLabels: List<String>
    ): Result<SendButtonsResponse> = withContext(Dispatchers.IO) {
        safeCall {
            val payloadButtons = buttonLabels.mapIndexed { index, label ->
                InteractiveButtonPayload(
                    id = "btn_${index + 1}",
                    text = label.trim()
                )
            }
            api().sendButtonMessage(
                SendButtonsRequest(
                    recipient = recipient,
                    title = "",
                    body = body,
                    buttons = payloadButtons
                )
            )
        }
    }

    suspend fun getCapturedStatuses(): Result<CapturedStatusesResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getCapturedStatuses() }
    }

    suspend fun forwardCapturedStatus(id: String): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall { api().forwardCapturedStatus(id) }
    }

    suspend fun uploadTextStatus(text: String, bgHex: String): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall {
            api().uploadStatus(
                StatusUploadRequest(
                    type = "text",
                    text = text,
                    backgroundColor = bgHex
                )
            )
        }
    }

    suspend fun uploadMediaStatus(
        type: String,
        uri: Uri,
        caption: String,
        fallbackMime: String,
        privacyMode: String = "everyone",
        onMetaResolved: (fileName: String, totalBytes: Long) -> Unit = { _, _ -> },
        onProgress: (bytesUploaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall {
            val defaultName = when (type) {
                "video" -> "status_video.mp4"
                "voice" -> "status_voice.ogg"
                else -> "status_photo.jpg"
            }
            val meta = com.zaidashiq.chat.data.network.resolveUriMeta(
                contentResolver = context.contentResolver,
                uri = uri,
                fallbackName = defaultName,
                fallbackMime = fallbackMime
            )
            onMetaResolved(meta.displayName, meta.sizeBytes)

            val progressBody = com.zaidashiq.chat.data.network.ProgressRequestBody(
                contentResolver = context.contentResolver,
                uri = uri,
                contentTypeStr = meta.mimeType,
                knownSize = meta.sizeBytes,
                onProgress = onProgress
            )
            val filePart = okhttp3.MultipartBody.Part.createFormData(
                name = "file",
                filename = meta.displayName,
                body = progressBody
            )
            val textPlain = "text/plain".toMediaType()
            val captionBody = caption.toRequestBody(textPlain)
            val voiceFlagBody = (type == "voice").toString().toRequestBody(textPlain)
            val privacyBody = privacyMode.toRequestBody(textPlain)

            api().uploadStatusMultipart(
                file = filePart,
                caption = captionBody,
                isVoiceNote = voiceFlagBody,
                privacyMode = privacyBody
            )
        }
    }

    suspend fun getVaultItems(): Result<VaultItemsResponse> = withContext(Dispatchers.IO) {
        safeCall { api().getVaultItems() }
    }

    suspend fun getVaultSettings(): Result<VaultSettingsPayload> = withContext(Dispatchers.IO) {
        safeCall { api().getVaultSettings() }
    }

    suspend fun updateVaultSettings(payload: VaultSettingsPayload): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall { api().updateVaultSettings(payload) }
    }

    suspend fun forwardVaultItem(id: String): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall { api().forwardVaultItem(id) }
    }

    suspend fun clearVault(): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall { api().clearVault() }
    }

    suspend fun getAutoReactConfig(): Result<AutoReactConfigPayload> = withContext(Dispatchers.IO) {
        safeCall { api().getAutoReactConfig() }
    }

    suspend fun updateAutoReactConfig(payload: AutoReactConfigPayload): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall { api().updateAutoReactConfig(payload) }
    }

    suspend fun addChannelReactRule(
        channelUrl: String,
        channelName: String,
        emojis: List<String>
    ): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall {
            api().addChannelReactRule(
                ChannelRuleRequest(
                    channelUrl = channelUrl,
                    channelName = channelName,
                    emojis = emojis
                )
            )
        }
    }

    suspend fun deleteChannelReactRule(id: String): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall { api().deleteChannelReactRule(id) }
    }

    suspend fun setFullProfilePic(
        targetType: String,
        groupJid: String,
        uri: Uri
    ): Result<GenericActionResponse> = withContext(Dispatchers.IO) {
        safeCall {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not read selected image")
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            api().setFullProfilePicture(
                FullProfilePicRequest(
                    targetType = targetType,
                    groupJid = groupJid,
                    imageData = b64
                )
            )
        }
    }

    suspend fun triggerCloudSync(): Result<CloudSyncResponse> = withContext(Dispatchers.IO) {
        safeCall { api().triggerCloudSync() }
    }
}
