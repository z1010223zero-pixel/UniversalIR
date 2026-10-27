package com.zero.universalir.core.model

/** 设备类别。 */
enum class DeviceType(val label: String) {
    TV("电视"),
    BOX("机顶盒/盒子"),
    LIGHT("灯具"),
    FAN("风扇"),
    AC("空调"),
    PROJECTOR("投影仪"),
    AUDIO("音响/功放"),
    GENERIC("通用");

    companion object {
        fun from(value: String): DeviceType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GENERIC
    }
}
