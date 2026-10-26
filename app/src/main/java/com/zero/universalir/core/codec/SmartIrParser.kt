package com.zero.universalir.core.codec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * SmartIR climate 码表解析（数据源 MIT 许可，github smartHomeHub/SmartIR codes/climate）。
 * 码值为 Broadlink base64；不同文件结构有差异，统一归一为 mode → fan → temp → code，
 * 无风速层级的文件（fan_only 直挂温度）归一到 [FAN_ANY]。
 */
class SmartIrParser(private val json: Json = Json) {

    data class AcCodeTable(
        val manufacturer: String,
        val minTemperature: Int,
        val maxTemperature: Int,
        val precision: Int,
        val operationModes: List<String>,
        val fanModes: List<String>,
        val commands: Map<String, Map<String, Map<Int, String>>>,
        val fileName: String,
    )

    fun parse(text: String, fileName: String = ""): AcCodeTable? {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return null
        }
        val manufacturer = root.str("manufacturer") ?: return null
        val minTemp = root.int("minTemperature") ?: 16
        val maxTemp = root.int("maxTemperature") ?: 30
        val precision = root.int("precision") ?: 1
        val operationModes = root.list("operationModes")
        val fanModes = root.list("fanModes")
        val commandsObj = root["commands"] as? JsonObject ?: return null

        val commands = HashMap<String, Map<String, Map<Int, String>>>()
        for ((key, value) in commandsObj) {
            when {
                value is JsonPrimitive && value.isString ->
                    commands[key] = mapOf(FAN_ANY to mapOf(0 to value.content))

                value is JsonObject -> {
                    val hasDirectTemps = value.values.any { it is JsonPrimitive && it.isString }
                    val fanMap = HashMap<String, Map<Int, String>>()
                    if (hasDirectTemps) {
                        val temps = HashMap<Int, String>()
                        for ((t, code) in value) {
                            if (code is JsonPrimitive && code.isString) t.toIntOrNull()?.let { temps[it] = code.content }
                        }
                        if (temps.isNotEmpty()) fanMap[FAN_ANY] = temps
                    } else {
                        for ((fanKey, fanVal) in value) {
                            if (fanVal !is JsonObject) continue
                            val temps = HashMap<Int, String>()
                            for ((t, code) in fanVal) {
                                if (code is JsonPrimitive && code.isString) t.toIntOrNull()?.let { temps[it] = code.content }
                            }
                            if (temps.isNotEmpty()) fanMap[fanKey] = temps
                        }
                    }
                    if (fanMap.isNotEmpty()) commands[key] = fanMap
                }
            }
        }
        if (commands.isEmpty()) return null
        return AcCodeTable(manufacturer, minTemp, maxTemp, precision, operationModes, fanModes, commands, fileName)
    }

    companion object {
        const val FAN_ANY = "_"
        const val MODE_OFF = "off"

        fun lookup(table: AcCodeTable, mode: String, fan: String, temp: Int): String? {
            val fanMap = table.commands[mode] ?: return null
            val temps = fanMap[fan] ?: fanMap[FAN_ANY] ?: return null
            return temps[temp]
        }

        fun codeForOff(table: AcCodeTable): String? = lookup(table, MODE_OFF, FAN_ANY, 0)
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.let {
        it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt()
    }

private fun JsonObject.list(key: String): List<String> =
    (this[key] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()
