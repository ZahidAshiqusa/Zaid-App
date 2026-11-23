package com.zaidashiq.chat.data.local

import android.content.Context
import com.zaidashiq.chat.data.model.ChatMessageItem
import com.zaidashiq.chat.data.model.ChatSummaryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
private data class LocalChatDatabaseSnapshot(
    val chats: List<ChatSummaryItem> = emptyList(),
    val messagesByChat: Map<String, List<ChatMessageItem>> = emptyMap()
)

/**
 * Local-only on-device chat & message storage (saved in app-private internal storage, NOT in cloud).
 * Keeps conversations fast, offline-readable, deduplicated, and respects Clear All Old Chats timestamps.
 */
class LocalChatStore(context: Context) {

    private val storageFile = File(context.filesDir, "aujla_local_chats.json")
    private val prefs = context.getSharedPreferences("aujla_local_chat_meta", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private var snapshot: LocalChatDatabaseSnapshot = loadFromDiskSync()

    private fun loadFromDiskSync(): LocalChatDatabaseSnapshot {
        return try {
            if (!storageFile.exists()) {
                LocalChatDatabaseSnapshot()
            } else {
                val raw = storageFile.readText(Charsets.UTF_8)
                if (raw.isBlank()) LocalChatDatabaseSnapshot()
                else json.decodeFromString<LocalChatDatabaseSnapshot>(raw)
            }
        } catch (_: Exception) {
            LocalChatDatabaseSnapshot()
        }
    }

    private fun saveToDiskSync(data: LocalChatDatabaseSnapshot) {
        try {
            val encoded = json.encodeToString(LocalChatDatabaseSnapshot.serializer(), data)
            val tmpFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
            tmpFile.writeText(encoded, Charsets.UTF_8)
            if (!tmpFile.renameTo(storageFile)) {
                storageFile.writeText(encoded, Charsets.UTF_8)
                tmpFile.delete()
            }
        } catch (_: Exception) {
            // Keep running safely in memory if storage write fails
        }
    }

    fun normalizeChatKey(jidOrNumber: String): String {
        val raw = jidOrNumber.trim()
        if (raw.isBlank()) return ""
        if (raw.endsWith("@g.us") || raw.endsWith("@newsletter")) return raw
        val digits = raw.substringBefore("@").substringBefore(":").filter { it.isDigit() }
        return digits.ifBlank { raw }
    }

    private fun normalizeTimestampMs(ts: Long): Long {
        if (ts <= 0L) return 0L
        return if (ts < 1_000_000_000_000L) ts * 1000L else ts
    }

    fun hasCompletedInitialOldChatClear(): Boolean =
        prefs.getBoolean(KEY_INITIAL_CLEAR_V1, false)

    fun markInitialOldChatClearDone(clearedAtMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(KEY_INITIAL_CLEAR_V1, true)
            .putLong(KEY_CLEARED_AT_MS, clearedAtMs)
            .apply()
    }

    fun getClearedCutoffMs(): Long = prefs.getLong(KEY_CLEARED_AT_MS, 0L)

    fun resetClearedCutoffForOlderSync() {
        prefs.edit().putLong(KEY_CLEARED_AT_MS, 0L).apply()
    }

    suspend fun clearAllLocalChats(recordCutoffNow: Boolean = true) = withContext(Dispatchers.IO) {
        mutex.withLock {
            snapshot = LocalChatDatabaseSnapshot()
            saveToDiskSync(snapshot)
            if (recordCutoffNow) {
                prefs.edit()
                    .putBoolean(KEY_INITIAL_CLEAR_V1, true)
                    .putLong(KEY_CLEARED_AT_MS, System.currentTimeMillis())
                    .apply()
            }
        }
    }

    suspend fun clearSingleChatLocally(jidOrNumber: String) = withContext(Dispatchers.IO) {
        val key = normalizeChatKey(jidOrNumber)
        if (key.isBlank()) return@withContext
        mutex.withLock {
            val updatedMessages = snapshot.messagesByChat.toMutableMap().apply {
                remove(key)
            }
            val updatedChats = snapshot.chats.map { chat ->
                if (normalizeChatKey(chat.resolvedJid) == key) {
                    chat.copy(lastMessage = "Chat cleared locally", messageCount = 0, unreadCount = 0)
                } else chat
            }
            snapshot = snapshot.copy(chats = updatedChats, messagesByChat = updatedMessages)
            saveToDiskSync(snapshot)
        }
    }

    suspend fun getLocalChats(): List<ChatSummaryItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            snapshot.chats
                .distinctBy { normalizeChatKey(it.resolvedJid.ifBlank { it.resolvedPhone }) }
                .sortedByDescending { normalizeTimestampMs(it.lastTimestamp) }
        }
    }

    suspend fun getLocalMessages(jidOrNumber: String): List<ChatMessageItem> = withContext(Dispatchers.IO) {
        val key = normalizeChatKey(jidOrNumber)
        if (key.isBlank()) return@withContext emptyList()
        mutex.withLock {
            snapshot.messagesByChat[key].orEmpty()
                .distinctBy { it.id.ifBlank { "${it.timestamp}_${it.text.hashCode()}" } }
                .sortedBy { normalizeTimestampMs(it.timestamp) }
        }
    }

    /**
     * Merges fresh server chats into local storage while filtering out chats older than the
     * user's "Clear All Old Chats" cutoff timestamp so cleared old chats stay gone.
     */
    suspend fun mergeServerChats(serverChats: List<ChatSummaryItem>): List<ChatSummaryItem> = withContext(Dispatchers.IO) {
        val cutoff = getClearedCutoffMs()
        mutex.withLock {
            val map = linkedMapOf<String, ChatSummaryItem>()
            for (existing in snapshot.chats) {
                val key = normalizeChatKey(existing.resolvedJid.ifBlank { existing.resolvedPhone })
                if (key.isNotBlank()) map[key] = existing
            }
            for (incoming in serverChats) {
                val key = normalizeChatKey(incoming.resolvedJid.ifBlank { incoming.resolvedPhone })
                if (key.isBlank()) continue
                val incomingTs = normalizeTimestampMs(incoming.lastTimestamp)
                if (cutoff > 0L && incomingTs in 1L..cutoff) {
                    continue
                }
                val prev = map[key]
                if (prev == null || incomingTs >= normalizeTimestampMs(prev.lastTimestamp)) {
                    map[key] = incoming.copy(
                        contactName = incoming.contactName.ifBlank { prev?.contactName.orEmpty() },
                        name = incoming.name.ifBlank { prev?.name.orEmpty() }
                    )
                }
            }
            val mergedList = map.values
                .sortedByDescending { normalizeTimestampMs(it.lastTimestamp) }
                .take(250)
            snapshot = snapshot.copy(chats = mergedList)
            saveToDiskSync(snapshot)
            mergedList
        }
    }

    /**
     * Merges fresh server messages for a chat into local device storage, filtering out messages
     * older than the user's Clear All cutoff and preserving every locally saved message.
     */
    suspend fun mergeServerMessages(
        jidOrNumber: String,
        serverMessages: List<ChatMessageItem>,
        contactName: String = ""
    ): List<ChatMessageItem> = withContext(Dispatchers.IO) {
        val key = normalizeChatKey(jidOrNumber)
        if (key.isBlank()) return@withContext emptyList()
        val cutoff = getClearedCutoffMs()
        mutex.withLock {
            val byId = linkedMapOf<String, ChatMessageItem>()
            for (existing in snapshot.messagesByChat[key].orEmpty()) {
                if (existing.pending) continue
                val msgKey = existing.id.ifBlank { "${existing.timestamp}_${existing.text.hashCode()}" }
                byId[msgKey] = existing
            }
            for (incoming in serverMessages) {
                val ts = normalizeTimestampMs(incoming.timestamp)
                if (cutoff > 0L && ts in 1L..cutoff) continue
                val msgKey = incoming.id.ifBlank { "${incoming.timestamp}_${incoming.text.hashCode()}" }
                val prev = byId[msgKey]
                byId[msgKey] = if (prev != null) {
                    incoming.copy(
                        status = maxOf(prev.status, incoming.status),
                        buttons = incoming.buttons.ifEmpty { prev.buttons },
                        pending = false
                    )
                } else {
                    incoming.copy(pending = false)
                }
            }
            val merged = byId.values
                .sortedBy { normalizeTimestampMs(it.timestamp) }
                .takeLast(600)

            val updatedMap = snapshot.messagesByChat.toMutableMap()
            updatedMap[key] = merged

            val updatedChats = upsertChatSummaryInList(
                chats = snapshot.chats,
                jidOrNumber = jidOrNumber,
                key = key,
                contactName = contactName,
                latestMsg = merged.lastOrNull(),
                messageCount = merged.size
            )

            snapshot = snapshot.copy(chats = updatedChats, messagesByChat = updatedMap)
            saveToDiskSync(snapshot)
            merged
        }
    }

    /**
     * Persists a newly sent/delivered message (text, song, video, number report, etc.) locally
     * immediately so the chat & Home list update instantaneously with zero cloud dependency.
     */
    suspend fun saveDeliveredMessage(
        jidOrNumber: String,
        message: ChatMessageItem,
        contactName: String = ""
    ): Pair<List<ChatSummaryItem>, List<ChatMessageItem>> = withContext(Dispatchers.IO) {
        val key = normalizeChatKey(jidOrNumber)
        if (key.isBlank()) return@withContext (snapshot.chats to emptyList())
        mutex.withLock {
            val byId = linkedMapOf<String, ChatMessageItem>()
            for (existing in snapshot.messagesByChat[key].orEmpty()) {
                if (existing.pending) continue
                val msgKey = existing.id.ifBlank { "${existing.timestamp}_${existing.text.hashCode()}" }
                byId[msgKey] = existing
            }
            val cleanMsg = message.copy(pending = false)
            val msgKey = cleanMsg.id.ifBlank { "${cleanMsg.timestamp}_${cleanMsg.text.hashCode()}" }
            byId[msgKey] = cleanMsg

            val merged = byId.values
                .sortedBy { normalizeTimestampMs(it.timestamp) }
                .takeLast(600)

            val updatedMap = snapshot.messagesByChat.toMutableMap()
            updatedMap[key] = merged

            val updatedChats = upsertChatSummaryInList(
                chats = snapshot.chats,
                jidOrNumber = jidOrNumber,
                key = key,
                contactName = contactName,
                latestMsg = merged.lastOrNull() ?: cleanMsg,
                messageCount = merged.size
            )

            snapshot = snapshot.copy(chats = updatedChats, messagesByChat = updatedMap)
            saveToDiskSync(snapshot)
            updatedChats to merged
        }
    }

    private fun upsertChatSummaryInList(
        chats: List<ChatSummaryItem>,
        jidOrNumber: String,
        key: String,
        contactName: String,
        latestMsg: ChatMessageItem?,
        messageCount: Int
    ): List<ChatSummaryItem> {
        if (latestMsg == null) return chats
        val isGroup = jidOrNumber.endsWith("@g.us")
        val normalizedJid = if (jidOrNumber.contains("@")) jidOrNumber else "$key@s.whatsapp.net"
        val map = linkedMapOf<String, ChatSummaryItem>()
        for (c in chats) {
            val ck = normalizeChatKey(c.resolvedJid.ifBlank { c.resolvedPhone })
            if (ck.isNotBlank()) map[ck] = c
        }
        val prev = map[key]
        val resolvedName = contactName.ifBlank {
            prev?.contactName?.ifBlank { prev.name }.orEmpty()
        }.ifBlank { if (isGroup) normalizedJid.substringBefore("@") else "+$key" }

        map[key] = ChatSummaryItem(
            number = key,
            cleanNumber = key,
            jid = normalizedJid,
            contactName = resolvedName,
            name = resolvedName,
            lastMessage = latestMsg.text.ifBlank { "[${latestMsg.mediaType}]" },
            lastMessageType = latestMsg.mediaType.ifBlank { "text" },
            lastTimestamp = maxOf(normalizeTimestampMs(latestMsg.timestamp), normalizeTimestampMs(prev?.lastTimestamp ?: 0L)),
            messageCount = messageCount,
            unreadCount = 0,
            isGroup = isGroup
        )
        return map.values
            .sortedByDescending { normalizeTimestampMs(it.lastTimestamp) }
            .take(250)
    }

    suspend fun exportLocalChatTranscript(jidOrNumber: String, fallbackName: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val msgs = getLocalMessages(jidOrNumber)
            val key = normalizeChatKey(jidOrNumber)
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val sb = StringBuilder()
            sb.appendLine("=== Aujla Chat App — Local Device Backup ===")
            sb.appendLine("Contact: ${fallbackName.ifBlank { "+$key" }} (+$key)")
            sb.appendLine("Storage: Saved Locally On Device (Not in Cloud)")
            sb.appendLine("Exported: ${fmt.format(Date())}")
            sb.appendLine("Messages: ${msgs.size}")
            sb.appendLine("============================================")
            sb.appendLine()
            if (msgs.isEmpty()) {
                sb.appendLine("(No local messages recorded in this chat yet)")
            } else {
                msgs.forEach { m ->
                    val stamp = fmt.format(Date(normalizeTimestampMs(m.timestamp).takeIf { it > 0L } ?: System.currentTimeMillis()))
                    val who = if (m.fromMe) "You" else (m.senderName.ifBlank { fallbackName })
                    val tag = if (m.mediaType.isNotBlank() && m.mediaType != "text") "[${m.mediaType.uppercase()}] " else ""
                    sb.appendLine("[$stamp] $who: $tag${m.text}")
                }
            }
            msgs.size to sb.toString()
        }

    suspend fun exportLocalFullBackup(): Triple<Int, Int, String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val chats = snapshot.chats.sortedByDescending { normalizeTimestampMs(it.lastTimestamp) }
            val totalMsgs = snapshot.messagesByChat.values.sumOf { it.size }
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val sb = StringBuilder()
            sb.appendLine("=== Aujla Chat App — Full Local Device Backup ===")
            sb.appendLine("Storage: Saved Locally On Device (Not in Cloud)")
            sb.appendLine("Generated: ${fmt.format(Date())}")
            sb.appendLine("Total Local Chats: ${chats.size}")
            sb.appendLine("Total Local Messages: $totalMsgs")
            sb.appendLine("=================================================")
            sb.appendLine()
            if (chats.isEmpty()) {
                sb.appendLine("(No local chats stored yet)")
            } else {
                chats.take(50).forEachIndexed { idx, c ->
                    sb.appendLine("${idx + 1}. ${c.resolvedName} (+${c.resolvedPhone}) — ${c.messageCount} msgs | Last: ${c.lastMessage.take(60)}")
                }
            }
            Triple(chats.size, totalMsgs, sb.toString())
        }
    }

    companion object {
        private const val KEY_INITIAL_CLEAR_V1 = "old_chats_cleared_v1"
        private const val KEY_CLEARED_AT_MS = "old_chats_cleared_cutoff_ms"
    }
}
