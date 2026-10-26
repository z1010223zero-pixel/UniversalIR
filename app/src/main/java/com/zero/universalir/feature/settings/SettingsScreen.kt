package com.zero.universalir.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zero.universalir.core.ir.IrLogic
import com.zero.universalir.core.ir.IrTransmitter
import com.zero.universalir.data.codes.CodeLibrary
import com.zero.universalir.core.model.DeviceType
import com.zero.universalir.data.db.RemoteDao
import com.zero.universalir.voice.FeedbackBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val version: Int = 1,
    val devices: List<BackupDevice> = emptyList(),
)

@Serializable
data class BackupDevice(
    val name: String,
    val room: String = "",
    val type: String,
    val brand: String = "",
    val model: String = "",
    val libraryFileId: String? = null,
    val boundSignalName: String? = null,
    val acPower: Boolean = false,
    val acMode: String? = null,
    val acFan: String? = null,
    val acTemp: Int? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val transmitter: IrTransmitter,
    private val library: CodeLibrary,
    private val dao: RemoteDao,
    private val feedback: FeedbackBus,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    data class UiState(
        val hasEmitter: Boolean = false,
        val ranges: List<IrLogic.CarrierRange> = emptyList(),
        val entryCount: Int = 0,
        val countByType: Map<String, Int> = emptyMap(),
        val sources: List<CodeLibrary.SourceInfo> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        val cap = transmitter.capability()
        val idx = library.index
        _state.value = UiState(
            hasEmitter = cap.hasEmitter,
            ranges = cap.ranges,
            entryCount = idx.entries.size,
            countByType = idx.entries.groupingBy { it.deviceType }.eachCount(),
            sources = idx.sources,
        )
    }

    suspend fun buildBackup(): String {
        val devices = dao.allDevices().map {
            BackupDevice(
                name = it.name,
                room = it.room,
                type = it.type,
                brand = it.brand,
                model = it.model,
                libraryFileId = it.libraryFileId,
                boundSignalName = it.boundSignalName,
                acPower = it.acPower,
                acMode = it.acMode,
                acFan = it.acFan,
                acTemp = it.acTemp,
            )
        }
        return Json.encodeToString(BackupData.serializer(), BackupData(devices = devices))
    }

    fun importJson(text: String) {
        viewModelScope.launch {
            try {
                val backup = Json.decodeFromString(BackupData.serializer(), text)
                var count = 0
                backup.devices.forEach { b ->
                    dao.insertDevice(
                        com.zero.universalir.data.db.RemoteDeviceEntity(
                            name = b.name,
                            room = b.room,
                            type = b.type,
                            brand = b.brand,
                            model = b.model,
                            libraryFileId = b.libraryFileId,
                            boundSignalName = b.boundSignalName,
                            acPower = b.acPower,
                            acMode = b.acMode,
                            acFan = b.acFan,
                            acTemp = b.acTemp,
                        )
                    )
                    count++
                }
                feedback.post("导入完成：$count 台设备")
            } catch (e: Exception) {
                feedback.post("导入失败：${e.message}")
            }
        }
    }

    fun writeBackupTo(uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(buildBackup().toByteArray(Charsets.UTF_8))
                }
                feedback.post("备份已保存")
                onDone(true)
            } catch (e: Exception) {
                feedback.post("备份失败：${e.message}")
                onDone(false)
            }
        }
    }

    fun readBackupFrom(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }
                if (text != null) importJson(text)
            } catch (e: Exception) {
                feedback.post("读取备份失败：${e.message}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.writeBackupTo(it) {} } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.readBackupFrom(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("红外硬件", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.hasEmitter) "检测到红外发射器" else "未检测到红外发射器（部分机型误报，发射仍可尝试）",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (state.ranges.isNotEmpty()) {
                        Text(
                            "支持载频：" + state.ranges.joinToString { "${it.minHz}-${it.maxHz}Hz" },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("内置码库（${state.entryCount} 个码表）", style = MaterialTheme.typography.titleMedium)
                    val byType = state.countByType
                    val summary = DeviceType.entries
                        .filter { (byType[it.name.lowercase()] ?: 0) > 0 }
                        .joinToString("、") { "${it.label} ${byType[it.name.lowercase()]}" }
                    if (summary.isNotEmpty()) {
                        Text(summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    state.sources.forEach {
                        Text(
                            "${it.name}（${it.license}）${it.url}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("配置备份", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("universalir-backup.json") }) {
                            Text("导出")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                            Text("导入")
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("关于", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "万能红外遥控 v0.1.0 · 本机红外发射走 ConsumerIrManager 公开 API。" +
                            "小米机型红外口仅可发射；学习功能规划经外置学习硬件补齐。" +
                            "码库来源：Flipper-IRDB（CC0-1.0）、SmartIR（MIT）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
