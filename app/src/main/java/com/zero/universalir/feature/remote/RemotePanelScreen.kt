package com.zero.universalir.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
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
    Column(modifier.fillMaxSize().padding(16.dp)) {
        PowerButton(onClick = vm::sendMain)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            items(buttons, key = { it.id }) { button ->
                Card(onClick = { vm.sendLabel(button.label) }, modifier = Modifier.height(64.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            button.label,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerButton(onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(72.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
            Text("  开关 / 电源", style = MaterialTheme.typography.titleMedium)
        }
    }
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
