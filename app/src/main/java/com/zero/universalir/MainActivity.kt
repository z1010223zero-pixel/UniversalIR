package com.zero.universalir

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zero.universalir.ui.AppRoot
import com.zero.universalir.ui.theme.UniversalIrTheme
import com.zero.universalir.voice.VoiceLinkBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var voiceLinkBus: VoiceLinkBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniversalIrTheme {
                AppRoot()
            }
        }
        // 仅冷启动投递；旋转重建时 intent 里仍带着原深链接，避免重复执行
        if (savedInstanceState == null) {
            voiceLinkBus.push(intent?.data)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        voiceLinkBus.push(intent.data)
    }
}
