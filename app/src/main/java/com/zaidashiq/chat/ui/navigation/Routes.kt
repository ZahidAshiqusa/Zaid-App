package com.zaidashiq.chat.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object PairingRoute

@Serializable
object HomeRoute

@Serializable
data class ChatRoute(
    val jid: String,
    val name: String,
    val phone: String = ""
)

@Serializable
data class ButtonComposerRoute(
    val recipientJid: String,
    val recipientName: String
)

@Serializable
object VaultRoute

@Serializable
object AutoStatusRoute

@Serializable
object FullProfilePicRoute

@Serializable
object ChannelReactRoute

@Serializable
object ConnectionRoute

@Serializable
object PrivacyAccountRoute
