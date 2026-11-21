package com.zaidashiq.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.zaidashiq.chat.ui.navigation.*
import com.zaidashiq.chat.ui.screens.*
import com.zaidashiq.chat.ui.theme.ZaidAshiqChatTheme
import com.zaidashiq.chat.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZaidAshiqChatTheme {
                ZaidAshiqChatApp()
            }
        }
    }
}

@Composable
fun ZaidAshiqChatApp(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = if (uiState.isLinked) HomeRoute else PairingRoute
            ) {
                composable<PairingRoute> {
                    PairingScreen(
                        uiState = uiState,
                        onGenerateCode = viewModel::requestPairingCode,
                        onContinueToHome = {
                            navController.navigate(HomeRoute) {
                                popUpTo<PairingRoute> { inclusive = true }
                            }
                            viewModel.loadAllHomeData()
                        },
                        onOpenConnectionSettings = {
                            navController.navigate(ConnectionRoute)
                        }
                    )
                }

                composable<HomeRoute> {
                    LaunchedEffect(Unit) {
                        viewModel.loadChats()
                    }
                    HomeScreen(
                        uiState = uiState,
                        onSelectTab = viewModel::setTab,
                        onToggleSearch = viewModel::toggleSearch,
                        onSearchChange = viewModel::updateSearchQuery,
                        onRefresh = viewModel::loadAllHomeData,
                        onOpenChat = { jid, name, phone ->
                            navController.navigate(ChatRoute(jid = jid, name = name, phone = phone))
                        },
                        onOpenVault = { navController.navigate(VaultRoute) },
                        onOpenAutoStatus = { navController.navigate(AutoStatusRoute) },
                        onOpenFullPp = { navController.navigate(FullProfilePicRoute) },
                        onOpenChannelReact = { navController.navigate(ChannelReactRoute) },
                        onOpenConnection = { navController.navigate(ConnectionRoute) },
                        onOpenPairing = { navController.navigate(PairingRoute) },
                        onUploadTextStatus = viewModel::uploadTextStatus,
                        onUploadMediaStatus = viewModel::uploadMediaStatus,
                        onDismissStatusProgress = viewModel::dismissStatusUploadProgress,
                        onForwardStatus = viewModel::forwardCapturedStatus
                    )
                }

                composable<ChatRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<ChatRoute>()
                    DisposableEffect(route.jid) {
                        viewModel.startChatPolling(route.phone.ifBlank { route.jid })
                        onDispose { viewModel.stopChatPolling() }
                    }
                    ChatScreen(
                        recipientJid = route.jid,
                        recipientName = route.name,
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onSendText = { text -> viewModel.sendTextMessage(route.jid, text) },
                        onOpenButtonComposer = {
                            navController.navigate(
                                ButtonComposerRoute(
                                    recipientJid = route.jid,
                                    recipientName = route.name
                                )
                            )
                        },
                        onDownloadAndSend = { tool, query, caption, done ->
                            viewModel.downloadAndSendMedia(route.jid, tool, query, caption, done)
                        },
                        onSearchGiphy = viewModel::searchGiphy,
                        onSendGiphy = { gif, done -> viewModel.sendGiphyGif(route.jid, gif, done) },
                        onSendVideoNoteUri = { uri, done -> viewModel.sendVideoNoteUri(route.jid, uri, done) },
                        onSendVoiceNoteUri = { uri, done -> viewModel.sendVoiceNoteUri(route.jid, uri, done) },
                        onSendRecordedVoice = { file, done -> viewModel.sendRecordedVoiceFile(route.jid, file, done) },
                        onSendFakeLocation = { lat, lng, dur, label, done ->
                            viewModel.sendFakeLocation(route.jid, lat, lng, dur, label, done)
                        }
                    )
                }

                composable<ButtonComposerRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<ButtonComposerRoute>()
                    ButtonComposerScreen(
                        recipientName = route.recipientName,
                        isSending = uiState.isProcessingTool,
                        onBack = { navController.popBackStack() },
                        onSend = { body, buttons ->
                            viewModel.sendButtonMessage(route.recipientJid, body, buttons) {
                                navController.popBackStack()
                            }
                        }
                    )
                }

                composable<VaultRoute> {
                    VaultScreen(
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onRefresh = viewModel::loadVault,
                        onUpdateSettings = viewModel::updateVaultSettings,
                        onForwardItem = viewModel::forwardVaultItem,
                        onClearVault = viewModel::clearVault
                    )
                }

                composable<AutoStatusRoute> {
                    AutoStatusScreen(
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onLoad = viewModel::loadAutoReactConfig,
                        onSave = viewModel::saveAutoReactConfig
                    )
                }

                composable<FullProfilePicRoute> {
                    FullProfilePicScreen(
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onApply = viewModel::applyFullProfilePic
                    )
                }

                composable<ChannelReactRoute> {
                    ChannelReactScreen(
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onRefresh = viewModel::loadChannels,
                        onAddRule = viewModel::addChannelRule,
                        onDeleteRule = viewModel::deleteChannelRule
                    )
                }

                composable<ConnectionRoute> {
                    ConnectionSettingsScreen(
                        uiState = uiState,
                        onBack = { navController.popBackStack() },
                        onRefreshStatus = viewModel::refreshBackendStatus,
                        onTriggerCloudSync = viewModel::triggerCloudSync,
                        onRelinkPhone = {
                            viewModel.unlinkAndRepair()
                            navController.navigate(PairingRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}
