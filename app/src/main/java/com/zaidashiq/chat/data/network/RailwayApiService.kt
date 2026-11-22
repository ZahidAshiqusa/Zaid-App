package com.zaidashiq.chat.data.network

import com.zaidashiq.chat.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface RailwayApiService {

    @GET("api/health")
    suspend fun checkHealth(): HealthResponse

    @GET("api/status")
    suspend fun getBotStatus(): BotStatusResponse

    @POST("api/pair")
    suspend fun requestPairingCode(@Body request: PairRequest): PairResponse

    @GET("api/contacts")
    suspend fun getContacts(@Query("sync") sync: String = "1"): ContactsListResponse

    @GET("api/chats/list")
    suspend fun getChatsList(): ChatsListResponse

    @GET("api/chats/view")
    suspend fun getChatMessages(@Query("number") number: String): ChatViewResponse

    @GET("api/chats/presence")
    suspend fun getChatPresence(@Query("jid") jid: String): PresenceResponse

    @POST("api/chats/load-older")
    suspend fun loadOlderChats(@Body request: LoadOlderChatsRequest): LoadOlderChatsResponse

    @GET("api/lookup/number")
    suspend fun lookupNumber(@Query("number") number: String): NumberLookupResponse

    @GET("api/chats/groups")
    suspend fun getGroups(): GroupsListResponse

    @GET("api/chats/channels")
    suspend fun getChannels(): ChannelsListResponse

    @GET("api/calls/recent")
    suspend fun getRecentCalls(): CallsListResponse

    @POST("api/chat/send-text")
    suspend fun sendTextMessage(@Body request: SendTextRequest): SendTextResponse

    @POST("api/chat/download-and-send")
    suspend fun downloadAndSendMedia(@Body request: DownloadAndSendRequest): DownloadAndSendResponse

    @GET("api/chat/giphy-search")
    suspend fun searchGiphy(@Query("q") query: String): GiphySearchResponse

    @POST("api/media/send")
    suspend fun sendMediaNote(@Body request: SendMediaRequest): SendMediaResponse

    @POST("api/location/send-live")
    suspend fun sendFakeLocation(@Body request: SendLocationRequest): SendLocationResponse

    @POST("api/messenger/buttons")
    suspend fun sendButtonMessage(@Body request: SendButtonsRequest): SendButtonsResponse

    @GET("api/status/captured")
    suspend fun getCapturedStatuses(): CapturedStatusesResponse

    @POST("api/status/captured/{id}/forward")
    suspend fun forwardCapturedStatus(@Path("id") id: String): GenericActionResponse

    @POST("api/status/upload")
    suspend fun uploadStatus(@Body request: StatusUploadRequest): GenericActionResponse

    @Multipart
    @POST("api/status/upload")
    suspend fun uploadStatusMultipart(
        @Part file: MultipartBody.Part,
        @Part("caption") caption: RequestBody,
        @Part("isVoiceNote") isVoiceNote: RequestBody,
        @Part("privacyMode") privacyMode: RequestBody
    ): GenericActionResponse

    @GET("api/vault/items")
    suspend fun getVaultItems(): VaultItemsResponse

    @GET("api/vault/settings")
    suspend fun getVaultSettings(): VaultSettingsPayload

    @POST("api/vault/settings")
    suspend fun updateVaultSettings(@Body payload: VaultSettingsPayload): GenericActionResponse

    @POST("api/vault/forward/{id}")
    suspend fun forwardVaultItem(@Path("id") id: String): GenericActionResponse

    @DELETE("api/vault/clear")
    suspend fun clearVault(): GenericActionResponse

    @GET("api/autoreact/config")
    suspend fun getAutoReactConfig(): AutoReactConfigPayload

    @POST("api/autoreact/config")
    suspend fun updateAutoReactConfig(@Body payload: AutoReactConfigPayload): GenericActionResponse

    @POST("api/char/rules")
    suspend fun addChannelReactRule(@Body payload: ChannelRuleRequest): GenericActionResponse

    @DELETE("api/char/rules/{id}")
    suspend fun deleteChannelReactRule(@Path("id") id: String): GenericActionResponse

    @POST("api/profile-pic/full")
    suspend fun setFullProfilePicture(@Body request: FullProfilePicRequest): GenericActionResponse

    @POST("api/session/cloud-sync")
    suspend fun triggerCloudSync(): CloudSyncResponse
}
