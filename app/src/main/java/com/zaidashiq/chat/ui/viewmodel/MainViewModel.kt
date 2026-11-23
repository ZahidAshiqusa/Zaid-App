package com.zaidashiq.chat.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaidashiq.chat.data.local.LocalChatStore
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

private val SOCIAL_MEDIA_LINK_REGEX = Regex(
    "^https?://(?:" +
        "(?:www\\.|vm\\.|vt\\.|m\\.)?tiktok\\.com/[^\\s]+|" +
        "(?:www\\.|m\\.)?instagram\\.com/(?:reel|p|tv)/[^\\s]+|" +
        "(?:www\\.|m\\.)?facebook\\.com/[^\\s]+|" +
        "fb\\.watch/[^\\s]+" +
        ")$",
    RegexOption.IGNORE_CASE
)

fun isDirectSongLink(input: String): Boolean {
    val clean = input.trim()
    return clean.isNotEmpty() && SONG_LINK_REGEX.matches(clean)
}

fun isDirectSocialMediaLink(input: String): Boolean {
    val clean = input.trim()
    return clean.isNotEmpty() && SOCIAL_MEDIA_LINK_REGEX.matches(clean)
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
    val accountProfile: AccountProfileResponse = AccountProfileResponse(),
    val privacySettings: NativePrivacyPayload = NativePrivacyPayload(),
    val ghostPrivacy: GhostPrivacyPayload = GhostPrivacyPayload(),
    val activeChatMeta: ChatMetaPayload = ChatMetaPayload(),
    val botCommands: List<BotCommandItem> = emptyList(),
    val isUpdatingSettings: Boolean = false,
    val exportedBackupTitle: String = "",
    val exportedBackupText: String? = null,
    val bannerNotice: String? = null,
    val snackbarMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionPrefs = SessionPreferences(application.applicationContext)
    private val localChatStore = LocalChatStore(application.applicationContext)
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
        viewModelScope.launch {
            if (!localChatStore.hasCompletedInitialOldChatClear()) {
                localChatStore.clearAllLocalChats(recordCutoffNow = true)
                _uiState.update {
                    it.copy(
                        chats = emptyList(),
                        activeChatMessages = emptyList(),
                        capturedStatuses = emptyList(),
                        calls = emptyList()
                    )
                }
                // Best-effort clear of old chats cached on Railway backend as well
                repository.clearAllChats()
            } else {
                val savedLocalChats = localChatStore.getLocalChats()
                if (savedLocalChats.isNotEmpty()) {
                    _uiState.update { it.copy(chats = savedLocalChats) }
                }
            }
            refreshBackendStatus()
        }
    }

    fun clearAllOldChats() {
        viewModelScope.launch {
            localChatStore.clearAllLocalChats(recordCutoffNow = true)
            _uiState.update {
                it.copy(
                    chats = emptyList(),
                    activeChatMessages = emptyList(),
                    capturedStatuses = emptyList(),
                    calls = emptyList(),
                    snackbarMessage = "All old chats cleared — new chats save locally on device."
                )
            }
            repository.clearAllChats()
        }
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
            val localCached = localChatStore.getLocalChats()
            _uiState.update { state ->
                state.copy(
                    chats = if (localCached.isNotEmpty()) localCached else state.chats,
                    isRefreshingHome = true
                )
            }
            repository.getChats().onSuccess { res ->
                val merged = localChatStore.mergeServerChats(res.chats)
                _uiState.update {
                    it.copy(
                        chats = merged.distinctBy { c -> c.resolvedJid.ifBlank { c.resolvedPhone } },
                        isRefreshingHome = false,
                        backendHealthy = true
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isRefreshingHome = false) }
            }
            repository.getContacts().onSuccess { res ->
                _uiState.update {
                    it.copy(contacts = res.contacts.distinctBy { c -> c.jid.ifBlank { c.number } })
                }
            }
        }
    }

    fun loadStatuses() {
        viewModelScope.launch {
            repository.getCapturedStatuses().onSuccess { res ->
                val cutoff = localChatStore.getClearedCutoffMs()
                val unique = res.statuses
                    .filter { st ->
                        val ts = if (st.timestamp < 1_000_000_000_000L) st.timestamp * 1000L else st.timestamp
                        cutoff <= 0L || ts <= 0L || ts > cutoff
                    }
                    .distinctBy { it.id.ifBlank { "${it.senderNumber}_${it.timestamp}" } }
                _uiState.update { it.copy(capturedStatuses = unique) }
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            repository.getGroups().onSuccess { res ->
                _uiState.update { it.copy(groups = res.groups.distinctBy { g -> g.jid }) }
            }
        }
    }

    fun loadChannels() {
        viewModelScope.launch {
            repository.getChannels().onSuccess { res ->
                _uiState.update {
                    it.copy(channels = res.channels.distinctBy { ch -> ch.id.ifBlank { ch.jid } })
                }
            }
        }
    }

    fun loadCalls() {
        viewModelScope.launch {
            repository.getRecentCalls().onSuccess { res ->
                val cutoff = localChatStore.getClearedCutoffMs()
                val unique = res.calls
                    .filter { c ->
                        val ts = if (c.timestamp < 1_000_000_000_000L) c.timestamp * 1000L else c.timestamp
                        cutoff <= 0L || ts <= 0L || ts > cutoff
                    }
                    .distinctBy { it.id.ifBlank { "${it.callerNumber}_${it.timestamp}" } }
                _uiState.update { it.copy(calls = unique) }
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
        viewModelScope.launch {
            val localMsgs = localChatStore.getLocalMessages(recipientPhoneOrJid)
            if (localMsgs.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        activeChatMessages = localMsgs,
                        isLoadingChat = false
                    )
                }
            }
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
                    val savedLocal = localChatStore.mergeServerMessages(
                        jidOrNumber = recipientPhoneOrJid,
                        serverMessages = res.messages,
                        contactName = res.contactName
                    )
                    val refreshedChats = localChatStore.getLocalChats()
                    _uiState.update { state ->
                        val pendingLocal = state.activeChatMessages.filter { it.pending }
                        val savedIds = savedLocal.map { it.id }.toSet()
                        val merged = (savedLocal + pendingLocal.filterNot { it.id in savedIds })
                            .distinctBy { it.id.ifBlank { "${it.timestamp}_${it.text.hashCode()}" } }
                        state.copy(
                            chats = if (refreshedChats.isNotEmpty()) refreshedChats else state.chats,
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
            localChatStore.resetClearedCutoffForOlderSync()
            repository.loadOlderChats(
                jid = recipientJid,
                oldestMsgId = oldest?.id.orEmpty(),
                oldestTimestamp = oldest?.timestamp ?: 0L,
                fromMe = oldest?.fromMe ?: false
            ).onSuccess { res ->
                val key = recipientJid.substringBefore("@")
                repository.getChatMessages(key).onSuccess { viewRes ->
                    val merged = localChatStore.mergeServerMessages(
                        jidOrNumber = recipientJid,
                        serverMessages = viewRes.messages,
                        contactName = viewRes.contactName
                    )
                    _uiState.update { state ->
                        state.copy(
                            activeChatMessages = merged,
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
            localChatStore.resetClearedCutoffForOlderSync()
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

        // Intercept ALL bot commands (.play, .video, .tiktok, .fb, .ig, .dl, .number) -> live in-chat processing bubble!
        if (clean.equals(".play", ignoreCase = true) || clean.equals(".song", ignoreCase = true)) {
            _uiState.update {
                it.copy(snackbarMessage = "Usage: .play <song name or link> — or paste a song link directly!")
            }
            return
        }
        val playRegex = Regex("^\\.(?:play|song|music|mp3)\\s+(.+)$", RegexOption.IGNORE_CASE)
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
        val videoRegex = Regex("^\\.(?:video|ytv|mp4)\\s+(.+)$", RegexOption.IGNORE_CASE)
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
        val tiktokRegex = Regex("^\\.(?:tiktok|tt)\\s+(.+)$", RegexOption.IGNORE_CASE)
        tiktokRegex.matchEntire(clean)?.let { match ->
            val q = match.groupValues[1].trim()
            if (q.isNotBlank()) {
                downloadAndSendMedia(recipientJid, ChatToolType.TIKTOK, q, "")
                return
            }
        }
        val fbRegex = Regex("^\\.(?:fb|facebook)\\s+(.+)$", RegexOption.IGNORE_CASE)
        fbRegex.matchEntire(clean)?.let { match ->
            val q = match.groupValues[1].trim()
            if (q.isNotBlank()) {
                downloadAndSendMedia(recipientJid, ChatToolType.FACEBOOK, q, "")
                return
            }
        }
        val igRegex = Regex("^\\.(?:ig|insta|instagram)\\s+(.+)$", RegexOption.IGNORE_CASE)
        igRegex.matchEntire(clean)?.let { match ->
            val q = match.groupValues[1].trim()
            if (q.isNotBlank()) {
                downloadAndSendMedia(recipientJid, ChatToolType.INSTAGRAM, q, "")
                return
            }
        }
        val dlRegex = Regex("^\\.(?:dl|download)\\s+(.+)$", RegexOption.IGNORE_CASE)
        dlRegex.matchEntire(clean)?.let { match ->
            val q = match.groupValues[1].trim()
            if (q.isNotBlank()) {
                downloadAndSendMedia(recipientJid, ChatToolType.DOWNLOADER, q, "")
                return
            }
        }

        val numRegex = Regex("^\\.(?:number|sim|tc|truecaller)\\s+(.+)$", RegexOption.IGNORE_CASE)
        numRegex.matchEntire(clean)?.let { match ->
            val q = match.groupValues[1].trim()
            if (q.isNotBlank()) {
                fetchAndSendNumberDetailsInChat(recipientJid, q)
                return
            }
        }

        // Auto-detect pasted Song or Social Media links directly in chat
        if (isDirectSongLink(clean)) {
            downloadAndSendMedia(
                recipientJid = recipientJid,
                tool = ChatToolType.SONG,
                queryOrUrl = clean,
                caption = ""
            )
            return
        }
        if (isDirectSocialMediaLink(clean)) {
            val detectedTool = when {
                clean.contains("tiktok", ignoreCase = true) -> ChatToolType.TIKTOK
                clean.contains("instagram", ignoreCase = true) -> ChatToolType.INSTAGRAM
                clean.contains("facebook", ignoreCase = true) || clean.contains("fb.watch", ignoreCase = true) -> ChatToolType.FACEBOOK
                else -> ChatToolType.DOWNLOADER
            }
            downloadAndSendMedia(
                recipientJid = recipientJid,
                tool = detectedTool,
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
                activeChatMessages = (it.activeChatMessages + optimistic).distinctBy { m -> m.id },
                isSendingMessage = true
            )
        }

        viewModelScope.launch {
            repository.sendText(recipientJid, clean).onSuccess { res ->
                val delivered = optimistic.copy(
                    id = res.id ?: tempId,
                    text = res.text?.ifBlank { clean } ?: clean,
                    status = res.status.coerceAtLeast(2),
                    timestamp = res.timestamp ?: optimistic.timestamp,
                    pending = false
                )
                val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                    jidOrNumber = recipientJid,
                    message = delivered
                )
                _uiState.update { state ->
                    val pendingOthers = state.activeChatMessages.filter { it.pending && it.id != tempId }
                    val merged = (updatedLocalMsgs + pendingOthers)
                        .distinctBy { it.id.ifBlank { "${it.timestamp}_${it.text.hashCode()}" } }
                    state.copy(
                        chats = updatedChats,
                        isSendingMessage = false,
                        activeChatMessages = merged
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

    fun fetchAndSendNumberDetailsInChat(recipientJid: String, rawQuery: String) {
        val clean = rawQuery.trim()
        val tempId = "pending_number_${System.currentTimeMillis()}"
        val pendingBubble = ChatMessageItem(
            id = tempId,
            fromMe = true,
            text = "Fetching Truecaller & SIM DB details for $clean…",
            mediaType = "number",
            timestamp = System.currentTimeMillis(),
            status = 1,
            pending = true
        )
        _uiState.update {
            it.copy(
                isProcessingTool = true,
                toolStatusText = "Fetching Number Details…",
                activeChatMessages = (it.activeChatMessages + pendingBubble).distinctBy { m -> m.id }
            )
        }
        viewModelScope.launch {
            repository.lookupNumber(clean).onSuccess { details ->
                val reportText = formatNumberDetailsSummary(details)
                repository.sendText(recipientJid, reportText).onSuccess { sentRes ->
                    val delivered = ChatMessageItem(
                        id = sentRes.id ?: "num_${System.currentTimeMillis()}",
                        fromMe = true,
                        text = sentRes.text?.ifBlank { reportText } ?: reportText,
                        mediaType = "number",
                        status = sentRes.status.coerceAtLeast(2),
                        timestamp = sentRes.timestamp ?: System.currentTimeMillis(),
                        pending = false
                    )
                    val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                        jidOrNumber = recipientJid,
                        message = delivered
                    )
                    _uiState.update { state ->
                        val pendingOthers = state.activeChatMessages.filter { it.pending && it.id != tempId }
                        val merged = (updatedLocalMsgs + pendingOthers)
                            .distinctBy { it.id.ifBlank { "${it.timestamp}_${it.text.hashCode()}" } }
                        state.copy(
                            chats = updatedChats,
                            isProcessingTool = false,
                            toolStatusText = null,
                            activeChatMessages = merged,
                            snackbarMessage = "Number details fetched & sent to chat!"
                        )
                    }
                }.onFailure { err ->
                    _uiState.update { state ->
                        state.copy(
                            isProcessingTool = false,
                            toolStatusText = null,
                            activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                            snackbarMessage = err.message ?: "Failed to send number details to chat"
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isProcessingTool = false,
                        toolStatusText = null,
                        activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                        snackbarMessage = err.message ?: "Number lookup failed"
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
            ChatToolType.SONG -> "Downloading Song on backend: $clean…"
            ChatToolType.VIDEO -> "Downloading Video on backend: $clean…"
            ChatToolType.TIKTOK -> "Downloading TikTok Video on backend…"
            ChatToolType.FACEBOOK -> "Downloading Facebook Video on backend…"
            ChatToolType.INSTAGRAM -> "Downloading Instagram Media on backend…"
            else -> "Downloading ${tool.title} on backend…"
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
                activeChatMessages = (it.activeChatMessages + pendingBubble).distinctBy { m -> m.id }
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
                val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                    jidOrNumber = recipientJid,
                    message = deliveredBubble
                )
                _uiState.update { state ->
                    val pendingOthers = state.activeChatMessages.filter { it.pending && it.id != tempId }
                    val merged = (updatedLocalMsgs + pendingOthers)
                        .distinctBy { it.id.ifBlank { "${it.timestamp}_${it.text.hashCode()}" } }
                    state.copy(
                        chats = updatedChats,
                        isProcessingTool = false,
                        activeMediaTool = null,
                        activeMediaQuery = "",
                        toolStatusText = null,
                        activeChatMessages = merged,
                        snackbarMessage = res.message ?: "${tool.title} downloaded & transferred to chat!"
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
                activeChatMessages = (it.activeChatMessages + pendingBubble).distinctBy { m -> m.id }
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
                val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                    jidOrNumber = recipientJid,
                    message = bubble
                )
                _uiState.update { state ->
                    val pendingOthers = state.activeChatMessages.filter { it.pending && it.id != tempId }
                    state.copy(
                        chats = updatedChats,
                        isProcessingTool = false,
                        activeMediaTool = null,
                        activeMediaQuery = "",
                        toolStatusText = null,
                        activeChatMessages = (updatedLocalMsgs + pendingOthers).distinctBy { it.id },
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
                        giphyResults = res.results.distinctBy { g -> g.id.ifBlank { g.mp4Url } },
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
                activeChatMessages = (it.activeChatMessages + pendingBubble).distinctBy { m -> m.id }
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
                val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                    jidOrNumber = recipientJid,
                    message = bubble
                )
                _uiState.update { state ->
                    val pendingOthers = state.activeChatMessages.filter { it.pending && it.id != tempId }
                    state.copy(
                        chats = updatedChats,
                        isProcessingTool = false,
                        activeMediaTool = null,
                        activeMediaQuery = "",
                        toolStatusText = null,
                        activeChatMessages = (updatedLocalMsgs + pendingOthers).distinctBy { it.id },
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
                activeChatMessages = (it.activeChatMessages + pendingBubble).distinctBy { m -> m.id }
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
                val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                    jidOrNumber = recipientJid,
                    message = bubble
                )
                _uiState.update { state ->
                    val pendingOthers = state.activeChatMessages.filter { it.pending && it.id != tempId }
                    state.copy(
                        chats = updatedChats,
                        isProcessingTool = false,
                        activeMediaTool = null,
                        toolStatusText = null,
                        activeChatMessages = (updatedLocalMsgs + pendingOthers).distinctBy { it.id },
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
                activeChatMessages = (it.activeChatMessages + pendingBubble).distinctBy { m -> m.id }
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
                val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                    jidOrNumber = recipientJid,
                    message = bubble
                )
                _uiState.update { state ->
                    val pendingOthers = state.activeChatMessages.filter { it.pending && it.id != tempId }
                    state.copy(
                        chats = updatedChats,
                        isProcessingTool = false,
                        activeMediaTool = null,
                        toolStatusText = null,
                        activeChatMessages = (updatedLocalMsgs + pendingOthers).distinctBy { it.id },
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
                activeChatMessages = (it.activeChatMessages + pendingBubble).distinctBy { m -> m.id }
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
                val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                    jidOrNumber = recipientJid,
                    message = bubble
                )
                _uiState.update { state ->
                    val pendingOthers = state.activeChatMessages.filter { it.pending && it.id != tempId }
                    state.copy(
                        chats = updatedChats,
                        isProcessingTool = false,
                        activeMediaTool = null,
                        toolStatusText = null,
                        activeChatMessages = (updatedLocalMsgs + pendingOthers).distinctBy { it.id },
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
                val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                    jidOrNumber = recipientJid,
                    message = bubble
                )
                _uiState.update {
                    it.copy(
                        chats = updatedChats,
                        isProcessingTool = false,
                        activeChatMessages = updatedLocalMsgs,
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
                _uiState.update {
                    it.copy(vaultItems = res.items.distinctBy { v -> v.id.ifBlank { "${v.senderNumber}_${v.timestamp}" } })
                }
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
                _uiState.update { it.copy(snackbarMessage = res.message ?: "Cloud session synced to PostgreSQL") }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Cloud sync failed") }
            }
        }
    }

    // ── Account, Ghost Privacy, Native WhatsApp Privacy & Backups ────────
    fun loadAccountAndPrivacy() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingSettings = true) }
            repository.getAccountProfile().onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isUpdatingSettings = false,
                        accountProfile = res,
                        privacySettings = res.privacy,
                        ghostPrivacy = res.ghostPrivacy
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isUpdatingSettings = false,
                        snackbarMessage = err.message ?: "Could not load account & privacy settings"
                    )
                }
            }
        }
    }

    fun updateProfileName(newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Enter a display name first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingSettings = true) }
            repository.updateAccountProfile(name = clean).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isUpdatingSettings = false,
                        accountProfile = res,
                        snackbarMessage = res.message ?: "WhatsApp Display Name updated!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isUpdatingSettings = false,
                        snackbarMessage = err.message ?: "Failed to update display name"
                    )
                }
            }
        }
    }

    fun updateProfileAbout(newAbout: String) {
        val clean = newAbout.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Enter About / Bio text first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingSettings = true) }
            repository.updateAccountProfile(about = clean).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isUpdatingSettings = false,
                        accountProfile = res,
                        snackbarMessage = res.message ?: "WhatsApp Bio / About updated!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isUpdatingSettings = false,
                        snackbarMessage = err.message ?: "Failed to update Bio / About"
                    )
                }
            }
        }
    }

    fun updateGhostPrivacy(updated: GhostPrivacyPayload) {
        _uiState.update { it.copy(ghostPrivacy = updated) }
        viewModelScope.launch {
            repository.updatePrivacySettings(ghostPrivacy = updated).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        ghostPrivacy = res.ghostPrivacy,
                        privacySettings = res.privacy,
                        snackbarMessage = res.message ?: "Ghost Privacy saved!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Failed to save Ghost Privacy") }
            }
        }
    }

    fun updateNativePrivacy(updated: NativePrivacyPayload) {
        _uiState.update { it.copy(privacySettings = updated, isUpdatingSettings = true) }
        viewModelScope.launch {
            repository.updatePrivacySettings(privacy = updated).onSuccess { res ->
                _uiState.update {
                    it.copy(
                        isUpdatingSettings = false,
                        privacySettings = res.privacy,
                        ghostPrivacy = res.ghostPrivacy,
                        snackbarMessage = res.message ?: "WhatsApp Privacy updated!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isUpdatingSettings = false,
                        snackbarMessage = err.message ?: "Failed to update WhatsApp Privacy"
                    )
                }
            }
        }
    }

    fun performChatAction(recipientJid: String, action: String, value: Int? = null) {
        viewModelScope.launch {
            if (action == "clear") {
                localChatStore.clearSingleChatLocally(recipientJid)
                val updatedChats = localChatStore.getLocalChats()
                _uiState.update {
                    it.copy(
                        chats = updatedChats,
                        activeChatMessages = emptyList()
                    )
                }
            }
            repository.performChatAction(recipientJid, action, value).onSuccess { res ->
                _uiState.update { state ->
                    state.copy(
                        activeChatMeta = res.chatMeta,
                        activeChatMessages = if (action == "clear") emptyList() else state.activeChatMessages,
                        snackbarMessage = res.message ?: "Chat updated!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (action == "clear") "Chat cleared locally!" else (err.message ?: "Chat action failed")
                    )
                }
            }
        }
    }

    fun exportChatTranscript(recipientJid: String, recipientName: String) {
        viewModelScope.launch {
            val (msgCount, transcript) = localChatStore.exportLocalChatTranscript(recipientJid, recipientName)
            _uiState.update {
                it.copy(
                    exportedBackupTitle = "Local Chat Backup: $recipientName ($msgCount msgs)",
                    exportedBackupText = transcript,
                    snackbarMessage = "Local chat backup ready ($msgCount messages)"
                )
            }
        }
    }

    fun exportFullBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingSettings = true) }
            val (chatCount, msgCount, summaryText) = localChatStore.exportLocalFullBackup()
            _uiState.update {
                it.copy(
                    isUpdatingSettings = false,
                    exportedBackupTitle = "Local Device Backup ($chatCount chats, $msgCount msgs)",
                    exportedBackupText = summaryText,
                    snackbarMessage = "Local device backup exported!"
                )
            }
        }
    }

    fun clearExportedBackup() {
        _uiState.update { it.copy(exportedBackupText = null, exportedBackupTitle = "") }
    }

    private fun formatNumberDetailsSummary(details: NumberLookupResponse): String = buildString {
        appendLine("*Number Details Report*")
        appendLine("Phone: ${details.formattedNumber.ifBlank { details.query }}")
        if (details.localNumber.isNotBlank() && details.localNumber != details.query) {
            appendLine("Local: ${details.localNumber}")
        }
        if (details.operator.isNotBlank()) appendLine("Operator: ${details.operator}")
        if (details.truecaller.name.isNotBlank()) appendLine("Truecaller Name: ${details.truecaller.name}")
        if (details.truecaller.country.isNotBlank()) appendLine("Country: ${details.truecaller.country}")
        if (details.truecaller.address.isNotBlank()) appendLine("Location: ${details.truecaller.address}")
        if (details.truecaller.email.isNotBlank()) appendLine("Email: ${details.truecaller.email}")
        appendLine("WhatsApp: ${if (details.whatsapp.registered) "Registered" else "Not verified / Private"}")
        if (details.whatsapp.about.isNotBlank()) appendLine("About: ${details.whatsapp.about}")
        if (details.simRecords.isNotEmpty()) {
            appendLine()
            appendLine("*SIM Records (${details.simRecords.size})*")
            details.simRecords.take(5).forEachIndexed { idx, rec ->
                val parts = listOfNotNull(
                    rec.name.takeIf { it.isNotBlank() }?.let { "Name: $it" },
                    rec.cnic.takeIf { it.isNotBlank() }?.let { "CNIC: $it" },
                    rec.network.takeIf { it.isNotBlank() }?.let { "Network: $it" },
                    rec.address.takeIf { it.isNotBlank() }?.let { "Address: $it" }
                ).joinToString(" | ")
                if (parts.isNotBlank()) appendLine("${idx + 1}. $parts")
            }
        }
    }.trim()

    fun shareNumberDetailsToChat(recipientJid: String, details: NumberLookupResponse) {
        val summary = formatNumberDetailsSummary(details)
        val tempId = "pending_share_num_${System.currentTimeMillis()}"
        val pendingBubble = ChatMessageItem(
            id = tempId,
            fromMe = true,
            text = "Sending Number Details to chat…",
            mediaType = "number",
            timestamp = System.currentTimeMillis(),
            status = 1,
            pending = true
        )
        _uiState.update {
            it.copy(
                activeChatMessages = (it.activeChatMessages + pendingBubble).distinctBy { m -> m.id },
                isSendingMessage = true
            )
        }
        viewModelScope.launch {
            repository.sendText(recipientJid, summary).onSuccess { res ->
                val delivered = ChatMessageItem(
                    id = res.id ?: "num_${System.currentTimeMillis()}",
                    fromMe = true,
                    text = res.text?.ifBlank { summary } ?: summary,
                    mediaType = "number",
                    status = res.status.coerceAtLeast(2),
                    timestamp = res.timestamp ?: System.currentTimeMillis(),
                    pending = false
                )
                val (updatedChats, updatedLocalMsgs) = localChatStore.saveDeliveredMessage(
                    jidOrNumber = recipientJid,
                    message = delivered
                )
                _uiState.update { state ->
                    val pendingOthers = state.activeChatMessages.filter { it.pending && it.id != tempId }
                    state.copy(
                        chats = updatedChats,
                        isSendingMessage = false,
                        activeChatMessages = (updatedLocalMsgs + pendingOthers).distinctBy { it.id },
                        snackbarMessage = "Number details sent to chat!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isSendingMessage = false,
                        activeChatMessages = state.activeChatMessages.filterNot { it.id == tempId },
                        snackbarMessage = err.message ?: "Failed to send number details"
                    )
                }
            }
        }
    }

    fun loadBotCommands() {
        viewModelScope.launch {
            repository.getBotCommands().onSuccess { res ->
                if (res.commands.isNotEmpty()) {
                    _uiState.update { it.copy(botCommands = res.commands) }
                }
            }
        }
    }
}
