package com.zero.universalir.voice

import android.net.Uri
import com.zero.universalir.data.db.RemoteDao
import com.zero.universalir.data.db.RemoteDeviceEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音指令模型与深链接解析。
 *
 * URI 语法（scheme uniir）：
 * - uniir://home
 * - uniir://device/{id|名称}[/on|/off|/toggle|/key/{按键名}]
 * - uniir://ac/{id|名称}?power=on&mode=cool&fan=low&temp=26
 *
 * 小爱训练计划"打开网址"、Tasker、桌面快捷方式都走这些 URI。
 */
data class VoiceCommand(
    val deviceKey: String,
    val action: String,            // on / off / toggle / key / ac
    val keyLabel: String? = null,
    val power: Boolean? = null,
    val mode: String? = null,
    val fan: String? = null,
    val temp: Int? = null,
)

@Singleton
class DeepLinkRouter @Inject constructor(private val dao: RemoteDao) {

    sealed class ParseResult {
        data class Command(val cmd: VoiceCommand) : ParseResult()
        data object OpenHome : ParseResult()
        data object Unknown : ParseResult()
    }

    fun parse(uri: Uri?): ParseResult {
        if (uri == null || uri.scheme != SCHEME) return ParseResult.Unknown
        return when (uri.host) {
            "home" -> ParseResult.OpenHome
            "device" -> {
                val segments = uri.pathSegments
                val key = segments.firstOrNull() ?: return ParseResult.Unknown
                val action = segments.getOrNull(1) ?: "toggle"
                val keyLabel = if (action == "key") segments.getOrNull(2) else null
                ParseResult.Command(
                    VoiceCommand(
                        deviceKey = key,
                        action = action,
                        keyLabel = keyLabel,
                    )
                )
            }
            "ac" -> {
                val key = uri.pathSegments.firstOrNull() ?: return ParseResult.Unknown
                val powerParam = uri.getQueryParameter("power")
                ParseResult.Command(
                    VoiceCommand(
                        deviceKey = key,
                        action = "ac",
                        power = when {
                            powerParam == null -> null
                            powerParam in listOf("on", "true", "1") -> true
                            else -> false
                        },
                        mode = uri.getQueryParameter("mode")?.let(::normalizeMode),
                        fan = uri.getQueryParameter("fan")?.lowercase(),
                        temp = uri.getQueryParameter("temp")?.toIntOrNull(),
                    )
                )
            }
            else -> ParseResult.Unknown
        }
    }

    /** 解析设备：数字 id 精确 → 名称精确 → 名称包含 → 房间+名称。 */
    suspend fun resolveDevice(key: String): RemoteDeviceEntity? {
        key.toLongOrNull()?.let { id ->
            dao.device(id)?.let { return it }
        }
        val all = dao.allDevices()
        all.firstOrNull { it.name == key }?.let { return it }
        all.firstOrNull { it.name.contains(key) || key.contains(it.name) }?.let { return it }
        all.firstOrNull { "${it.room}${it.name}".contains(key) || key.contains("${it.room}${it.name}") }
            ?.let { return it }
        return null
    }

    companion object {
        const val SCHEME = "uniir"

        /** 模式别名归一：中英文 → SmartIR operationModes 常用值。 */
        fun normalizeMode(raw: String): String {
            val m = raw.trim().lowercase()
            return when {
                m in setOf("cool", "制冷", "冷气", "cooling") -> "cool"
                m in setOf("heat", "制热", "暖气", "heating") -> "heat"
                m in setOf("dry", "除湿", "抽湿") -> "dry"
                m in setOf("fan_only", "fanonly", "fan", "送风", "通风") -> "fan_only"
                m in setOf("auto", "自动", "智能") -> "auto"
                m in setOf("off", "关", "关闭") -> "off"
                else -> m
            }
        }
    }
}
