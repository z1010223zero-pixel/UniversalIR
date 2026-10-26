package com.zero.universalir.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zero.universalir.core.ir.IrTransmitter
import com.zero.universalir.core.model.DeviceType
import com.zero.universalir.data.codes.CodeLibrary
import com.zero.universalir.data.db.DeviceButtonEntity
import com.zero.universalir.data.db.RemoteDao
import com.zero.universalir.data.db.RemoteDeviceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AddStep { TYPE, BRAND, MODEL, TRIAL, NAME }

data class AddUiState(
    val step: AddStep = AddStep.TYPE,
    val type: DeviceType? = null,
    val brands: List<String> = emptyList(),
    val models: List<CodeLibrary.Entry> = emptyList(),
    val brand: String? = null,
    val entry: CodeLibrary.Entry? = null,
    val signals: List<CodeLibrary.LibrarySignal> = emptyList(),
    val trialIndex: Int = 0,
    val autoTrial: Boolean = false,
    val foundSignal: CodeLibrary.LibrarySignal? = null,
    val name: String = "",
    val room: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val libraryEmpty: Boolean = false,
)

@HiltViewModel
class AddDeviceViewModel @Inject constructor(
    private val library: CodeLibrary,
    private val dao: RemoteDao,
    private val transmitter: IrTransmitter,
) : ViewModel() {

    private val _state = MutableStateFlow(AddUiState())
    val state = _state.asStateFlow()

    private var trialJob: Job? = null

    init {
        _state.update { it.copy(libraryEmpty = library.index.entries.isEmpty()) }
    }

    fun selectType(type: DeviceType) {
        val brands = library.entriesFor(type).map { it.brand }.distinct().sorted()
        _state.update { it.copy(step = AddStep.BRAND, type = type, brands = brands) }
    }

    fun selectBrand(brand: String) {
        val models = library.entriesFor(_state.value.type ?: DeviceType.GENERIC)
            .filter { it.brand == brand }
            .sortedBy { it.model }
        _state.update { it.copy(step = AddStep.MODEL, brand = brand, models = models) }
    }

    fun selectEntry(entry: CodeLibrary.Entry) {
        if (entry.kind == "smartir_climate") {
            _state.update {
                it.copy(step = AddStep.NAME, entry = entry, name = entry.model, signals = emptyList())
            }
        } else {
            viewModelScope.launch {
                _state.update { it.copy(busy = true, message = "加载码表…") }
                val signals = try {
                    library.trialOrder(library.loadFlipperSignals(entry))
                } catch (e: Exception) {
                    _state.update { it.copy(busy = false, message = "码表加载失败：${e.message}") }
                    return@launch
                }
                if (signals.isEmpty()) {
                    _state.update { it.copy(busy = false, message = "该码表没有可用信号") }
                    return@launch
                }
                _state.update {
                    it.copy(
                        step = AddStep.TRIAL,
                        entry = entry,
                        signals = signals,
                        trialIndex = 0,
                        busy = false,
                        message = "对着设备按【试当前】，或打开自动轮播",
                    )
                }
            }
        }
    }

    fun sendCurrent() {
        val st = _state.value
        val signal = st.signals.getOrNull(st.trialIndex) ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = "发送 ${signal.name}") }
            transmitter.transmit(signal.signal)
            _state.update { it.copy(busy = false) }
        }
    }

    fun moveTrial(delta: Int) {
        val st = _state.value
        if (st.signals.isEmpty()) return
        val next = (st.trialIndex + delta).mod(st.signals.size)
        _state.update { it.copy(trialIndex = next) }
    }

    fun setAutoTrial(enabled: Boolean) {
        trialJob?.cancel()
        if (!enabled) {
            _state.update { it.copy(autoTrial = false) }
            return
        }
        _state.update { it.copy(autoTrial = true) }
        trialJob = viewModelScope.launch {
            while (isActive) {
                val st = _state.value
                val idx = st.trialIndex % st.signals.size.coerceAtLeast(1)
                val signal = st.signals.getOrNull(idx) ?: break
                transmitter.transmit(signal.signal)
                delay(1800)
                _state.update { it.copy(trialIndex = (idx + 1) % st.signals.size) }
            }
        }
    }

    fun markFound() {
        val st = _state.value
        val found = st.signals.getOrNull(st.trialIndex) ?: return
        trialJob?.cancel()
        _state.update {
            it.copy(autoTrial = false, foundSignal = found, step = AddStep.NAME, name = it.entry?.model ?: "", message = "绑定「${found.name}」为主开关")
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setRoom(value: String) = _state.update { it.copy(room = value) }

    fun save(onDone: (Long) -> Unit) {
        val st = _state.value
        val entry = st.entry ?: return
        val name = st.name.trim().ifEmpty { entry.model }
        viewModelScope.launch {
            val boundName = st.foundSignal?.name
                ?: st.signals.firstOrNull { s ->
                    val n = s.name.lowercase()
                    n.contains("power") || n.contains("开")
                }?.name
            val deviceId = dao.insertDevice(
                RemoteDeviceEntity(
                    name = name,
                    room = st.room.trim(),
                    type = st.type?.name ?: DeviceType.GENERIC.name,
                    brand = entry.brand,
                    model = entry.model,
                    libraryFileId = entry.id,
                    boundSignalName = boundName,
                    acTemp = if (entry.kind == "smartir_climate") 26 else null,
                    acMode = if (entry.kind == "smartir_climate") "cool" else null,
                )
            )
            if (st.signals.isNotEmpty()) {
                val ordered = (st.foundSignal?.let { listOf(it) } ?: emptyList()) +
                    st.signals.filter { it !== st.foundSignal }
                dao.insertButtons(
                    ordered.take(80).mapIndexed { index, s ->
                        DeviceButtonEntity(
                            deviceId = deviceId,
                            label = s.name,
                            signalName = s.name,
                            sortOrder = index,
                        )
                    }
                )
            }
            _state.update { AddUiState() }
            onDone(deviceId)
        }
    }

    override fun onCleared() {
        trialJob?.cancel()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    onDone: (Long) -> Unit,
    onBack: () -> Unit,
    vm: AddDeviceViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var search by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stepTitle(state.step)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            state.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            when (state.step) {
                AddStep.TYPE -> TypeStep(vm)
                AddStep.BRAND -> BrandStep(state, search, { search = it }, vm)
                AddStep.MODEL -> ModelStep(state, vm)
                AddStep.TRIAL -> TrialStep(state, vm)
                AddStep.NAME -> NameStep(state, vm, onDone)
            }
        }
    }
}

