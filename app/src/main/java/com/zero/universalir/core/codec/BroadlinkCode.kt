package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Broadlink base64 码 → 微秒 pattern。
 *
 * 字节布局（python-broadlink protocol.md）：
 * [0]=0x26(IR)/0xb2/0xd7(RF)，[1]=repeat，[2..3]=脉冲数据长度(小端)，
 * 之后为脉冲流：值 ≤255 单字节；>255 为 0x00 + 大端两字节。
 *
 * tick 用真值 2^-15s ≈ 30.5176µs。python-broadlink 代码里的 32.84 常数偏短 7%
 * （issue #839 已台架验证），跨设备发射（Broadlink 学的码发到手机 IR 灯）必须用真值。
 */
@OptIn(ExperimentalEncodingApi::class)
object BroadlinkCode {
    const val TICK_US = 1_000_000.0 / 32_768.0
    const val CARRIER_HZ = 38_000

    private const val HEADER_IR = 0x26

    /** 解码为微秒序列；非 IR 码或格式错误返回 null。 */
    fun decodeUs(base64: String): IntArray? {
        val data = try {
            Base64.decode(base64.trim())
        } catch (_: Exception) {
            return null
        }
        if (data.size < 5 || data[0] != HEADER_IR.toByte()) return null
        val declaredLen = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
        val end = minOf(declaredLen + 4, data.size)
        val out = ArrayList<Int>(end)
        var i = 4
        while (i < end) {
            var v = data[i].toInt() and 0xFF
            i++
            if (v == 0) {
                if (i + 1 >= end) break
                v = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                i += 2
            }
            out += Math.round(v * TICK_US).toInt()
        }
        return if (out.size >= 2) out.toIntArray() else null
    }

    fun toSignal(base64: String, source: String? = "broadlink"): IrSignal? =
        decodeUs(base64)?.let { IrSignal(CARRIER_HZ, it, source = source) }
}
