package com.zero.universalir.voice

import android.net.Uri
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PendingLink(val uri: Uri, val token: Long)

/**
 * MainActivity 收到的 uniir:// 深链接投递给 Compose 层处理。
 * StateFlow + token + 消费后清除：冷启动不丢（onCreate 先于订阅）、
 * 旋转不重发（onCreate 仅在无 savedState 时 push；已消费的 token 不会再次触发）。
 */
@Singleton
class VoiceLinkBus @Inject constructor() {
    private val _pending = MutableStateFlow<PendingLink?>(null)
    val pending: StateFlow<PendingLink?> = _pending.asStateFlow()

    private val counter = AtomicLong()

    fun push(uri: Uri?) {
        if (uri != null) _pending.value = PendingLink(uri, counter.incrementAndGet())
    }

    fun consume(token: Long) {
        if (_pending.value?.token == token) _pending.value = null
    }
}