private fun stepTitle(step: AddStep) = when (step) {
    AddStep.TYPE -> "添加设备 · 选类型"
    AddStep.BRAND -> "添加设备 · 选品牌"
    AddStep.MODEL -> "添加设备 · 选型号"
    AddStep.TRIAL -> "添加设备 · 试码绑定"
    AddStep.NAME -> "添加设备 · 命名"
}

@Composable
private fun TypeStep(vm: AddDeviceViewModel) {
    val state by vm.state.collectAsState()
    if (state.libraryEmpty) {
        Card(Modifier.fillMaxWidth()) {
            Text(
                "内置码库为空。请在项目目录运行 tools/fetch_codes.py 生成后重新安装。",
                Modifier.padding(16.dp),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(DeviceType.entries.filter { it != DeviceType.GENERIC }) { type ->
            Card(
                onClick = { vm.selectType(type) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(type.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun BrandStep(
    state: AddUiState,
    search: String,
    onSearch: (String) -> Unit,
    vm: AddDeviceViewModel,
) {
    Column {
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            label = { Text("搜索品牌") },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            val brands = if (search.isBlank()) state.brands
            else state.brands.filter { it.contains(search, ignoreCase = true) }
            items(brands) { brand ->
                Card(onClick = { vm.selectBrand(brand) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(brand)
                        Text(
                            "${state.brands.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStep(state: AddUiState, vm: AddDeviceViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.models, key = { it.id }) { entry ->
            Card(onClick = { vm.selectEntry(entry) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(entry.model, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (entry.kind) {
                            "smartir_climate" -> "空调全状态码表"
                            else -> "${entry.signalCount} 个按键"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrialStep(state: AddUiState, vm: AddDeviceViewModel) {
    val current = state.signals.getOrNull(state.trialIndex)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    current?.name ?: "-",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "第 ${state.trialIndex + 1} / ${state.signals.size} 个信号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                LinearProgressIndicator(
                    progress = {
                        if (state.signals.isEmpty()) 0f
                        else (state.trialIndex + 1).toFloat() / state.signals.size
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { vm.moveTrial(-1) }, modifier = Modifier.weight(1f)) {
                Text("上一个")
            }
            Button(onClick = { vm.sendCurrent() }, modifier = Modifier.weight(1f), enabled = !state.busy) {
                if (state.busy) CircularProgressIndicator(Modifier.padding(4.dp)) else Text("试当前")
            }
            OutlinedButton(onClick = { vm.moveTrial(1) }, modifier = Modifier.weight(1f)) {
                Text("下一个")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("自动轮播试码（每 1.8 秒发一个）")
            Switch(checked = state.autoTrial, onCheckedChange = { vm.setAutoTrial(it) })
        }
        Button(
            onClick = { vm.markFound() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("设备有反应了，就这个！")
        }
    }
}

@Composable
private fun NameStep(state: AddUiState, vm: AddDeviceViewModel, onDone: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::setName,
            label = { Text("设备名称（语音呼名用）") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.room,
            onValueChange = vm::setRoom,
            label = { Text("位置/房间（可选，如：客厅）") },
            modifier = Modifier.fillMaxWidth(),
        )
        state.foundSignal?.let {
            Text("主开关绑定：${it.name}", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { vm.save(onDone) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.name.isNotBlank() || state.entry?.model?.isNotBlank() == true,
        ) {
            Text("保存设备")
        }
    }
}
