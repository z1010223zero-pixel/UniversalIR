package com.zero.universalir.core.ir

import android.content.Context
import android.hardware.ConsumerIrManager
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
        data object Sent : TransmitResult()
        data object NoHardware : TransmitResult()
        data class PatternTooLong(val durationUs: Long) : TransmitResult()
        data class Invalid(val reason: String) : TransmitResult()
    }

    private val manager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    fun capability(): IrLogic.Capability {
        val m = manager ?: return IrLogic.Capability(false, emptyList())
        val ranges = try {
            m.carrierFrequencies?.map {
                IrLogic.CarrierRange(it.minFrequency, it.maxFrequency)
            }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        return IrLogic.Capability(m.hasIrEmitter(), ranges)
    }

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
        val carrier = IrLogic.pickCarrier(signal.carrierHz, capability().ranges)
        frames.forEach { frame ->
            m.transmit(carrier, frame)
        }
        TransmitResult.Sent
    }
}
