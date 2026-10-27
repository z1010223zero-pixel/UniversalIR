package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/** 协议名（Flipper/irdb 风格）→ 原始信号渲染。返回 null 表示该协议暂不支持。 */
object ProtocolRegistry {
    fun encode(protocol: String, address: Long, command: Long, toggle: Boolean = false): IrSignal? {
        val p = protocol.trim().uppercase().replace("_", "")
        return when {
            p == "NEC" -> NecEncoder.encodeStandard(address.toInt(), command.toInt())
            p == "NECEXT" -> NecEncoder.encodeExtended(address.toInt(), command.toInt())
            p == "RC5" -> Rc5Encoder.encode(address.toInt(), command.toInt(), toggle)
            p == "RC5X" -> Rc5Encoder.encode(address.toInt(), command.toInt(), toggle, extended = true)
            p.startsWith("SIRC") -> SircEncoder.encode(
                command = command.toInt(),
                address = address.toInt(),
                totalBits = when {
                    "20" in p -> 20
                    "15" in p -> 15
                    else -> 12
                },
            )
            p == "SAMSUNG32" -> Samsung32Encoder.encode(address.toInt(), command.toInt())
            p == "KASEIKYO" -> KaseikyoEncoder.encode(address, command)
            p == "RCA" -> RcaEncoder.encode(address.toInt(), command.toInt())
            p == "PIONEER" -> PioneerEncoder.encode(address.toInt(), command.toInt())
            p == "RC6" -> Rc6Encoder.encode(address.toInt(), command.toInt(), toggle)
            else -> null
        }
    }

    /** RC5/RC5X/RC6 带翻转位，可在发送时按"新按键"语义翻转。 */
    fun supportsToggle(protocol: String): Boolean =
        protocol.trim().uppercase().replace("_", "") in setOf("RC5", "RC5X", "RC6")
}
