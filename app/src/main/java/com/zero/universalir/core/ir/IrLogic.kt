package com.zero.universalir.core.ir

/**
 * 纯 Kotlin 发射前置逻辑，脱离 Android 环境便于单元测试。
 *
 * 关键事实（AOSP）：
 * - transmit() 单次只发射总时长 < 2s 的 pattern，超限不发射且不报错；
 * - transmit() 失败静默返回，无法感知；
 * - 载频需落在 getCarrierFrequencies() 报告的某个区间内，超出可能被静默钳位。
 */
object IrLogic {

    /** ConsumerIrManager 文档：只有短于 2 秒的 pattern 会被发射。留一点余量。 */
    const val MAX_PATTERN_US: Long = 1_999_000

    data class CarrierRange(val minHz: Int, val maxHz: Int)

    data class Capability(
        val hasEmitter: Boolean,
        val ranges: List<CarrierRange>,
    )

    sealed class SendCheck {
        data object Ok : SendCheck()
        data class TooLong(val durationUs: Long) : SendCheck()
        data object EmptyPattern : SendCheck()
    }

    fun checkPattern(patternUs: IntArray): SendCheck {
        if (patternUs.isEmpty()) return SendCheck.EmptyPattern
        val total = patternUs.sumOf { it.toLong() }
        return if (total > MAX_PATTERN_US) SendCheck.TooLong(total) else SendCheck.Ok
    }

    /**
     * 请求载频若不在支持区间内，退而选择包含 38k 标准值的区间，否则取区间中点最近者。
     * 区间为空（取不到）时原样返回请求值，交给硬件自行处理。
     */
    fun pickCarrier(requestedHz: Int, ranges: List<CarrierRange>): Int {
        if (ranges.isEmpty()) return requestedHz
        if (ranges.any { requestedHz in it.minHz..it.maxHz }) return requestedHz
        val standard = ranges.firstOrNull { 38_000 in it.minHz..it.maxHz }
        if (standard != null) return 38_000
        return ranges.minByOrNull { kotlin.math.abs((it.minHz + it.maxHz) / 2 - requestedHz) }
            ?.let { (it.minHz + it.maxHz) / 2 } ?: requestedHz
    }
}
