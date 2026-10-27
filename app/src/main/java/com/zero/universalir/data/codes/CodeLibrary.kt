package com.zero.universalir.data.codes

import android.content.Context
import com.zero.universalir.core.codec.FlipperIrParser
import com.zero.universalir.core.codec.ProtocolRegistry
import com.zero.universalir.core.codec.SmartIrParser
import com.zero.universalir.core.ir.IrSignal
import com.zero.universalir.core.model.DeviceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 内置离线码库（assets/codes/，tools/fetch_codes.py 生成）。
 * flipper 条目 → 按键信号列表；smartir_climate 条目 → 空调全状态码表。
 */
@Singleton
class CodeLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Serializable
    data class Entry(
        val id: String,
        val kind: String,          // flipper / smartir_climate
        val deviceType: String,    // tv/light/fan/ac/projector/box/audio
        val brand: String,
        val model: String,
        val file: String,
        val signalCount: Int = 0,
        val minTemperature: Int? = null,
        val maxTemperature: Int? = null,
    )

    @Serializable
    data class SourceInfo(val name: String, val url: String, val license: String)

    @Serializable
    data class LibraryIndex(
        val version: Int = 1,
        val sources: List<SourceInfo> = emptyList(),
        val entries: List<Entry> = emptyList(),
    )

    /**
     * 库内信号。[withToggle] 非 null 表示该协议（RC5/RC5X/RC6）支持翻转位，
     * 发送时可按"新按键"语义翻转后重渲染。
     */
    data class LibrarySignal(
        val name: String,
        val signal: IrSignal,
        val withToggle: ((Boolean) -> IrSignal)? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val indexResult: Result<LibraryIndex> by lazy {
        runCatching { json.decodeFromString(LibraryIndex.serializer(), readAsset("index.json")) }
    }

    val index: LibraryIndex get() = indexResult.getOrElse { LibraryIndex() }

    /** 在 IO 线程确保索引已加载（142KB JSON 解析不宜在主线程做）。 */
    suspend fun ensureIndex(): LibraryIndex = withContext(Dispatchers.IO) { index }

    /** 索引加载失败原因；null 表示正常。避免索引损坏时静默退化成空码库。 */
    val indexError: String? get() =
        indexResult.exceptionOrNull()?.let { it.message ?: it::class.simpleName }

    fun entriesFor(type: DeviceType): List<Entry> =
        index.entries.filter { it.deviceType == type.name.lowercase() }

    fun entry(id: String): Entry? = index.entries.firstOrNull { it.id == id }

    fun search(query: String, type: DeviceType? = null): List<Entry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return index.entries.filter { e ->
            (type == null || e.deviceType == type.name.lowercase()) &&
                (e.brand.lowercase().contains(q) || e.model.lowercase().contains(q))
        }
    }

    /** flipper 条目 → 按键信号列表（parsed 渲染失败回退 raw），同名去重。磁盘 IO 与解析在 IO 线程。 */
    suspend fun loadFlipperSignals(entry: Entry): List<LibrarySignal> = withContext(Dispatchers.IO) {
        val parser = FlipperIrParser()
        val out = LinkedHashMap<String, LibrarySignal>()
        parser.parse(readAsset(entry.file)).forEach { s ->
            val signal = FlipperIrParser.render(s) ?: return@forEach
            val label = s.name.ifBlank { "信号" }
            if (!out.containsKey(label)) {
                val withToggle =
                    if (s.protocol != null && ProtocolRegistry.supportsToggle(s.protocol)) {
                        { t: Boolean -> FlipperIrParser.renderParsed(s, t) ?: signal }
                    } else {
                        null
                    }
                out[label] = LibrarySignal(label, signal, withToggle)
            }
        }
        out.values.toList()
    }

    suspend fun loadAcTable(entry: Entry): SmartIrParser.AcCodeTable? = withContext(Dispatchers.IO) {
        SmartIrParser().parse(readAsset(entry.file), entry.file)
    }

    /** 试码顺序：开关机类信号排最前。 */
    fun trialOrder(signals: List<LibrarySignal>): List<LibrarySignal> =
        signals.sortedByDescending { s -> priority(s.name) }

    private fun priority(name: String): Int {
        val n = name.lowercase()
        return when {
            n.contains("power") || n.contains("开关") || n == "on" || n == "off" -> 3
            n.contains("ok") || n.contains("menu") || n.contains("menu") -> 2
            n.contains("vol") || n.contains("ch") -> 1
            else -> 0
        }
    }

    private fun readAsset(path: String): String =
        context.assets.open("codes/$path").use { input ->
            decodeAsset(path, input.readBytes())
        }

    companion object {
        /** 只有码表文件是 gzip（.gz）；index.json 等索引是明文，按扩展名区分。 */
        internal fun decodeAsset(path: String, bytes: ByteArray): String =
            if (path.endsWith(".gz", ignoreCase = true)) {
                GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                bytes.decodeToString()
            }
    }
}
