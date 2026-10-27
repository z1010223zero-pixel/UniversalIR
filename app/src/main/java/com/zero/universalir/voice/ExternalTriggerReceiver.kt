package com.zero.universalir.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Tasker / MacroDroid 等自动化工具的广播入口。
 *
 * action = com.zero.universalir.SEND
 * extras:
 * - device  必填，设备名或 id
 * - key     按键名（电视/灯等）
 * - power   on/off（空调）
 * - mode    cool/heat/dry/fan_only/auto 或中文（空调）
 * - fan     low/mid/high/auto（空调）
 * - temp    16-30（空调）
 * - pin     鉴权码；设置页配置后必填且需一致，否则指令被拒
 */
@AndroidEntryPoint
class ExternalTriggerReceiver : BroadcastReceiver() {

    @Inject lateinit var executor: VoiceExecutor
    @Inject lateinit var security: TriggerSecurity
    @Inject lateinit var feedback: FeedbackBus

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND) return
        val deviceKey = intent.getStringExtra(EXTRA_DEVICE) ?: return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (!TriggerAuth.authOk(security.requiredPin(), intent.getStringExtra(EXTRA_PIN))) {
                    feedback.post("外部触发被拒绝：PIN 鉴权失败")
                    return@launch
                }
                val temp = intent.getStringExtra(EXTRA_TEMP)?.toIntOrNull()
                val mode = intent.getStringExtra(EXTRA_MODE)?.let(DeepLinkRouter::normalizeMode)
                val cmd = if (temp != null || mode != null) {
                    VoiceCommand(
                        deviceKey = deviceKey,
                        action = "ac",
                        power = DeepLinkRouter.parsePower(intent.getStringExtra(EXTRA_POWER)),
                        mode = mode,
                        fan = intent.getStringExtra(EXTRA_FAN)?.lowercase(),
                        temp = temp,
                    )
                } else {
                    VoiceCommand(
                        deviceKey = deviceKey,
                        action = "key",
                        keyLabel = intent.getStringExtra(EXTRA_KEY) ?: "on",
                    )
                }
                executor.execute(cmd)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_SEND = "com.zero.universalir.SEND"
        const val EXTRA_DEVICE = "device"
        const val EXTRA_KEY = "key"
        const val EXTRA_POWER = "power"
        const val EXTRA_MODE = "mode"
        const val EXTRA_FAN = "fan"
        const val EXTRA_TEMP = "temp"
        const val EXTRA_PIN = "pin"
    }
}
