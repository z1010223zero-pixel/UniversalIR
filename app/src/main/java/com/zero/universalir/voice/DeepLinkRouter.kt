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
                ParseResult.Command(
                    VoiceCommand(
                        deviceKey = key,
                        action = "ac",
                        power = parsePower(uri.getQueryParameter("power")),
                        mode = uri.getQueryParameter("mode")?.let(::normalizeMode),
                        fan = uri.getQueryParameter("fan")?.lowercase(),
                        temp = uri.getQueryParameter("temp")?.toIntOrNull(),
                    )
                )
            }
            else -> ParseResult.Unknown
        }
    }

    /** 解析设备：数字 id 精确 → 名称精确 → 名称模糊 → 房间+名称模糊。 */
    suspend fun resolveDevice(key: String): RemoteDeviceEntity? {
        key.toLongOrNull()?.let { id ->
            dao.device(id)?.let { return it }
        }
        val all = dao.allDevices()
        all.firstOrNull { it.name == key }?.let { return it }
        fuzzyMatch(key, all.map { it.name })?.let { name ->
            return all.first { it.name == name }
        }
        val fullNames = all.map { "${it.room}${it.name}" }
        fuzzyMatch(key, fullNames)?.let { name ->
            return all.first { "${it.room}${it.name}" == name }
        }
        return null
    }

    companion object {
        const val SCHEME = "uniir"

        /**
         * power 参数归一：大小写不敏感；已知值映射 on/off，
         * 未知值返回 null（由执行器按当前状态推断，避免外部链接拼错时误关空调）。
         */
        fun parsePower(raw: String?): Boolean? = raw?.trim()?.lowercase()?.let {
            when (it) {
                "on", "true", "1", "open", "开", "开机" -> true
                "off", "false", "0", "close", "关", "关机" -> false
                else -> null
            }
        }

        /**
         * 模糊匹配打分：重叠覆盖率优先（较短串完全落在较长串内的比例），
         * 覆盖率相同则"设备名包含完整指令"优于"指令包含部分设备名"。
         * 这样"灯"命中"台灯"（覆盖 1/2）而不是"客厅灯带"（覆盖 1/4），
         * "客厅灯"命中"客厅灯带"（覆盖 3/4）而不是"台灯"（1/3）。
         * 返回命中的名字，无命中返回 null。
         */
        fun fuzzyMatch(key: String, names: List<String>): String? {
            var best: String? = null
            var bestCoverage = -1.0
            var bestRank = Int.MAX_VALUE
            for (n in names) {
                val (coverage, rank) = when {
                    n == key -> 1.0 to 0
                    n.contains(key) -> key.length.toDouble() / n.length to 1
                    key.contains(n) -> n.length.toDouble() / key.length to 2
                    else -> continue
                }
                if (coverage > bestCoverage || (coverage == bestCoverage && rank < bestRank)) {
                    best = n
                    bestCoverage = coverage
                    bestRank = rank
                }
            }
            return best
        }

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
