package com.zero.universalir.data.codes

import android.content.Context
import com.zero.universalir.core.codec.FlipperIrParser
import com.zero.universalir.core.codec.SmartIrParser
import com.zero.universalir.core.ir.IrSignal
import com.zero.universalir.core.model.DeviceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
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

    data class LibrarySignal(val name: String, val signal: IrSignal)

    private val json = Json { ignoreUnknownKeys = true }

    val index: LibraryIndex by lazy {
        try {
            json.decodeFromString(LibraryIndex.serializer(), readAsset("index.json"))
        } catch (_: Exception) {
            LibraryIndex()
        }
    }

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

    /** flipper 条目 → 按键信号列表（parsed 渲染失败回退 raw），同名去重。 */
    fun loadFlipperSignals(entry: Entry): List<LibrarySignal> {
        val parser = FlipperIrParser()
        val out = LinkedHashMap<String, LibrarySignal>()
        parser.parse(readAsset(entry.file)).forEach { s ->
            val signal = FlipperIrParser.render(s) ?: return@forEach
            val label = s.name.ifBlank { "信号" }
            if (!out.containsKey(label)) out[label] = LibrarySignal(label, signal)
        }
        return out.values.toList()
    }

    fun loadAcTable(entry: Entry): SmartIrParser.AcCodeTable? =
        SmartIrParser().parse(readAsset(entry.file), entry.file)

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
            GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
}
