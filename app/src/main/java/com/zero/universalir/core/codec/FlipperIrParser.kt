package com.zero.universalir.core.codec

import com.zero.universalir.core.ir.IrSignal

/**
 * Flipper Zero .ir 文本格式解析（官方 infrared_file_format）。
 * parsed 条目用内置协议编码器渲染；raw 条目直接给出微秒序列。
 */
class FlipperIrParser {

    data class FlipperSignal(
        val name: String,
        val type: String,
        val protocol: String?,
        val addressHex: String?,
        val commandHex: String?,
        val frequencyHz: Int,
        val dataUs: List<Long>,
    ) {
        val addressValue: Long? = addressHex?.let(FlipperIrParser::bytesToLittleEndian)
        val commandValue: Long? = commandHex?.let(FlipperIrParser::bytesToLittleEndian)
    }

    fun parse(text: String): List<FlipperSignal> {
        val signals = mutableListOf<FlipperSignal>()
        var name: String? = null
        var type = "raw"
        var protocol: String? = null
        var address: String? = null
        var command: String? = null
        var freq = 38000
        var data = mutableListOf<Long>()
        var pending = false

        fun flush() {
            if (pending && !name.isNullOrEmpty()) {
                signals += FlipperSignal(name!!, type, protocol, address, command, freq, data.toList())
            }
            name = null
            type = "raw"
            protocol = null
            address = null
            command = null
            freq = 38000
            data = mutableListOf()
            pending = false
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("#") -> if (pending) flush()
                else -> {
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim().lowercase()
                        val value = line.substring(idx + 1).trim()
                        when (key) {
                            "name" -> {
                                if (pending) flush()
                                name = value
                                pending = true
                            }
                            "type" -> type = value
                            "protocol" -> protocol = value
                            "address" -> address = value
                            "command" -> command = value
                            "frequency" -> freq = value.toIntOrNull() ?: 38000
                            "duty_cycle" -> Unit
                            "data" -> data = value.split(WHITESPACE)
                                .mapNotNull { it.toLongOrNull() }
                                .toMutableList()
                        }
                    }
                }
            }
        }
        flush()
        return signals
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /** Flipper 地址/命令字段为字节序列，按发送序（LSB 字节在前）合成数值。 */
        fun bytesToLittleEndian(hex: String): Long? {
            val bytes = hex.split(WHITESPACE).mapNotNull { it.toIntOrNull(16) }
            if (bytes.isEmpty()) return null
            var v = 0L
            bytes.forEachIndexed { i, b -> v = v or ((b and 0xFF).toLong() shl (8 * i)) }
            return v
        }

        /** parsed 条目 → 信号；协议不支持或缺字段返回 null。 [toggle] 仅对 RC5/RC5X/RC6 生效。 */
        fun renderParsed(s: FlipperSignal, toggle: Boolean = false): IrSignal? {
            val addr = s.addressValue ?: return null
            val cmd = s.commandValue ?: return null
            val protocol = s.protocol ?: return null
            return ProtocolRegistry.encode(protocol, addr, cmd, toggle)?.let {
                IrSignal(it.carrierHz, it.patternUs, it.repeat, "flipper:${s.name}")
            }
        }

        fun renderRaw(s: FlipperSignal): IrSignal? {
            if (s.type != "raw" || s.dataUs.size < 2) return null
            return IrSignal(s.frequencyHz, s.dataUs.map { it.toInt() }.toIntArray(), source = "flipper:${s.name}")
        }

        fun render(s: FlipperSignal): IrSignal? = renderParsed(s) ?: renderRaw(s)
    }
}
