package com.zero.universalir.feature.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zero.universalir.core.codec.BroadlinkCode
import com.zero.universalir.core.codec.SmartIrParser
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

enum class AddStep { TYPE, BRAND, MODEL, TRIAL, VERIFY, NAME }

data class AddUiState(
    val step: AddStep = AddStep.TYPE,
    val type: DeviceType? = null,
    val brands: List<String> = emptyList(),
    val models: List<CodeLibrary.Entry> = emptyList(),
    val brand: String? = null,
    val entry: CodeLibrary.Entry? = null,
    /** 同品牌同类型下所有可试的型号，试码按型号轮播（而不是型号内部的按键）。 */
    val trialModels: List<CodeLibrary.Entry> = emptyList(),
    val trialModelIndex: Int = 0,
    val signals: List<CodeLibrary.LibrarySignal> = emptyList(),
    /** 当前型号里用于试码的信号（开关类优先）。 */
    val trialSignal: CodeLibrary.LibrarySignal? = null,
    val autoTrial: Boolean = false,
    val foundSignal: CodeLibrary.LibrarySignal? = null,
    /** 主开关确认后要抽验的按键（音量/频道/方向/空调温度等）。 */
    val verifySignals: List<CodeLibrary.LibrarySignal> = emptyList(),
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

    /** 型号 id → 已加载的信号，轮播来回切换时避免重复解压码表。 */
    private val signalCache = mutableMapOf<String, List<CodeLibrary.LibrarySignal>>()

    /** 空调码表缓存（全状态码表解析较慢，轮播来回切换时复用）。 */
    private val acCache = mutableMapOf<String, SmartIrParser.AcCodeTable>()

    init {
        viewModelScope.launch {
            val idx = library.ensureIndex()
            val error = library.indexError
            _state.update {
                it.copy(
                    libraryEmpty = idx.entries.isEmpty(),
                    message = error?.let { e -> "内置码库索引加载失败：$e" },
                )
            }
        }
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
        val st = _state.value
        // 空调全状态码表也进轮播：试的是"制冷 24℃"这类开机帧，抓到响声的型号再绑定。
        val trialModels = library.entriesFor(st.type ?: DeviceType.GENERIC)
            .filter { it.brand == st.brand }
            .sortedWith(compareBy({ it.kind == "smartir_climate" }, { it.model }))
        val start = trialModels.indexOfFirst { it.id == entry.id }.coerceAtLeast(0)
        _state.update {
            it.copy(
                step = AddStep.TRIAL,
                entry = entry,
                trialModels = trialModels,
                trialModelIndex = start,
                signals = emptyList(),
                trialSignal = null,
                autoTrial = false,
                foundSignal = null,
            )
        }
        viewModelScope.launch { prepareModel(start, send = true) }
    }

    /**
     * 加载指定型号并挑出试码信号。返回是否可用；[send] 为真时立即发一次。
     * 轮播用同一个型号列表推进，因此 "上一个/下一个" 与自动轮播都是换型号。
     */
    private suspend fun prepareModel(index: Int, send: Boolean = false): Boolean {
        val st = _state.value
        val entry = st.trialModels.getOrNull(index) ?: return false
        _state.update {
            it.copy(
                busy = true,
                trialModelIndex = index,
                entry = entry,
                trialSignal = null,
                message = "加载 ${entry.model} 的码表…",
            )
        }
        val signals: List<CodeLibrary.LibrarySignal>
        val trial: CodeLibrary.LibrarySignal?
        if (entry.kind == "smartir_climate") {
            val table = acCache[entry.id] ?: try {
                library.loadAcTable(entry)?.also { acCache[entry.id] = it }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = "码表加载失败：${e.message}") }
                return false
            } ?: run {
                _state.update { it.copy(busy = false, message = "${entry.model} 码表格式不支持") }
                return false
            }
            signals = emptyList()
            trial = acTrialSignal(table)
        } else {
            signals = signalCache[entry.id] ?: try {
                library.trialOrder(library.loadFlipperSignals(entry)).also { signalCache[entry.id] = it }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = "码表加载失败：${e.message}") }
                return false
            }
            trial = signals.firstOrNull { isPower(it.name) } ?: signals.firstOrNull()
        }
        if (trial == null) {
            _state.update { it.copy(busy = false, message = "${entry.model} 没有可用信号") }
            return false
        }
        _state.update {
            it.copy(
                signals = signals,
                trialSignal = trial,
                busy = false,
                message = "型号 ${index + 1}/${st.trialModels.size} · ${entry.model} · 发送「${trial.name}」",
            )
        }
        if (send) sendCurrent()
        return true
    }

    /** 空调全状态码表取一个"开机 + 制冷/制热 + 中间温度 + 自动风速"的代表帧。 */
    private fun acTrialSignal(table: SmartIrParser.AcCodeTable): CodeLibrary.LibrarySignal? {
        val mode = listOf("cool", "heat", "auto", "fan_only", "dry")
            .firstOrNull { table.commands.containsKey(it) }
            ?: table.commands.keys.firstOrNull { it != SmartIrParser.MODE_OFF }
            ?: return null
        val temp = ((table.minTemperature + table.maxTemperature) / 2)
            .coerceIn(table.minTemperature, table.maxTemperature)
        return acSignal(table, mode, temp)
    }

    /** 指定模式/温度构造空调帧，风速按 auto → low → 首个可用 → 任意 的顺序兜底。 */
    private fun acSignal(
        table: SmartIrParser.AcCodeTable,
        mode: String,
        temp: Int,
    ): CodeLibrary.LibrarySignal? {
        val fan = listOf("auto", "low").firstOrNull { table.fanModes.contains(it) }
            ?: table.fanModes.firstOrNull()
            ?: SmartIrParser.FAN_ANY
        val code = SmartIrParser.lookup(table, mode, fan, temp)
            ?: SmartIrParser.lookup(table, mode, SmartIrParser.FAN_ANY, temp)
            ?: table.commands[mode]?.values?.firstOrNull { it.containsKey(temp) }?.get(temp)
            ?: return null
        val signal = BroadlinkCode.toSignal(code) ?: return null
        return CodeLibrary.LibrarySignal("${modeLabel(mode)} $temp℃", signal)
    }

    private fun modeLabel(mode: String): String = when (mode) {
        "cool" -> "制冷"
        "heat" -> "制热"
        "auto" -> "自动"
        "fan_only" -> "送风"
        "dry" -> "除湿"
        else -> mode
    }

    fun sendCurrent() {
        val signal = _state.value.trialSignal ?: return
        transmit(signal)
    }

    fun sendVerify(label: String) {
        val signal = _state.value.verifySignals.firstOrNull { it.name == label } ?: return
        transmit(signal)
    }

    private fun transmit(signal: CodeLibrary.LibrarySignal) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = "发送 ${signal.name}") }
            val result = transmitter.transmit(signal.signal)
            val text = when (result) {
                is IrTransmitter.TransmitResult.Sent ->
                    "已发出 ${signal.name} · ${result.carrierHz / 1000}kHz · " +
                        "${result.frames} 帧 · ${result.durationUs / 1000}ms"
                IrTransmitter.TransmitResult.NoHardware -> "这台手机没有红外发射器"
                is IrTransmitter.TransmitResult.PatternTooLong ->
                    "红外码超长（${result.durationUs / 1000}ms），系统拒绝发送"
                is IrTransmitter.TransmitResult.Invalid -> "发送失败：${result.reason}"
            }
            _state.update { it.copy(busy = false, message = text) }
        }
    }

    fun moveTrial(delta: Int) {
        val st = _state.value
        if (st.trialModels.isEmpty()) return
        trialJob?.cancel()
        val next = (st.trialModelIndex + delta).mod(st.trialModels.size)
        _state.update {
            it.copy(
                autoTrial = false,
                step = AddStep.TRIAL,
                foundSignal = null,
                verifySignals = emptyList(),
            )
        }
        viewModelScope.launch { prepareModel(next) }
    }

    fun setAutoTrial(enabled: Boolean) {
        trialJob?.cancel()
        if (!enabled) {
            _state.update { it.copy(autoTrial = false) }
            return
        }
        val models = _state.value.trialModels
        if (models.isEmpty()) return
        _state.update { it.copy(autoTrial = true) }
        trialJob = viewModelScope.launch {
            var index = _state.value.trialModelIndex
            while (isActive) {
                val sent = prepareModel(index, send = true)
                delay(if (sent) 1800 else 600)
                index = (index + 1) % models.size
            }
        }
    }

    /**
     * 主开关被确认。像小米万能遥控一样，先确认开关，再抽验几个其它按键；
     * 全部符合才命名保存，不符合就回到轮播换下一个型号。
     */
    fun markFound() {
        val st = _state.value
        val found = st.trialSignal ?: return
        trialJob?.cancel()
        val verify = buildVerifySignals(st)
        if (verify.isEmpty()) {
            _state.update {
                it.copy(
                    autoTrial = false,
                    foundSignal = found,
                    step = AddStep.NAME,
                    name = it.entry?.model ?: "",
                    message = "已选 ${it.entry?.model} 的主开关「${found.name}」",
                )
            }
        } else {
            _state.update {
                it.copy(
                    autoTrial = false,
                    foundSignal = found,
                    step = AddStep.VERIFY,
                    verifySignals = verify,
                    message = "主开关已确认，再试几个按键看看是否都正常",
                )
            }
        }
    }

    private fun buildVerifySignals(st: AddUiState): List<CodeLibrary.LibrarySignal> {
        val entry = st.entry ?: return emptyList()
        if (entry.kind == "smartir_climate") {
            val table = acCache[entry.id] ?: return emptyList()
            val out = ArrayList<CodeLibrary.LibrarySignal>()
            acSignal(table, "cool", 26.coerceIn(table.minTemperature, table.maxTemperature))
                ?.let { out += it }
            if (table.commands.containsKey("heat")) {
                acSignal(table, "heat", 20.coerceIn(table.minTemperature, table.maxTemperature))
                    ?.let { out += it }
            }
            SmartIrParser.codeForOff(table)?.let { code ->
                BroadlinkCode.toSignal(code)?.let {
                    out += CodeLibrary.LibrarySignal("关机", it)
                }
            }
            return out
        }
        val keywords = listOf("vol", "ch", "mute", "up", "down", "left", "right", "ok", "静音", "音量", "频道")
        return st.signals
            .asSequence()
            .filter { s -> keywords.any { s.name.lowercase().contains(it) } }
            .filterNot { isPower(it.name) }
            .distinctBy { it.name }
            .take(5)
            .toList()
    }

    fun markVerifyPassed() {
        val st = _state.value
        _state.update {
            it.copy(
                step = AddStep.NAME,
                name = it.entry?.model ?: "",
                message = "已选 ${it.entry?.model}，按键抽验通过",
                verifySignals = emptyList(),
            )
        }
    }

    /** 顶部返回键与系统返回键共用：逐层退出向导，而不是直接回首页。 */
    fun goBackStep() {
        trialJob?.cancel()
        val st = _state.value
        when (st.step) {
            AddStep.TYPE -> Unit
            AddStep.BRAND -> _state.update {
                it.copy(step = AddStep.TYPE, type = null, brands = emptyList())
            }
            AddStep.MODEL -> _state.update {
                it.copy(step = AddStep.BRAND, brand = null, models = emptyList())
            }
            AddStep.TRIAL -> _state.update {
                it.copy(
                    step = AddStep.MODEL,
                    entry = null,
                    trialModels = emptyList(),
                    signals = emptyList(),
                    trialSignal = null,
                    autoTrial = false,
                )
            }
            AddStep.VERIFY -> {
                _state.update { it.copy(step = AddStep.TRIAL, verifySignals = emptyList(), foundSignal = null) }
                viewModelScope.launch { prepareModel(_state.value.trialModelIndex) }
            }
            AddStep.NAME -> {
                _state.update { it.copy(step = AddStep.TRIAL, foundSignal = null, autoTrial = false) }
                viewModelScope.launch { prepareModel(_state.value.trialModelIndex) }
            }
        }
    }

    private fun isPower(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("power") || n.contains("开关") || n == "on" || n == "off"
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setRoom(value: String) = _state.update { it.copy(room = value) }

    fun save(onDone: (Long) -> Unit) {
        val st = _state.value
        val entry = st.entry ?: return
        val name = st.name.trim().ifEmpty { entry.model }
        viewModelScope.launch {
            val boundName = if (entry.kind == "smartir_climate") {
                null // 空调按状态发码，没有"主开关信号"的概念
            } else {
                st.foundSignal?.name
                    ?: st.signals.firstOrNull { s ->
                        val n = s.name.lowercase()
                        n.contains("power") || n.contains("开")
                    }?.name
            }
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

    // 向导步骤都在同一个 destination 内，系统返回键需要逐层退，而不是直接回首页。
    BackHandler(enabled = state.step != AddStep.TYPE) { vm.goBackStep() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stepTitle(state.step)) },
                navigationIcon = {
                    IconButton(onClick = { if (state.step == AddStep.TYPE) onBack() else vm.goBackStep() }) {
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
                AddStep.VERIFY -> VerifyStep(state, vm)
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
    AddStep.VERIFY -> "添加设备 · 验证按键"
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
    Column(Modifier.fillMaxSize()) {
        Text(
            "选择要控制的设备类型",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            gridItems(DeviceType.entries.filter { it != DeviceType.GENERIC }, key = { it.name }) { type ->
                Card(
                    onClick = { vm.selectType(type) },
                    modifier = Modifier.fillMaxWidth().height(108.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = typeIconOf(type),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            type.label,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun typeIconOf(type: DeviceType) = when (type) {
    DeviceType.TV -> Icons.Rounded.Tv
    DeviceType.BOX -> Icons.Rounded.Devices
    DeviceType.LIGHT -> Icons.Rounded.Lightbulb
    DeviceType.FAN -> Icons.Rounded.Air
    DeviceType.AC -> Icons.Rounded.AcUnit
    DeviceType.PROJECTOR -> Icons.Rounded.Videocam
    DeviceType.AUDIO -> Icons.Rounded.Speaker
    DeviceType.GENERIC -> Icons.Rounded.Devices
}

@Composable
private fun BrandStep(
    state: AddUiState,
    search: String,
    onSearch: (String) -> Unit,
    vm: AddDeviceViewModel,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            label = { Text("搜索品牌") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val brands = if (search.isBlank()) state.brands
        else state.brands.filter { it.contains(search, ignoreCase = true) }
        Text(
            "${brands.size} 个品牌",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).padding(top = 12.dp),
        ) {
            gridItems(brands, key = { it }) { brand ->
                Card(onClick = { vm.selectBrand(brand) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            brand,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStep(state: AddUiState, vm: AddDeviceViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "${state.brand ?: ""} · ${state.models.size} 个型号",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "点任意型号会立即发送一次开关信号，方便边选边试。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.models, key = { it.id }) { entry ->
                Card(onClick = { vm.selectEntry(entry) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
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
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrialStep(state: AddUiState, vm: AddDeviceViewModel) {
    val modelCount = state.trialModels.size.coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("把手机对准设备", style = MaterialTheme.typography.titleMedium)
        Text(
            "按下面的电源键试一下；没反应就换下一个型号，直到设备有反应。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        IconButton(
            onClick = vm::sendCurrent,
            enabled = !state.busy && state.trialSignal != null,
            modifier = Modifier.size(136.dp).clip(CircleShape),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = "发送电源",
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        Text(
            state.entry?.model ?: "-",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "型号 ${state.trialModelIndex + 1} / $modelCount" +
                (state.trialSignal?.let { " · 发送「${it.name}」" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (state.trialModelIndex + 1).toFloat() / modelCount },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { vm.moveTrial(1) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Text("没反应，下一个")
            }
            Button(
                onClick = vm::markFound,
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = state.trialSignal != null,
            ) {
                Text("有反应了")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "自动轮播各型号（每 1.8 秒一个）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(checked = state.autoTrial, onCheckedChange = { vm.setAutoTrial(it) })
        }
    }
}

@Composable
private fun VerifyStep(state: AddUiState, vm: AddDeviceViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "主开关能用了，再确认其它按键",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (state.entry?.kind == "smartir_climate") {
                "依次试一下温度/模式/关机。如果对不上，说明这个码表不适配，换下一个型号继续。"
            } else {
                "点下面的按键试一下。如果有关键按键不响应，说明这个型号只是开关碰巧一致，换下一个型号继续。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "当前型号：${state.entry?.model ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.verifySignals.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { signal ->
                    OutlinedButton(
                        onClick = { vm.sendVerify(signal.name) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Text(signal.name)
                    }
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { vm.moveTrial(1) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Text("有按键不对，换型号")
            }
            Button(
                onClick = vm::markVerifyPassed,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Text("都正常")
            }
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
