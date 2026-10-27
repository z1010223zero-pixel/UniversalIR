package com.zero.universalir.voice

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.triggerDataStore by preferencesDataStore(name = "trigger_security")

/**
 * 外部触发（SEND 广播）鉴权。
 *
 * 广播入口是 exported 的，任意应用都能伪造设备指令；PIN 是最低成本的防护——
 * 在设置页配置后，Tasker/MacroDroid 发广播时需携带同名 extra「pin」。
 * PIN 为空表示不鉴权，保持自动化工具开箱即用。
 */
@Singleton
class TriggerSecurity @Inject constructor(@ApplicationContext private val context: Context) {

    /** 当前生效的鉴权 PIN；空串 = 未启用。 */
    suspend fun requiredPin(): String =
        context.triggerDataStore.data.map { it[KEY_PIN] ?: "" }.first()

    suspend fun setPin(pin: String) {
        context.triggerDataStore.edit { it[KEY_PIN] = pin.trim() }
    }

    companion object {
        val KEY_PIN = stringPreferencesKey("trigger_pin")
    }
}

/** 纯逻辑鉴权判断，独立出来便于单元测试。 */
object TriggerAuth {

    /** requiredPin 为空 = 未启用鉴权；否则必须精确匹配。 */
    fun authOk(requiredPin: String, provided: String?): Boolean =
        requiredPin.isBlank() || provided == requiredPin
}
