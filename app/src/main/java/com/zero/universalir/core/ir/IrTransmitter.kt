package com.zero.universalir.core.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ConsumerIrManager 封装。注意：
 * - 部分机型 hasIrEmitter() 误报 false，因此只要系统服务存在就允许发射；
 * - transmit 静默失败，调用方只能通过"设备是否响应"确认，UI 需要有重发按钮；
 * - repeat 帧逐次单独 transmit，规避单次 2s 上限（空调整帧通常 <2s）。
 */
@Singleton
class IrTransmitter @Inject constructor(
    @ApplicationContext context: Context,
) {
    sealed class TransmitResult {
        data class Sent(val carrierHz: Int, val frames: Int, val durationUs: Long) : TransmitResult()
        data object NoHardware : TransmitResult()
        data class PatternTooLong(val durationUs: Long) : TransmitResult()
        data class Invalid(val reason: String) : TransmitResult()
    }

    private val manager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    /**
     * 硬件能力进程内只查一次：发射器是内置硬件，hasIrEmitter/载频区间不会中途变化。
     */
    private val capabilityOnce: IrLogic.Capability by lazy {
        val m = manager ?: return@lazy IrLogic.Capability(false, emptyList())
        val ranges = try {
            m.carrierFrequencies?.map {
                IrLogic.CarrierRange(it.minFrequency, it.maxFrequency)
            }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        IrLogic.Capability(m.hasIrEmitter(), ranges)
    }

    fun capability(): IrLogic.Capability = capabilityOnce

    suspend fun transmit(signal: IrSignal): TransmitResult = withContext(Dispatchers.IO) {
        val m = manager ?: return@withContext TransmitResult.NoHardware
        val frames = signal.framesWithGap()
        // 每帧（含帧间 gap）单独校验 2s 单次上限
        frames.forEach { frame ->
            when (val check = IrLogic.checkPattern(frame)) {
                is IrLogic.SendCheck.TooLong ->
                    return@withContext TransmitResult.PatternTooLong(check.durationUs)
                is IrLogic.SendCheck.EmptyPattern ->
                    return@withContext TransmitResult.Invalid("空 pattern")
                IrLogic.SendCheck.Ok -> Unit
            }
        }
        val cap = capability()
        val carrier = IrLogic.pickCarrier(signal.carrierHz, cap.ranges)
        val durationUs = frames.sumOf { frame -> frame.sumOf { it.toLong() } }
        Log.d(
            TAG,
            "transmit hasEmitter=${cap.hasEmitter} ranges=${cap.ranges} " +
                "carrier=$carrier frames=${frames.size} duration=${durationUs / 1000}ms " +
                "first=${frames.first().take(24)}",
        )
        return@withContext try {
            frames.forEach { frame ->
                Log.d(TAG, "transmit frame ${frame.size} entries: ${frame.take(24)}")
                m.transmit(carrier, frame)
            }
            TransmitResult.Sent(carrier, frames.size, durationUs)
        } catch (t: Throwable) {
            Log.e(TAG, "transmit failed", t)
            TransmitResult.Invalid("${t::class.simpleName}: ${t.message}")
        }
    }

    private companion object {
        const val TAG = "UniversalIR.Ir"
    }
}
