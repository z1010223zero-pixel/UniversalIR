package com.zero.universalir.feature.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zero.universalir.core.codec.SmartIrParser
import com.zero.universalir.core.ir.IrSignal
import com.zero.universalir.core.ir.IrTransmitter
import com.zero.universalir.core.ir.signalForPress
import com.zero.universalir.data.codes.CodeLibrary
import com.zero.universalir.data.db.RemoteDao
import com.zero.universalir.data.db.RemoteDeviceEntity
import com.zero.universalir.voice.VoiceExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RemoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dao: RemoteDao,
    private val library: CodeLibrary,
    private val executor: VoiceExecutor,
    private val transmitter: IrTransmitter,
) : ViewModel() {

    private val deviceId: Long = savedStateHandle["id"] ?: 0L

    val device = dao.deviceFlow(deviceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val buttons = flow { emit(dao.buttons(deviceId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val signalsByNameFlow = MutableStateFlow<Map<String, CodeLibrary.LibrarySignal>>(emptyMap())

    private val acTableFlow = MutableStateFlow<SmartIrParser.AcCodeTable?>(null)
    val acTable = acTableFlow.asStateFlow()

    init {
        viewModelScope.launch {
            val d = dao.device(deviceId) ?: return@launch
            val entry = d.libraryFileId?.let { library.entry(it) } ?: return@launch
            try {
                when (entry.kind) {
                    "flipper" -> signalsByNameFlow.value =
                        library.loadFlipperSignals(entry).associateBy { it.name }

                    "smartir_climate" -> acTableFlow.value = library.loadAcTable(entry)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun sendMain() {
        viewModelScope.launch {
            dao.device(deviceId)?.let { executor.sendMainKey(it, preferOn = null) }
        }
    }

    fun sendLabel(label: String) {
        viewModelScope.launch {
            dao.device(deviceId)?.let { executor.sendKeyLabel(it, label) }
        }
    }

    fun sendSignal(name: String) {
        viewModelScope.launch {
            signalsByNameFlow.value[name]?.let { target ->
                transmitter.transmit(target.signalForPress(deviceId))
            }
        }
    }

    fun acPower(on: Boolean) = acCall { executor.sendAc(it, power = on, mode = null, fan = null, temp = null) }
    fun acMode(mode: String) = acCall { executor.sendAc(it, power = true, mode = mode, fan = null, temp = null) }
    fun acTemp(temp: Int) = acCall { executor.sendAc(it, power = true, mode = null, fan = null, temp = temp) }
    fun acFan(fan: String) = acCall { executor.sendAc(it, power = true, mode = null, fan = fan, temp = null) }

    private fun acCall(block: suspend (RemoteDeviceEntity) -> Unit) {
        viewModelScope.launch {
            dao.device(deviceId)?.let { block(it) }
        }
    }

    fun deleteDevice(onDone: () -> Unit) {
        viewModelScope.launch {
            dao.deleteButtonsOf(deviceId)
            dao.device(deviceId)?.let { dao.deleteDevice(it) }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotePanelScreen(
    deviceId: Long,
    onBack: () -> Unit,
    onVoiceGuide: () -> Unit,
    vm: RemoteViewModel = hiltViewModel(),
) {
    val device by vm.device.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "遥控器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onVoiceGuide) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "语音接入")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                },
            )
        },
    ) { padding ->
        when {
            device == null -> Box(Modifier.fillMaxSize().padding(padding))
            device!!.type == "AC" -> AcPanel(vm, device!!, Modifier.padding(padding))
            else -> GridPanel(vm, Modifier.padding(padding))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除设备") },
            text = { Text("确定删除「${device?.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteDevice(onDone = onBack)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun GridPanel(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val buttons by vm.buttons.collectAsState()
    val labels = buttons.map { it.label }

    val up = findKey(labels, "up", "上", "方向up", "arrowup")
    val down = findKey(labels, "down", "下", "方向down", "arrowdown")
    val left = findKey(labels, "left", "左", "方向左", "arrowleft")
    val right = findKey(labels, "right", "右", "方向右", "arrowright")
    val ok = findKey(labels, "ok", "okay", "enter", "确定", "确认", "ok键")
    val mute = findKey(labels, "mute", "静音")
    val volUp = findKey(labels, "volup", "volumeup", "vol+", "volume+", "音量+", "音量加", "volup键")
    val volDown = findKey(labels, "voldown", "voldn", "volumedown", "vol-", "volume-", "音量-", "音量减")
    val chUp = findKey(labels, "chup", "channelup", "ch+", "channel+", "频道+", "频道加")
    val chDown = findKey(labels, "chdown", "chdn", "channeldown", "ch-", "channel-", "频道-", "频道减")

    val used = setOfNotNull(up, down, left, right, ok, mute, volUp, volDown, chUp, chDown)
    val powerLabels = labels.filter { isPowerLabel(it) }.toSet()
    val extras = buttons.filter { it.label !in used && it.label !in powerLabels }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = vm::sendMain,
                modifier = Modifier.weight(1f).height(68.dp),
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(26.dp))
                Text("  电源", style = MaterialTheme.typography.titleMedium)
            }
            if (mute != null) {
                OutlinedButton(
                    onClick = { vm.sendLabel(mute) },
                    modifier = Modifier.weight(0.55f).height(68.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null)
                    Text("  静音")
                }
            }
        }

        if (up != null && down != null && left != null && right != null) {
            DPad(up, down, left, right, ok, vm::sendLabel)
        }

        if (volUp != null || volDown != null || chUp != null || chDown != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (volUp != null || volDown != null) {
                    Rocker("音量", volUp, volDown, vm::sendLabel)
                }
                if (chUp != null || chDown != null) {
                    Rocker("频道", chUp, chDown, vm::sendLabel)
                }
            }
        }

        if (extras.isNotEmpty()) {
            Text("其它按键", style = MaterialTheme.typography.titleSmall)
            extras.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { button ->
                        Card(onClick = { vm.sendLabel(button.label) }, modifier = Modifier.weight(1f).height(56.dp)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    button.label,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (labels.isEmpty()) {
            Text("码表加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DPad(
    up: String,
    down: String,
    left: String,
    right: String,
    ok: String?,
    onSend: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        ) {
            DPadArrow(Icons.Default.KeyboardArrowUp, "上", up, Modifier.align(Alignment.TopCenter).padding(top = 6.dp), onSend)
            DPadArrow(Icons.Default.KeyboardArrowDown, "下", down, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), onSend)
            DPadArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "左", left, Modifier.align(Alignment.CenterStart).padding(start = 6.dp), onSend)
            DPadArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "右", right, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), onSend)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = { ok?.let(onSend) }, enabled = ok != null) {
                    Text(
                        "OK",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DPadArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    label: String,
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit,
) {
    androidx.compose.material3.FilledTonalIconButton(
        onClick = { onSend(label) },
        modifier = modifier.size(56.dp),
    ) {
        Icon(icon, contentDescription = desc)
    }
}

@Composable
private fun Rocker(title: String, upLabel: String?, downLabel: String?, onSend: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .padding(top = 6.dp)
                .width(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = { upLabel?.let(onSend) },
                enabled = upLabel != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "$title 加")
            }
            HorizontalDivider(Modifier.width(44.dp))
            IconButton(
                onClick = { downLabel?.let(onSend) },
                enabled = downLabel != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "$title 减")
            }
        }
    }
}

private fun normalizeLabel(label: String): String =
    label.lowercase().filter { it.isLetterOrDigit() || it == '+' || it == '-' }

private fun findKey(labels: List<String>, vararg aliases: String): String? {
    val wanted = aliases.map { normalizeLabel(it) }.toSet()
    return labels.firstOrNull { normalizeLabel(it) in wanted }
}

private fun isPowerLabel(label: String): Boolean {
    val n = normalizeLabel(label)
    return n == "power" || n == "电源" || n == "开关" || n == "on" || n == "off" ||
        n.contains("power")
}

@Composable
private fun AcPanel(vm: RemoteViewModel, device: RemoteDeviceEntity, modifier: Modifier = Modifier) {
    val table by vm.acTable.collectAsState()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 电源
        FilledIconButton(
            onClick = { vm.acPower(!device.acPower) },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Text(
                    if (device.acPower) "  开机中 · 点按关闭" else "  已关机 · 点按开机",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        val t = table
        if (t == null) {
            Text("码表加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        // 温度步进（钳制到码表上下限，到边界禁用按钮）
        val temp = device.acTemp ?: ((t.minTemperature + t.maxTemperature) / 2)
        val minTemp = t.minTemperature
        val maxTemp = t.maxTemperature
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { vm.acTemp((temp - t.precision).coerceIn(minTemp, maxTemp)) },
                enabled = device.acPower && temp - t.precision >= minTemp,
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "${temp}℃",
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { vm.acTemp((temp + t.precision).coerceIn(minTemp, maxTemp)) },
                enabled = device.acPower && temp + t.precision <= maxTemp,
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }

        // 模式
        ChipRow(
            label = "模式",
            options = t.operationModes.filter { it != SmartIrParser.MODE_OFF && t.commands[it] != null },
            selected = device.acMode,
            enabled = device.acPower,
            labelOf = ::modeLabel,
        ) { vm.acMode(it) }

        // 风速
        ChipRow(
            label = "风速",
            options = t.fanModes.ifEmpty { listOf(SmartIrParser.FAN_ANY) },
            selected = device.acFan,
            enabled = device.acPower,
            labelOf = ::fanLabel,
        ) { vm.acFan(it) }

        Text(
            "红外空调码是全状态帧：每次调整都会整帧重发。若设备无响应，检查是否对着设备且在接收范围内。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: String?,
    enabled: Boolean,
    labelOf: (String) -> String,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            options.take(4).forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    label = { Text(labelOf(option)) },
                )
            }
        }
        if (options.size > 4) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                options.drop(4).take(4).forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        enabled = enabled,
                        label = { Text(labelOf(option)) },
                    )
                }
            }
        }
    }
}

fun modeLabel(mode: String): String = when (mode.lowercase()) {
    "cool" -> "制冷"
    "heat" -> "制热"
    "dry" -> "除湿"
    "fan_only", "fan" -> "送风"
    "auto" -> "自动"
    "heat_cool" -> "冷暖"
    else -> mode
}

fun fanLabel(fan: String): String = when (fan.lowercase()) {
    "low" -> "低风"
    "mid", "medium" -> "中风"
    "high" -> "高风"
    "auto" -> "自动"
    "_" -> "默认"
    else -> fan
}
