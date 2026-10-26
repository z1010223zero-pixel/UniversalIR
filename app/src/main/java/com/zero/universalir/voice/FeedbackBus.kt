package com.zero.universalir.voice

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 执行结果的全局提示（Snackbar 展示），深链接/广播触发时 UI 可能不在前台。
 * replay=1 保证冷启动执行结果也能显示。
 */
@Singleton
class FeedbackBus @Inject constructor() {
    private val _messages = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages

    fun post(message: String) {
        _messages.tryEmit(message)
    }
}
