package com.zaidashiq.chat.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaidashiq.chat.data.local.SessionPreferences
import com.zaidashiq.chat.data.model.*
import com.zaidashiq.chat.data.network.BackendConfig
import com.zaidashiq.chat.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class HomeTab(val label: String) {
    CHATS("CHATS"),
    STATUS("STATUS"),
    CALLS("CALLS"),
    GROUPS("GROUPS"),
    CHANNELS("CHANNELS")
}

enum class ChatToolType(val title: String, val platformKey: String, val hint: String) {
    VIDEO_NOTE("Video Note", "ptv", "Send circular WhatsApp video note"),
    VOICE_NOTE("Voice Note", "voice", "Record or pick audio as voice note"),
    FAKE_LOCATION("Fake Location", "location", "Send custom GPS pin or live location"),
    BUTTON_MESSAGES("Button Messages", "buttons", "Create interactive reply buttons"),
    SONG("Song", "song", "Enter song name or YouTube URL (sends MP3 audio)"),
    VIDEO("Video", "video", "Enter video title or YouTube URL (sends MP4)"),
    TIKTOK("TikTok", "tiktok", "Paste TikTok video link (sends video, not link)"),
    FACEBOOK("Facebook", "facebook", "Paste Facebook Reel or video link (sends video)"),
    INSTAGRAM("Instagram", "instagram", "Paste Instagram Reel or Post link (sends media)"),
    DOWNLOADER("Downloader", "downloader", "Paste any media link or search GIFs via Giphy")
}

private val SONG_LINK_REGEX = Regex(
    "^https?://(?:" +
        "(?:www\\.|m\\.|music\\.)?youtube\\.com/(?:watch\\?[^\\s]*v=[\\w-]+|shorts/[\\w-]+|live/[\\w-]+)[^\\s]*|" +
        "youtu\\.be/[\\w-]+[^\\s]*|" +
        "open\\.spotify\\.com/track/[\\w-]+[^\\s]*|" +
        "spotify\\.link/[\\w-]+[^\\s]*|" +
        "(?:www\\.|m\\.|on\\.)?soundcloud\\.com/[^\\s]+|" +
        "[^\\s]+\\.(?:mp3|m4a|aac|ogg|opus|wav)(?:\\?[^\\s]*)?" +
        ")$",
    RegexOption.IGNORE_CASE
)

fun isDirectSongLink(input: String): Boolean {
    val clean = input.trim()
    return clean.isNotEmpty() && SONG_LINK_REGEX.matches(clean)
}

