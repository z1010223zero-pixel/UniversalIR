package com.zero.universalir.core.ir

import com.zero.universalir.data.codes.CodeLibrary

/**
 * RC5/RC5X/RC6 翻转位状态：真实遥控器每按下新键翻转一次，
 * 接收端借此区分"新按键"与"长按自动重复"（恒定值会被部分设备当成连按去重）。
 * 本应用一次发送即一次按键，因此按（设备, 按键）维度在每次发送时翻转。
 * 进程重启后重置——最坏情况是重启后的首次按键被个别设备当作重复忽略，再按一次即可。
 */
object RcToggleMemory {
    private val state = HashMap<String, Boolean>()

    /** 返回本次发送应使用的翻转位，并为下一次按键翻转。 */
    @Synchronized
    fun next(deviceId: Long, key: String): Boolean {
        val k = "$deviceId:$key"
        val v = !(state[k] ?: false)
        state[k] = v
        return v
    }
}

/**
 * 按键语义信号：支持翻转位的协议（RC5/RC5X/RC6）按（设备, 按键）翻转 toggle 后重渲染。
 * 重渲染失败时回退原始信号。
 */
fun CodeLibrary.LibrarySignal.signalForPress(deviceId: Long): IrSignal =
    withToggle?.let { render ->
        render(RcToggleMemory.next(deviceId, name)) ?: signal
    } ?: signal
