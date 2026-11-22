package com.zaidashiq.chat

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.zaidashiq.chat.ui.navigation.*
import com.zaidashiq.chat.ui.screens.*
import com.zaidashiq.chat.ui.theme.GlassCardElevated
import com.zaidashiq.chat.ui.theme.NeonMintAccent
import com.zaidashiq.chat.ui.theme.ObsidianBase
import com.zaidashiq.chat.ui.theme.TextPrimary
import com.zaidashiq.chat.ui.theme.ZaidAshiqChatTheme
import com.zaidashiq.chat.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
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
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        containerColor = ObsidianBase,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = GlassCardElevated,
                    contentColor = TextPrimary,
                    actionColor = NeonMintAccent,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = if (uiState.isLinked) HomeRoute else PairingRoute,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it / 5 }, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(200, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 6 }, animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        fadeOut(tween(160, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 5 }, animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(180, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it / 5 }, animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        fadeOut(tween(160, easing = FastOutSlowInEasing))
                }
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
                        onLoadOlderChats = viewModel::loadOlderChatsList,
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
                        recipientPhone = route.phone,
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
                        },
                        onLookupNumber = viewModel::lookupNumberDetails,
                        onClearNumberLookup = viewModel::clearNumberLookup,
                        onLoadOlderMessages = { viewModel.loadOlderMessages(route.jid) }
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

            // Animated Fading-Up Startup Splash Screen Overlay
            AnimatedVisibility(
                visible = showSplash,
                exit = fadeOut(tween(320, easing = FastOutSlowInEasing))
            ) {
                SplashScreen(
                    onFinished = { showSplash = false }
                )
            }
        }
    }
}
