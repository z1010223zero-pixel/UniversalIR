package com.zero.universalir.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zero.universalir.feature.add.AddDeviceScreen
import com.zero.universalir.feature.home.HomeScreen
import com.zero.universalir.feature.remote.RemotePanelScreen
import com.zero.universalir.feature.settings.SettingsScreen
import com.zero.universalir.feature.voice.VoiceGuideScreen
import com.zero.universalir.voice.DeepLinkRouter
import com.zero.universalir.voice.FeedbackBus
import com.zero.universalir.voice.VoiceExecutor
import com.zero.universalir.voice.VoiceLinkBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    val router: DeepLinkRouter,
    private val executor: VoiceExecutor,
    private val linkBus: VoiceLinkBus,
    feedback: FeedbackBus,
) : ViewModel() {
    val feedbackMessages = feedback.messages
    val pendingLinks = linkBus.pending

    fun consumeLink(token: Long) = linkBus.consume(token)

    fun execute(cmd: com.zero.universalir.voice.VoiceCommand) {
        viewModelScope.launch { executor.execute(cmd) }
    }
}

@Composable
fun AppRoot() {
    val vm: AppViewModel = hiltViewModel()
    val nav = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.feedbackMessages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        vm.pendingLinks.collect { pending ->
            if (pending != null) {
                when (val result = vm.router.parse(pending.uri)) {
                    DeepLinkRouter.ParseResult.OpenHome ->
                        nav.navigate("home") { launchSingleTop = true }
                    is DeepLinkRouter.ParseResult.Command -> vm.execute(result.cmd)
                    DeepLinkRouter.ParseResult.Unknown -> Unit
                }
                vm.consumeLink(pending.token)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    onAddDevice = { nav.navigate("add") },
                    onOpenDevice = { id -> nav.navigate("remote/$id") },
                    onVoiceGuide = { id -> nav.navigate("voice/$id") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable("add") {
                AddDeviceScreen(
                    onDone = { id ->
                        nav.navigate("remote/$id") { popUpTo("home") }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("remote/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                RemotePanelScreen(
                    deviceId = id,
                    onBack = { nav.popBackStack() },
                    onVoiceGuide = { nav.navigate("voice/$id") },
                )
            }
            composable("voice/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                VoiceGuideScreen(deviceId = id, onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
