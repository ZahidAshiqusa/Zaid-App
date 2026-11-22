package com.zaidashiq.chat.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val uptime: Double? = null
)

@Serializable
data class BotStatusResponse(
    val status: String = "running",
    val botName: String = "Zaid Ashiq Chat",
    val uptimeFormatted: String = "",
    val connectedSessions: List<String> = emptyList(),
    val activeSessionId: String? = null
)

@Serializable
data class PairRequest(
    val phoneNumber: String
)

@Serializable
data class PairResponse(
    val success: Boolean = false,
    val code: String? = null,
    val cleanNumber: String? = null,
    val alreadyConnected: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class ChatSummaryItem(
    val number: String = "",
    val cleanNumber: String = "",
    val jid: String = "",
    val contactName: String = "",
    val name: String = "",
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val lastTimestamp: Long = 0L,
    val messageCount: Int = 0,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false
) {
    val resolvedJid: String
        get() = when {
            jid.isNotBlank() -> jid
            cleanNumber.isNotBlank() -> "${cleanNumber}@s.whatsapp.net"
            number.isNotBlank() -> "${number.filter { it.isDigit() }}@s.whatsapp.net"
            else -> ""
        }

    val resolvedPhone: String
        get() = cleanNumber.ifBlank { number.ifBlank { jid.substringBefore("@") } }

    val resolvedName: String
        get() = contactName.ifBlank { name.ifBlank { "+$resolvedPhone" } }
}

@Serializable
data class ChatsListResponse(
    val success: Boolean = false,
    val totalChats: Int = 0,
    val chats: List<ChatSummaryItem> = emptyList(),
    val error: String? = null
)

@Serializable
data class ChatMessageItem(
    val id: String = "",
    val fromMe: Boolean = false,
    val senderJid: String = "",
    val senderName: String = "",
    val text: String = "",
    val mediaType: String = "text",
    val timestamp: Long = 0L,
    val isoTime: String = "",
    val buttons: List<String> = emptyList(),
    val status: Int = 2, // 1=pending/clock, 2=single gray tick (offline/server ack), 3=double gray tick (delivered), 4=double blue tick (read)
    val pending: Boolean = false
)

@Serializable
data class ChatViewResponse(
    val success: Boolean = false,
    val cleanNumber: String = "",
    val contactName: String = "",
    val lastSeen: Long = 0L,
    val lastSeenFormatted: String = "",
    val totalMessages: Int = 0,
    val messages: List<ChatMessageItem> = emptyList(),
    val error: String? = null
)

@Serializable
data class SendTextRequest(
    val recipient: String,
    val text: String
)

@Serializable
data class SendTextResponse(
    val success: Boolean = false,
    val id: String? = null,
    val recipient: String? = null,
    val text: String? = null,
    val status: Int = 2,
    val timestamp: Long? = null,
    val error: String? = null
)

@Serializable
data class DownloadAndSendRequest(
    val recipient: String,
    val platform: String,
    val query: String,
    val caption: String = "",
    val withButtons: Boolean = true
)

@Serializable
data class DownloadAndSendResponse(
    val success: Boolean = false,
    val id: String? = null,
    val platform: String? = null,
    val mediaType: String? = null,
    val title: String? = null,
    val buttons: List<String> = emptyList(),
    val status: Int = 2,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class GiphyItem(
    val id: String = "",
    val title: String = "",
    val previewUrl: String = "",
    val mp4Url: String = ""
)

@Serializable
data class GiphySearchResponse(
    val success: Boolean = false,
    val results: List<GiphyItem> = emptyList(),
    val error: String? = null
)

@Serializable
data class SendMediaRequest(
    val recipient: String,
    val type: String, // "ptv" or "voice"
    val mediaData: String, // Base64
    val mimetype: String
)

@Serializable
data class SendMediaResponse(
    val success: Boolean = false,
    val simulated: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class SendLocationRequest(
    val recipient: String,
    val latitude: Double,
    val longitude: Double,
    val durationSeconds: Int = 900,
    val accuracy: Int = 15,
    val speed: Double = 0.0
)

@Serializable
data class SendLocationResponse(
    val success: Boolean = false,
    val trackingId: String? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class InteractiveButtonPayload(
    val id: String,
    val text: String,
    val type: String = "reply"
)

@Serializable
data class SendButtonsRequest(
    val recipient: String,
    val title: String = "",
    val body: String,
    val buttons: List<InteractiveButtonPayload>
)

@Serializable
data class SendButtonsResponse(
    val success: Boolean = false,
    val simulated: Boolean = false,
    val message: String? = null,
    val buttonCount: Int = 0,
    val error: String? = null
)

@Serializable
data class GroupItem(
    val jid: String = "",
    val name: String = "",
    val participantsCount: Int = 0,
    val description: String = "",
    val creation: Long = 0L
)

@Serializable
data class GroupsListResponse(
    val success: Boolean = false,
    val connected: Boolean = false,
    val groups: List<GroupItem> = emptyList(),
    val error: String? = null
)

@Serializable
data class ChannelItem(
    val id: String = "",
    val jid: String = "",
    val name: String = "",
    val emojis: List<String> = emptyList(),
    val active: Boolean = true
)

@Serializable
data class ChannelsListResponse(
    val success: Boolean = false,
    val channels: List<ChannelItem> = emptyList(),
    val error: String? = null
)

@Serializable
data class CallRecordItem(
    val id: String = "",
    val callerJid: String = "",
    val callerName: String = "",
    val callerNumber: String = "",
    val isVideo: Boolean = false,
    val status: String = "missed",
    val timestamp: Long = 0L
)

@Serializable
data class CallsListResponse(
    val success: Boolean = false,
    val calls: List<CallRecordItem> = emptyList()
)

@Serializable
data class CapturedStatusItem(
    val id: String = "",
    val senderName: String = "",
    val senderNumber: String = "",
    val mediaType: String = "text",
    val caption: String = "",
    val timestamp: Long = 0L
)

@Serializable
data class CapturedStatusesResponse(
    val success: Boolean = false,
    val statuses: List<CapturedStatusItem> = emptyList()
)

@Serializable
data class StatusUploadRequest(
    val type: String = "text", // text, image, video, voice
    val text: String = "",
    val caption: String = "",
    val backgroundColor: String = "#075E54",
    val mediaData: String? = null,
    val mimetype: String? = null
)

@Serializable
data class GenericActionResponse(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class VaultItem(
    val id: String = "",
    val type: String = "deleted",
    val senderName: String = "",
    val senderNumber: String = "",
    val text: String = "",
    val mediaType: String = "text",
    val timestamp: Long = 0L
)

@Serializable
data class VaultItemsResponse(
    val success: Boolean = false,
    val items: List<VaultItem> = emptyList()
)

@Serializable
data class VaultSettingsPayload(
    val antiDeleteEnabled: Boolean = true,
    val viewOnceEnabled: Boolean = true,
    val autoForwardToOwner: Boolean = false
)

@Serializable
data class AutoReactConfigPayload(
    val enabled: Boolean = true,
    val autoViewStatus: Boolean = true,
    val autoReactStatus: Boolean = true,
    val autoReplyStatus: Boolean = false,
    val emoji: String = "💚",
    val replyMessage: String = "Nice status!"
)

@Serializable
data class ChannelRuleRequest(
    val channelUrl: String,
    val channelName: String = "",
    val emojis: List<String> = listOf("❤️", "🔥", "👍")
)

@Serializable
data class FullProfilePicRequest(
    val targetType: String = "personal",
    val groupJid: String = "",
    val imageData: String
)

@Serializable
data class ContactItem(
    val number: String = "",
    val jid: String = "",
    val name: String = ""
)

@Serializable
data class ContactsListResponse(
    val success: Boolean = false,
    val totalContacts: Int = 0,
    val contacts: List<ContactItem> = emptyList()
)

@Serializable
data class CloudSyncResponse(
    val success: Boolean = false,
    val message: String? = null,
    val configured: Boolean = false,
    val error: String? = null
)

@Serializable
data class TruecallerDetails(
    val found: Boolean = false,
    val name: String = "",
    val carrier: String = "",
    val country: String = "",
    val address: String = "",
    val email: String = "",
    val spamScore: String = ""
)

@Serializable
data class SimRecordItem(
    val name: String = "",
    val number: String = "",
    val cnic: String = "",
    val address: String = "",
    val network: String = ""
)

@Serializable
data class WhatsAppProfileInfo(
    val registered: Boolean = false,
    val about: String = "",
    val profilePicUrl: String = "",
    val contactName: String = ""
)

@Serializable
data class NumberLookupResponse(
    val success: Boolean = false,
    val query: String = "",
    val formattedNumber: String = "",
    val localNumber: String = "",
    val operator: String = "",
    val truecaller: TruecallerDetails = TruecallerDetails(),
    val simRecords: List<SimRecordItem> = emptyList(),
    val whatsapp: WhatsAppProfileInfo = WhatsAppProfileInfo(),
    val error: String? = null
)

@Serializable
data class PresenceResponse(
    val success: Boolean = false,
    val jid: String = "",
    val lastSeen: Long = 0L,
    val lastSeenFormatted: String = "",
    val error: String? = null
)

@Serializable
data class LoadOlderChatsRequest(
    val jid: String = "",
    val oldestMsgId: String = "",
    val oldestTimestamp: Long = 0L,
    val fromMe: Boolean = false
)

@Serializable
data class LoadOlderChatsResponse(
    val success: Boolean = false,
    val fetched: Boolean = false,
    val message: String? = null,
    val error: String? = null
)