enum class UploadStage {
    IDLE,
    PREPARING,
    UPLOADING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class StatusUploadProgress(
    val stage: UploadStage = UploadStage.IDLE,
    val mediaType: String = "",
    val mediaLabel: String = "",
    val fileName: String = "",
    val bytesUploaded: Long = 0L,
    val totalBytes: Long = 0L,
    val statusMessage: String = ""
) {
    val isVisible: Boolean
        get() = stage != UploadStage.IDLE

    val isActive: Boolean
        get() = stage == UploadStage.PREPARING ||
            stage == UploadStage.UPLOADING ||
            stage == UploadStage.PROCESSING

    val fraction: Float?
        get() = when {
            stage == UploadStage.COMPLETED || stage == UploadStage.PROCESSING -> 1f
            totalBytes > 0L -> (bytesUploaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            else -> null
        }

    val percent: Int
        get() = ((fraction ?: 0f) * 100f).toInt().coerceIn(0, 100)
}

data class MainUiState(
    val isInitialized: Boolean = false,
    val isLinked: Boolean = false,
    val linkedPhone: String = BackendConfig.defaultOwnerNumber,
    val backendHealthy: Boolean = false,
    val backendUptime: String = "",
    val pairingCode: String? = null,
    val isRequestingCode: Boolean = false,
    val isWaitingForLink: Boolean = false,
    val pairingError: String? = null,
    val selectedTab: HomeTab = HomeTab.CHATS,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isRefreshingHome: Boolean = false,
    val chats: List<ChatSummaryItem> = emptyList(),
    val contacts: List<ContactItem> = emptyList(),
    val groups: List<GroupItem> = emptyList(),
    val channels: List<ChannelItem> = emptyList(),
    val calls: List<CallRecordItem> = emptyList(),
    val capturedStatuses: List<CapturedStatusItem> = emptyList(),
    val statusUploadProgress: StatusUploadProgress = StatusUploadProgress(),
    val activeChatMessages: List<ChatMessageItem> = emptyList(),
    val activeChatLastSeen: String = "",
    val isLoadingChat: Boolean = false,
    val isLoadingOlderChats: Boolean = false,
    val isSendingMessage: Boolean = false,
    val isProcessingTool: Boolean = false,
    val toolStatusText: String? = null,
    val activeMediaTool: ChatToolType? = null,
    val activeMediaQuery: String = "",
    val giphyResults: List<GiphyItem> = emptyList(),
    val isSearchingGiphy: Boolean = false,
    val isLookingUpNumber: Boolean = false,
    val numberLookupResult: NumberLookupResponse? = null,
    val numberLookupError: String? = null,
    val vaultItems: List<VaultItem> = emptyList(),
    val vaultSettings: VaultSettingsPayload = VaultSettingsPayload(),
    val autoReactConfig: AutoReactConfigPayload = AutoReactConfigPayload(),
    val bannerNotice: String? = null,
    val snackbarMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionPrefs = SessionPreferences(application.applicationContext)
    private val repository = ChatRepository(application.applicationContext, sessionPrefs)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var pairingPollJob: Job? = null
    private var activeChatPollJob: Job? = null

    init {
        viewModelScope.launch {
            sessionPrefs.sessionFlow.collect { stored ->
                _uiState.update { state ->
                    state.copy(
                        isInitialized = true,
                        isLinked = stored.isLinked || state.isLinked,
                        linkedPhone = stored.pairedPhone.ifBlank { state.linkedPhone }
                    )
                }
            }
        }
        refreshBackendStatus()
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun setTab(tab: HomeTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            HomeTab.CHATS -> loadChats()
            HomeTab.STATUS -> loadStatuses()
            HomeTab.CALLS -> loadCalls()
            HomeTab.GROUPS -> loadGroups()
            HomeTab.CHANNELS -> loadChannels()
        }
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (!active) "" else it.searchQuery
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun refreshBackendStatus() {
        if (!BackendConfig.isRailwayConfigured) {
            _uiState.update {
                it.copy(
                    backendHealthy = false,
                    bannerNotice = "Service Not Available"
                )
            }
            return
        }
        viewModelScope.launch {
            repository.getBotStatus().onSuccess { status ->
                val connectedPhone = status.connectedSessions.firstOrNull().orEmpty()
                if (connectedPhone.isNotBlank()) {
                    sessionPrefs.saveLinkedSession(connectedPhone)
                }
                _uiState.update { state ->
                    state.copy(
                        backendHealthy = true,
                        backendUptime = status.uptimeFormatted,
                        isLinked = state.isLinked || connectedPhone.isNotBlank(),
                        linkedPhone = connectedPhone.ifBlank { state.linkedPhone },
                        bannerNotice = null
                    )
                }
                if (connectedPhone.isNotBlank()) {
                    loadAllHomeData()
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        backendHealthy = false,
                        bannerNotice = "Service Not Available"
                    )
                }
            }
        }
    }

    fun requestPairingCode(rawPhone: String) {
        val clean = rawPhone.filter { it.isDigit() }
        if (clean.length < 7) {
            _uiState.update { it.copy(pairingError = "Enter a valid phone number with country code (e.g. 923001234567).") }
            return
        }

        _uiState.update {
            it.copy(
                isRequestingCode = true,
                pairingError = null,
                pairingCode = null
            )
        }

        viewModelScope.launch {
            repository.requestPairingCode(clean).onSuccess { res ->
                if (res.alreadyConnected) {
                    sessionPrefs.saveLinkedSession(res.cleanNumber ?: clean)
                    _uiState.update {
                        it.copy(
                            isRequestingCode = false,
                            isWaitingForLink = false,
                            isLinked = true,
                            linkedPhone = res.cleanNumber ?: clean,
                            snackbarMessage = res.message ?: "WhatsApp already linked!"
                        )
                    }
                    loadAllHomeData()
                } else if (!res.code.isNullOrBlank()) {
                    val formatted = formatPairingCode(res.code)
                    _uiState.update {
                        it.copy(
                            isRequestingCode = false,
                            pairingCode = formatted,
                            isWaitingForLink = true,
                            linkedPhone = res.cleanNumber ?: clean
                        )
                    }
                    startPairingPoll(res.cleanNumber ?: clean)
                } else {
                    _uiState.update {
                        it.copy(
                            isRequestingCode = false,
                            pairingError = res.error ?: "Unable to generate pairing code."
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isRequestingCode = false,
                        pairingError = err.message ?: "Failed to connect to Railway backend."
                    )
                }
            }
        }
    }

    private fun formatPairingCode(raw: String): String {
        val clean = raw.replace("-", "").trim().uppercase()
        return if (clean.length == 8) "${clean.substring(0, 4)}-${clean.substring(4)}" else raw
    }

    private fun startPairingPoll(targetPhone: String) {
        pairingPollJob?.cancel()
        pairingPollJob = viewModelScope.launch {
            while (isActive) {
                delay(2500L)
                repository.getBotStatus().onSuccess { status ->
                    val matched = status.connectedSessions.firstOrNull {
                        it.contains(targetPhone) || targetPhone.contains(it)
                    } ?: status.connectedSessions.firstOrNull()
                    if (!matched.isNullOrBlank()) {
                        sessionPrefs.saveLinkedSession(matched)
                        _uiState.update {
                            it.copy(
                                isLinked = true,
                                isWaitingForLink = false,
                                backendHealthy = true,
                                linkedPhone = matched,
                                snackbarMessage = "WhatsApp linked successfully (+$matched)"
                            )
                        }
                        loadAllHomeData()
                        pairingPollJob?.cancel()
                    }
                }
            }
        }
    }

    fun unlinkAndRepair() {
        pairingPollJob?.cancel()
        viewModelScope.launch {
            sessionPrefs.clearLinkedSession()
            _uiState.update {
                it.copy(
                    isLinked = false,
                    pairingCode = null,
                    isWaitingForLink = false,
                    pairingError = null
                )
            }
        }
    }

    fun loadAllHomeData() {
        loadChats()
        loadStatuses()
        loadGroups()
        loadChannels()
        loadCalls()
    }

    fun loadChats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingHome = true) }
            repository.getChats().onSuccess { res ->
                _uiState.update { it.copy(chats = res.chats, isRefreshingHome = false, backendHealthy = true) }
            }.onFailure {
                _uiState.update { it.copy(isRefreshingHome = false) }
            }
            repository.getContacts().onSuccess { res ->
                _uiState.update { it.copy(contacts = res.contacts) }
            }
        }
    }

    fun loadStatuses() {
        viewModelScope.launch {
            repository.getCapturedStatuses().onSuccess { res ->
                _uiState.update { it.copy(capturedStatuses = res.statuses) }
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            repository.getGroups().onSuccess { res ->
                _uiState.update { it.copy(groups = res.groups) }
            }
        }
    }

    fun loadChannels() {
        viewModelScope.launch {
            repository.getChannels().onSuccess { res ->
                _uiState.update { it.copy(channels = res.channels) }
            }
        }
    }

    fun loadCalls() {
        viewModelScope.launch {
            repository.getRecentCalls().onSuccess { res ->
                _uiState.update { it.copy(calls = res.calls) }
            }
        }
    }

    fun startChatPolling(recipientPhoneOrJid: String) {
        activeChatPollJob?.cancel()
        val key = recipientPhoneOrJid.substringBefore("@")
        _uiState.update {
            it.copy(
                isLoadingChat = true,
                activeChatMessages = emptyList(),
                activeChatLastSeen = ""
            )
        }
        if (!recipientPhoneOrJid.endsWith("@g.us")) {
            viewModelScope.launch {
                repository.getChatPresence(recipientPhoneOrJid).onSuccess { pres ->
                    if (pres.lastSeenFormatted.isNotBlank()) {
                        _uiState.update { it.copy(activeChatLastSeen = pres.lastSeenFormatted) }
                    }
                }
            }
        }
        activeChatPollJob = viewModelScope.launch {
            while (isActive) {
                repository.getChatMessages(key).onSuccess { res ->
                    _uiState.update { state ->
                        val pendingLocal = state.activeChatMessages.filter { it.pending }
                        val serverIds = res.messages.map { it.id }.toSet()
                        val merged = res.messages + pendingLocal.filterNot { it.id in serverIds }
                        state.copy(
                            activeChatMessages = merged,
                            activeChatLastSeen = res.lastSeenFormatted.ifBlank { state.activeChatLastSeen },
                            isLoadingChat = false
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoadingChat = false) }
                }
                delay(4000L)
            }
        }
    }

    fun stopChatPolling() {
        activeChatPollJob?.cancel()
        activeChatPollJob = null
        _uiState.update { it.copy(activeChatLastSeen = "") }
    }

    fun loadOlderMessages(recipientJid: String) {
        if (_uiState.value.isLoadingOlderChats) return
        val oldest = _uiState.value.activeChatMessages.firstOrNull()
        _uiState.update { it.copy(isLoadingOlderChats = true) }
        viewModelScope.launch {
            repository.loadOlderChats(
                jid = recipientJid,
                oldestMsgId = oldest?.id.orEmpty(),
                oldestTimestamp = oldest?.timestamp ?: 0L,
                fromMe = oldest?.fromMe ?: false
            ).onSuccess { res ->
                val key = recipientJid.substringBefore("@")
                repository.getChatMessages(key).onSuccess { viewRes ->
                    _uiState.update { state ->
                        state.copy(
                            activeChatMessages = viewRes.messages,
                            activeChatLastSeen = viewRes.lastSeenFormatted.ifBlank { state.activeChatLastSeen },
                            isLoadingOlderChats = false,
                            snackbarMessage = res.message ?: "Checked for older messages"
                        )
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(
                            isLoadingOlderChats = false,
                            snackbarMessage = res.message ?: "Older history synced"
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingOlderChats = false,
                        snackbarMessage = err.message ?: "Could not load older messages"
                    )
                }
            }
        }
    }

    fun loadOlderChatsList() {
        if (_uiState.value.isLoadingOlderChats) return
        _uiState.update { it.copy(isLoadingOlderChats = true, isRefreshingHome = true) }
        viewModelScope.launch {
            repository.loadOlderChats().onSuccess { res ->
                loadAllHomeData()
                _uiState.update {
                    it.copy(
                        isLoadingOlderChats = false,
                        snackbarMessage = res.message ?: "Synced older chats from WhatsApp"
                    )
                }
            }.onFailure { err ->
                loadChats()
                _uiState.update {
                    it.copy(
                        isLoadingOlderChats = false,
                        snackbarMessage = err.message ?: "Failed syncing older chats"
                    )
                }
            }
        }
    }

    fun lookupNumberDetails(phoneOrJid: String) {
        val clean = phoneOrJid.substringBefore("@").filter { it.isDigit() }
        if (clean.length < 7) {
            _uiState.update {
                it.copy(
                    isLookingUpNumber = false,
                    numberLookupError = "Enter a valid phone number or CNIC (at least 7 digits)."
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isLookingUpNumber = true,
                numberLookupError = null,
                numberLookupResult = null
            )
        }
        viewModelScope.launch {
            repository.lookupNumber(clean).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isLookingUpNumber = false,
                        numberLookupResult = res,
                        numberLookupError = if (!res.success) res.error ?: "No details found." else null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLookingUpNumber = false,
                        numberLookupError = err.message ?: "Number lookup failed."
                    )
                }
            }
        }
    }

    fun clearNumberLookup() {
        _uiState.update {
            it.copy(
                isLookingUpNumber = false,
                numberLookupResult = null,
                numberLookupError = null
            )
        }
    }

    fun sendTextMessage(recipientJid: String, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return

        // Intercept .play / .song / .video commands -> download & send direct media + buttons in chat!
        if (clean.equals(".play", ignoreCase = true) || clean.equals(".song", ignoreCase = true)) {
            _uiState.update {
                it.copy(snackbarMessage = "Usage: .play <song name or link> — or paste a song link directly!")
            }
            return
        }
        val playRegex = Regex("^\\.(?:play|song)\\s+(.+)$", RegexOption.IGNORE_CASE)
        playRegex.matchEntire(clean)?.let { match ->
            val songQuery = match.groupValues[1].trim()
            if (songQuery.isNotBlank()) {
                downloadAndSendMedia(
                    recipientJid = recipientJid,
                    tool = ChatToolType.SONG,
                    queryOrUrl = songQuery,
                    caption = ""
                )
                return
            }
        }
        val videoRegex = Regex("^\\.video\\s+(.+)$", RegexOption.IGNORE_CASE)
        videoRegex.matchEntire(clean)?.let { match ->
            val videoQuery = match.groupValues[1].trim()
            if (videoQuery.isNotBlank()) {
                downloadAndSendMedia(
                    recipientJid = recipientJid,
                    tool = ChatToolType.VIDEO,
                    queryOrUrl = videoQuery,
                    caption = ""
                )
                return
            }
        }

        // Auto-detect pasted Song Link directly in chat (YouTube, YT Music, Spotify, SoundCloud, direct MP3/M4A/OGG)
        if (isDirectSongLink(clean)) {
            downloadAndSendMedia(
                recipientJid = recipientJid,
                tool = ChatToolType.SONG,
                queryOrUrl = clean,
                caption = ""
            )
            return
        }

        val tempId = "local_${System.currentTimeMillis()}"
        val optimistic = ChatMessageItem(
            id = tempId,
            fromMe = true,
            text = clean,
            mediaType = "text",
            timestamp = System.currentTimeMillis(),
            status = 1,
            pending = true
        )

        _uiState.update {
            it.copy(
                activeChatMessages = it.activeChatMessages + optimistic,
                isSendingMessage = true
            )
        }

        viewModelScope.launch {
            repository.sendText(recipientJid, clean).onSuccess { res ->
                _uiState.update { state ->
                    state.copy(
                        isSendingMessage = false,
                        activeChatMessages = state.activeChatMessages.map { msg ->
                            if (msg.id == tempId) {
                                msg.copy(
                                    id = res.id ?: tempId,
                                    status = res.status.coerceAtLeast(2),
                                    pending = false
                                )
                            } else msg
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isSendingMessage = false,
                        activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                        snackbarMessage = err.message ?: "Failed to send message"
                    )
                }
            }
        }
    }

    fun downloadAndSendMedia(
        recipientJid: String,
        tool: ChatToolType,
        queryOrUrl: String,
        caption: String = "",
        onDone: () -> Unit = {}
    ) {
        val clean = queryOrUrl.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Please enter a link or search query") }
            return
        }

        val tempId = "pending_media_${System.currentTimeMillis()}"
        val processingLabel = when (tool) {
            ChatToolType.SONG -> "Processing Song: $clean"
            ChatToolType.VIDEO -> "Processing Video: $clean"
            ChatToolType.TIKTOK -> "Processing TikTok Video…"
            ChatToolType.FACEBOOK -> "Processing Facebook Video…"
            ChatToolType.INSTAGRAM -> "Processing Instagram Media…"
            else -> "Processing ${tool.title}…"
        }

        val pendingBubble = ChatMessageItem(
            id = tempId,
            fromMe = true,
            text = processingLabel,
            mediaType = tool.platformKey,
            timestamp = System.currentTimeMillis(),
            status = 1,
            pending = true
        )

        _uiState.update {
            it.copy(
                isProcessingTool = true,
                activeMediaTool = tool,
                activeMediaQuery = clean,
                toolStatusText = processingLabel,
                activeChatMessages = it.activeChatMessages + pendingBubble
            )
        }
        onDone()

        viewModelScope.launch {
            repository.downloadAndSend(
                recipient = recipientJid,
                platform = tool.platformKey,
                query = clean,
                caption = caption,
                withButtons = true
            ).onSuccess { res ->
                val deliveredBubble = ChatMessageItem(
                    id = res.id ?: "media_${System.currentTimeMillis()}",
                    fromMe = true,
                    text = res.title?.ifBlank { tool.title } ?: tool.title,
                    mediaType = res.mediaType ?: tool.platformKey,
                    buttons = res.buttons,
                    status = res.status.coerceAtLeast(2),
                    timestamp = System.currentTimeMillis(),
                    pending = false
                )
                _uiState.update { state ->
                    val replaced = state.activeChatMessages.map { msg ->
                        if (msg.id == tempId) deliveredBubble else msg
                    }
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        activeMediaQuery = "",
                        toolStatusText = null,
                        activeChatMessages = if (replaced.any { it.id == deliveredBubble.id }) replaced else replaced + deliveredBubble,
                        snackbarMessage = res.message ?: "${tool.title} sent directly!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        activeMediaQuery = "",
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                        snackbarMessage = err.message ?: "Download & send failed"
                    )
                }
            }
        }
    }

    fun sendGiphyGif(recipientJid: String, gif: GiphyItem, onDone: () -> Unit = {}) {
        val tempId = "pending_gif_${System.currentTimeMillis()}"
        val pendingBubble = ChatMessageItem(
            id = tempId,
            fromMe = true,
            text = "Processing GIF…",
            mediaType = "gif",
            timestamp = System.currentTimeMillis(),
            status = 1,
            pending = true
        )
        _uiState.update {
            it.copy(
                isProcessingTool = true,
                activeMediaTool = ChatToolType.DOWNLOADER,
                activeMediaQuery = gif.title.ifBlank { "GIF" },
                toolStatusText = "Processing GIF…",
                activeChatMessages = it.activeChatMessages + pendingBubble
            )
        }
        onDone()

        viewModelScope.launch {
            repository.downloadAndSend(
                recipient = recipientJid,
                platform = "giphy",
                query = gif.mp4Url
            ).onSuccess { res ->
                val bubble = ChatMessageItem(
                    id = res.id ?: "gif_${System.currentTimeMillis()}",
                    fromMe = true,
                    text = gif.title.ifBlank { "GIF" },
                    mediaType = "gif",
                    status = res.status.coerceAtLeast(2),
                    timestamp = System.currentTimeMillis(),
                    pending = false
                )
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        activeMediaQuery = "",
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.map { if (it.id == tempId) bubble else it },
                        snackbarMessage = "GIF sent!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        activeMediaQuery = "",
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                        snackbarMessage = err.message ?: "Failed to send GIF"
                    )
                }
            }
        }
    }

    fun searchGiphy(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingGiphy = true) }
            repository.searchGiphy(query).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isSearchingGiphy = false,
                        giphyResults = res.results,
                        snackbarMessage = if (res.results.isEmpty() && !res.error.isNullOrBlank()) res.error else it.snackbarMessage
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSearchingGiphy = false,
                        snackbarMessage = err.message ?: "Giphy search unavailable"
                    )
                }
            }
        }
    }

    fun sendVideoNoteUri(recipientJid: String, uri: Uri, onDone: () -> Unit = {}) {
        val tempId = "pending_ptv_${System.currentTimeMillis()}"
        val pendingBubble = ChatMessageItem(
            id = tempId,
            fromMe = true,
            text = "Processing Video Note…",
            mediaType = "ptv",
            timestamp = System.currentTimeMillis(),
            status = 1,
            pending = true
        )
        _uiState.update {
            it.copy(
                isProcessingTool = true,
                activeMediaTool = ChatToolType.VIDEO_NOTE,
                activeMediaQuery = "Circular Video Note",
                toolStatusText = "Processing Video Note…",
                activeChatMessages = it.activeChatMessages + pendingBubble
            )
        }
        onDone()

        viewModelScope.launch {
            repository.sendMediaFromUri(recipientJid, "ptv", uri, "video/mp4").onSuccess { res ->
                val bubble = ChatMessageItem(
                    id = "ptv_${System.currentTimeMillis()}",
                    fromMe = true,
                    text = "Video Note",
                    mediaType = "ptv",
                    status = 2,
                    timestamp = System.currentTimeMillis(),
                    pending = false
                )
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        activeMediaQuery = "",
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.map { if (it.id == tempId) bubble else it },
                        snackbarMessage = res.message ?: "Video Note sent!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        activeMediaQuery = "",
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                        snackbarMessage = err.message ?: "Failed to send Video Note"
                    )
                }
            }
        }
    }

    fun sendVoiceNoteUri(recipientJid: String, uri: Uri, onDone: () -> Unit = {}) {
        val tempId = "pending_ptt_${System.currentTimeMillis()}"
        val pendingBubble = ChatMessageItem(
            id = tempId,
            fromMe = true,
            text = "Processing Voice Note…",
            mediaType = "voice",
            timestamp = System.currentTimeMillis(),
            status = 1,
            pending = true
        )
        _uiState.update {
            it.copy(
                isProcessingTool = true,
                activeMediaTool = ChatToolType.VOICE_NOTE,
                toolStatusText = "Processing Voice Note…",
                activeChatMessages = it.activeChatMessages + pendingBubble
            )
        }
        onDone()

        viewModelScope.launch {
            repository.sendMediaFromUri(recipientJid, "voice", uri, "audio/ogg; codecs=opus").onSuccess { res ->
                val bubble = ChatMessageItem(
                    id = "ptt_${System.currentTimeMillis()}",
                    fromMe = true,
                    text = "Voice Note",
                    mediaType = "voice",
                    status = 2,
                    timestamp = System.currentTimeMillis(),
                    pending = false
                )
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.map { if (it.id == tempId) bubble else it },
                        snackbarMessage = res.message ?: "Voice Note sent!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                        snackbarMessage = err.message ?: "Failed to send Voice Note"
                    )
                }
            }
        }
    }

    fun sendRecordedVoiceFile(recipientJid: String, file: File, onDone: () -> Unit = {}) {
        val tempId = "pending_rec_${System.currentTimeMillis()}"
        val pendingBubble = ChatMessageItem(
            id = tempId,
            fromMe = true,
            text = "Processing Voice Note…",
            mediaType = "voice",
            timestamp = System.currentTimeMillis(),
            status = 1,
            pending = true
        )
        _uiState.update {
            it.copy(
                isProcessingTool = true,
                activeMediaTool = ChatToolType.VOICE_NOTE,
                toolStatusText = "Processing Voice Note…",
                activeChatMessages = it.activeChatMessages + pendingBubble
            )
        }
        onDone()

        viewModelScope.launch {
            repository.sendVoiceFile(recipientJid, file).onSuccess { res ->
                val bubble = ChatMessageItem(
                    id = "ptt_${System.currentTimeMillis()}",
                    fromMe = true,
                    text = "Voice Note",
                    mediaType = "voice",
                    status = 2,
                    timestamp = System.currentTimeMillis(),
                    pending = false
                )
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.map { if (it.id == tempId) bubble else it },
                        snackbarMessage = res.message ?: "Voice Note sent!"
                    )
                }
                file.delete()
            }.onFailure { err ->
                file.delete()
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                        snackbarMessage = err.message ?: "Failed to send Voice Note"
                    )
                }
            }
        }
    }

    fun sendFakeLocation(
        recipientJid: String,
        latitude: Double,
        longitude: Double,
        durationSeconds: Int,
        label: String,
        onDone: () -> Unit = {}
    ) {
        val tempId = "pending_loc_${System.currentTimeMillis()}"
        val pendingBubble = ChatMessageItem(
            id = tempId,
            fromMe = true,
            text = "Processing Fake Location ($label)…",
            mediaType = "location",
            timestamp = System.currentTimeMillis(),
            status = 1,
            pending = true
        )
        _uiState.update {
            it.copy(
                isProcessingTool = true,
                activeMediaTool = ChatToolType.FAKE_LOCATION,
                toolStatusText = "Processing Fake Location…",
                activeChatMessages = it.activeChatMessages + pendingBubble
            )
        }
        onDone()

        viewModelScope.launch {
            repository.sendFakeLocation(recipientJid, latitude, longitude, durationSeconds).onSuccess { res ->
                val bubble = ChatMessageItem(
                    id = "loc_${System.currentTimeMillis()}",
                    fromMe = true,
                    text = "Location: $label (${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)})",
                    mediaType = "location",
                    status = 2,
                    timestamp = System.currentTimeMillis(),
                    pending = false
                )
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.map { if (it.id == tempId) bubble else it },
                        snackbarMessage = res.message ?: "Fake Location sent!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        activeMediaTool = null,
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                        snackbarMessage = err.message ?: "Failed to send Fake Location"
                    )
                }
            }
        }
    }

    fun sendButtonMessage(
        recipientJid: String,
        body: String,
        buttons: List<String>,
        onSuccess: () -> Unit
    ) {
        val cleanedButtons = buttons.map { it.trim() }.filter { it.isNotBlank() }
        if (body.isBlank() || cleanedButtons.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Enter a message and at least one button.") }
            return
        }
        _uiState.update { it.copy(isProcessingTool = true) }
        viewModelScope.launch {
            repository.sendButtonMessage(recipientJid, body.trim(), cleanedButtons).onSuccess { res ->
                val bubble = ChatMessageItem(
                    id = "btn_${System.currentTimeMillis()}",
                    fromMe = true,
                    text = body.trim(),
                    mediaType = "buttons",
                    buttons = cleanedButtons,
                    timestamp = System.currentTimeMillis()
                )
                _uiState.update {
                    it.copy(
                        isProcessingTool = false,
                        activeChatMessages = it.activeChatMessages + bubble,
                        snackbarMessage = res.message ?: "Interactive button message sent!"
                    )
                }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessingTool = false,
                        snackbarMessage = err.message ?: "Failed to send button message"
                    )
                }
            }
        }
    }

    // Status Tab Actions
    fun dismissStatusUploadProgress() {
        _uiState.update { it.copy(statusUploadProgress = StatusUploadProgress()) }
    }

    fun uploadTextStatus(text: String, bgHex: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    statusUploadProgress = StatusUploadProgress(
                        stage = UploadStage.PROCESSING,
                        mediaType = "text",
                        mediaLabel = "Text Status",
                        statusMessage = "Publishing Text Status to WhatsApp…"
                    )
                )
            }
            repository.uploadTextStatus(text.trim(), bgHex).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        statusUploadProgress = StatusUploadProgress(
                            stage = UploadStage.COMPLETED,
                            mediaType = "text",
                            mediaLabel = "Text Status",
                            statusMessage = res.message ?: "Text status uploaded!"
                        ),
                        snackbarMessage = res.message ?: "Text status uploaded!"
                    )
                }
                loadStatuses()
                delay(3500L)
                _uiState.update { state ->
                    if (state.statusUploadProgress.stage == UploadStage.COMPLETED) {
                        state.copy(statusUploadProgress = StatusUploadProgress())
                    } else state
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        statusUploadProgress = StatusUploadProgress(
                            stage = UploadStage.FAILED,
                            mediaType = "text",
                            mediaLabel = "Text Status",
                            statusMessage = err.message ?: "Status upload failed"
                        ),
                        snackbarMessage = err.message ?: "Status upload failed"
                    )
                }
            }
        }
    }

    fun uploadMediaStatus(type: String, uri: Uri, caption: String, fallbackMime: String) {
        val label = when (type) {
            "video" -> "Video Status"
            "voice" -> "Voice Note Status"
            else -> "Photo Status"
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingTool = true,
                    statusUploadProgress = StatusUploadProgress(
                        stage = UploadStage.PREPARING,
                        mediaType = type,
                        mediaLabel = label,
                        statusMessage = "Preparing $label for upload…"
                    )
                )
            }
            repository.uploadMediaStatus(
                type = type,
                uri = uri,
                caption = caption,
                fallbackMime = fallbackMime,
                onMetaResolved = { fileName, totalBytes ->
                    _uiState.update { state ->
                        state.copy(
                            statusUploadProgress = state.statusUploadProgress.copy(
                                stage = UploadStage.UPLOADING,
                                fileName = fileName,
                                totalBytes = totalBytes,
                                statusMessage = "Uploading $label to Railway…"
                            )
                        )
                    }
                },
                onProgress = { uploaded, total ->
                    val reachedEnd = total > 0L && uploaded >= total
                    _uiState.update { state ->
                        state.copy(
                            statusUploadProgress = state.statusUploadProgress.copy(
                                stage = if (reachedEnd) UploadStage.PROCESSING else UploadStage.UPLOADING,
                                bytesUploaded = uploaded,
                                totalBytes = if (total > 0L) total else state.statusUploadProgress.totalBytes,
                                statusMessage = if (reachedEnd) {
                                    "Processing & publishing $label via Baileys…"
                                } else {
                                    "Uploading $label…"
                                }
                            )
                        )
                    }
                }
            ).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isProcessingTool = false,
                        statusUploadProgress = it.statusUploadProgress.copy(
                            stage = UploadStage.COMPLETED,
                            bytesUploaded = it.statusUploadProgress.totalBytes.coerceAtLeast(1L),
                            statusMessage = res.message ?: "$label uploaded!"
                        ),
                        snackbarMessage = res.message ?: "$label uploaded!"
                    )
                }
                loadStatuses()
                delay(3500L)
                _uiState.update { state ->
                    if (state.statusUploadProgress.stage == UploadStage.COMPLETED) {
                        state.copy(statusUploadProgress = StatusUploadProgress())
                    } else state
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessingTool = false,
                        statusUploadProgress = it.statusUploadProgress.copy(
                            stage = UploadStage.FAILED,
                            statusMessage = err.message ?: "Media status upload failed"
                        ),
                        snackbarMessage = err.message ?: "Media status upload failed"
                    )
                }
            }
        }
    }

    fun forwardCapturedStatus(id: String) {
        viewModelScope.launch {
            repository.forwardCapturedStatus(id).onSuccess { res ->
                _uiState.update { it.copy(snackbarMessage = res.message ?: "Status forwarded to your DM!") }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Forward failed") }
            }
        }
    }

    // 3-Dot Automation Tools Actions
    fun loadVault() {
        viewModelScope.launch {
            repository.getVaultItems().onSuccess { res ->
                _uiState.update { it.copy(vaultItems = res.items) }
            }
            repository.getVaultSettings().onSuccess { cfg ->
                _uiState.update { it.copy(vaultSettings = cfg) }
            }
        }
    }

    fun updateVaultSettings(updated: VaultSettingsPayload) {
        _uiState.update { it.copy(vaultSettings = updated) }
        viewModelScope.launch {
            repository.updateVaultSettings(updated).onSuccess {
                _uiState.update { it.copy(snackbarMessage = "Vault settings saved") }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Failed to update Vault settings") }
            }
        }
    }

    fun forwardVaultItem(id: String) {
        viewModelScope.launch {
            repository.forwardVaultItem(id).onSuccess { res ->
                _uiState.update { it.copy(snackbarMessage = res.message ?: "Forwarded recovered item to DM") }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Failed to forward item") }
            }
        }
    }

    fun clearVault() {
        viewModelScope.launch {
            repository.clearVault().onSuccess {
                _uiState.update { it.copy(vaultItems = emptyList(), snackbarMessage = "Vault cleared") }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Failed to clear Vault") }
            }
        }
    }

    fun loadAutoReactConfig() {
        viewModelScope.launch {
            repository.getAutoReactConfig().onSuccess { cfg ->
                _uiState.update { it.copy(autoReactConfig = cfg) }
            }
        }
    }

    fun saveAutoReactConfig(updated: AutoReactConfigPayload) {
        _uiState.update { it.copy(autoReactConfig = updated) }
        viewModelScope.launch {
            repository.updateAutoReactConfig(updated).onSuccess {
                _uiState.update { it.copy(snackbarMessage = "Auto-Status settings updated") }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Failed to save Auto-Status settings") }
            }
        }
    }

    fun addChannelRule(channelUrl: String, channelName: String, emojisRaw: String) {
        val emojis = emojisRaw.split(" ", ",").map { it.trim() }.filter { it.isNotBlank() }
            .ifEmpty { listOf("❤️", "🔥", "👍") }
        viewModelScope.launch {
            repository.addChannelReactRule(channelUrl.trim(), channelName.trim(), emojis).onSuccess {
                _uiState.update { it.copy(snackbarMessage = "Channel Auto-React rule added") }
                loadChannels()
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Failed to add channel rule") }
            }
        }
    }

    fun deleteChannelRule(id: String) {
        viewModelScope.launch {
            repository.deleteChannelReactRule(id).onSuccess {
                _uiState.update { it.copy(snackbarMessage = "Channel rule removed") }
                loadChannels()
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Failed to remove rule") }
            }
        }
    }

    fun applyFullProfilePic(targetType: String, groupJid: String, uri: Uri) {
        _uiState.update { it.copy(isProcessingTool = true) }
        viewModelScope.launch {
            repository.setFullProfilePic(targetType, groupJid, uri).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isProcessingTool = false,
                        snackbarMessage = res.message ?: "Full uncropped profile picture applied!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessingTool = false,
                        snackbarMessage = err.message ?: "Failed to apply Full Profile Picture"
                    )
                }
            }
        }
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            repository.triggerCloudSync().onSuccess { res ->
                _uiState.update { it.copy(snackbarMessage = res.message ?: "Cloud session synced to Supabase") }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Cloud sync failed") }
            }
        }
    }
}
