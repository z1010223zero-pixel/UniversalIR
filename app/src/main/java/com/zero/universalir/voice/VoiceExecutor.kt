package com.zero.universalir.voice

import com.zero.universalir.core.codec.BroadlinkCode
import com.zero.universalir.core.codec.SmartIrParser
import com.zero.universalir.core.ir.IrSignal
import com.zero.universalir.core.ir.IrTransmitter
import com.zero.universalir.core.ir.signalForPress
import com.zero.universalir.core.model.DeviceType
import com.zero.universalir.data.codes.CodeLibrary
import com.zero.universalir.data.db.RemoteDao
import com.zero.universalir.data.db.RemoteDeviceEntity
import javax.inject.Inject
import javax.inject.Singleton

/** 执行语音/深链接/广播指令的唯一入口。 */
@Singleton
class VoiceExecutor @Inject constructor(
    private val dao: RemoteDao,
    private val library: CodeLibrary,
    private val transmitter: IrTransmitter,
    private val feedback: FeedbackBus,
    private val router: DeepLinkRouter,
) {

    suspend fun execute(cmd: VoiceCommand): String {
        val device = router.resolveDevice(cmd.deviceKey) ?: run {
            val msg = "找不到设备「${cmd.deviceKey}」"
            feedback.post(msg)
            return msg
        }
        val result = when (cmd.action) {
            "on" -> sendMainKey(device, preferOn = true)
            "off" -> sendMainKey(device, preferOn = false)
            "toggle" -> sendMainKey(device, preferOn = null)
            "key" -> sendKeyLabel(device, cmd.keyLabel ?: "")
            "ac" -> sendAc(device, cmd.power, cmd.mode, cmd.fan, cmd.temp)
            else -> "未知动作 ${cmd.action}"
        }
        feedback.post(result)
        return result
    }

    /** 设备主开关：flipper 试码绑定的信号；on/off 时优先精确信号。 */
    suspend fun sendMainKey(device: RemoteDeviceEntity, preferOn: Boolean? = null): String {
        if (DeviceType.from(device.type) == DeviceType.AC) {
            // toggle 按当前状态取反，而不是恒开机
            return sendAc(device, power = preferOn ?: !device.acPower, mode = null, fan = null, temp = null)
        }
        val entry = device.libraryFileId?.let { library.entry(it) }
            ?: return "设备「${device.name}」没有绑定码库"
        val signals = try {
            library.loadFlipperSignals(entry)
        } catch (e: Exception) {
            return "码库加载失败：${e.message}"
        }
        val target: com.zero.universalir.data.codes.CodeLibrary.LibrarySignal? = when (preferOn) {
            true -> signals.firstOrNull { isOnName(it.name) }
                ?: signals.firstOrNull { it.name == device.boundSignalName }
            false -> signals.firstOrNull { isOffName(it.name) }
                ?: signals.firstOrNull { it.name == device.boundSignalName }
            null -> signals.firstOrNull { it.name == device.boundSignalName }
                ?: signals.firstOrNull { isOnName(it.name) }
        } ?: signals.firstOrNull()
        if (target == null) return "码表为空"
        val result = transmitter.transmit(target.signalForPress(device.id))
        return describe(device.name, target.name, result)
    }

    /** 按键名发射：按钮记录 → 库内信号名（精确/包含）→ 主键兜底。 */
    suspend fun sendKeyLabel(device: RemoteDeviceEntity, label: String): String {
        if (label.isBlank()) return "按键名为空"
        val entry = device.libraryFileId?.let { library.entry(it) }
            ?: return "设备「${device.name}」没有绑定码库"
        if (entry.kind == "smartir_climate") {
            // 归一后交给 sendAc 按码表 operationModes 校验（标签本身是规范名时不再被误丢）
            return sendAc(device, power = null, mode = DeepLinkRouter.normalizeMode(label), fan = null, temp = null)
        }
        val signals = try {
            library.loadFlipperSignals(entry)
        } catch (e: Exception) {
            return "码库加载失败：${e.message}"
        }
        val target = signals.firstOrNull { it.name == label }
            ?: signals.firstOrNull { it.name.contains(label) || label.contains(it.name) }
            ?: signals.firstOrNull {
                isOnName(it.name) && (label.contains("开") || label.equals("on", true))
            }
            ?: signals.firstOrNull {
                isOffName(it.name) && (label.contains("关") || label.equals("off", true))
            }
        if (target == null) return "码表没有「$label」按键"
        val result = transmitter.transmit(target.signalForPress(device.id))
        return describe(device.name, target.name, result)
    }

    /** 空调状态设置。全状态码：改任何参数都重发整帧。 */
    suspend fun sendAc(
        device: RemoteDeviceEntity,
        power: Boolean?,
        mode: String?,
        fan: String?,
        temp: Int?,
    ): String {
        val entry = device.libraryFileId?.let { library.entry(it) }
            ?: return "设备「${device.name}」没有绑定空调码表"
        val table = try {
            library.loadAcTable(entry)
        } catch (e: Exception) {
            return "码表加载失败：${e.message}"
        } ?: return "码表格式不支持"

        val newPower = power ?: (device.acPower || mode != null || temp != null)
        if (!newPower) {
            val code = SmartIrParser.codeForOff(table)
                ?: return "码表没有关闭码"
            val signal = BroadlinkCode.toSignal(code) ?: return "关闭码解码失败"
            val result = transmitter.transmit(signal)
            // 只写电源列：mode/fan/temp 未被本条指令改动，避免覆盖并发写入
            dao.updateAcPower(device.id, false)
            return describe(device.name, "关闭", result)
        }
        // 模式必须属于码表支持集合（"off" 除外），否则明确报错而不是重发当前状态帧
        if (mode != null && mode != SmartIrParser.MODE_OFF &&
            table.operationModes.none { it.equals(mode, ignoreCase = true) }
        ) {
            return "码表不支持模式「$mode」"
        }
        val newMode = mode
            ?: device.acMode
            ?: table.operationModes.firstOrNull { it != SmartIrParser.MODE_OFF }
            ?: "cool"
        if (newMode == SmartIrParser.MODE_OFF) {
            return sendAc(device, power = false, mode = null, fan = null, temp = null)
        }
        val newFan = fan
            ?: device.acFan
            ?: table.fanModes.firstOrNull()
            ?: SmartIrParser.FAN_ANY
        val newTemp = temp ?: device.acTemp ?: ((table.minTemperature + table.maxTemperature) / 2)

        val code = lookupFlex(table, newMode, newFan, newTemp)
            ?: return "码表没有该组合：模式 $newMode / 风速 $newFan / ${newTemp}℃"
        val signal = BroadlinkCode.toSignal(code) ?: return "码解码失败"
        val result = transmitter.transmit(signal)
        // 用本次实际发送的状态做定向列更新，避免用发射前的陈旧快照整行覆盖并发写入
        dao.updateAcState(device.id, acPower = true, acMode = newMode, acFan = newFan, acTemp = newTemp)
        return describe(device.name, "$newMode ${newTemp}℃", result)
    }

    private fun lookupFlex(
        table: SmartIrParser.AcCodeTable,
        mode: String,
        fan: String,
        temp: Int,
    ): String? {
        SmartIrParser.lookup(table, mode, fan, temp)?.let { return it }
        SmartIrParser.lookup(table, mode, SmartIrParser.FAN_ANY, temp)?.let { return it }
        // 任一风速下有该温度就用它（风速码可能并入温度码）
        val fanMap = table.commands[mode] ?: return null
        return fanMap.values.firstOrNull { it.containsKey(temp) }?.get(temp)
    }

    private fun describe(deviceName: String, action: String, result: IrTransmitter.TransmitResult): String =
        when (result) {
            is IrTransmitter.TransmitResult.Sent -> "已发送：$deviceName $action"
            is IrTransmitter.TransmitResult.NoHardware -> "手机没有红外发射器"
            is IrTransmitter.TransmitResult.PatternTooLong -> "红外码超长（${result.durationUs / 1000}ms），未发送"
            is IrTransmitter.TransmitResult.Invalid -> "无效红外码：$action"
        }

    private fun isOnName(name: String): Boolean {
        val n = name.lowercase()
        return n == "on" || n == "power" || n.contains("开机") || (n.contains("开") && !n.contains("关"))
    }

    private fun isOffName(name: String): Boolean {
        val n = name.lowercase()
        return n == "off" || n.contains("关机") || n.contains("关闭") || n.contains("关")
    }
}
