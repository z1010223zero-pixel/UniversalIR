package com.zero.universalir.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zero.universalir.core.model.DeviceType
import com.zero.universalir.data.db.RemoteDao
import com.zero.universalir.data.db.RemoteDeviceEntity
import com.zero.universalir.voice.FeedbackBus
import com.zero.universalir.voice.ShortcutsHelper
import com.zero.universalir.voice.VoiceExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dao: RemoteDao,
    private val executor: VoiceExecutor,
    private val feedback: FeedbackBus,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    val devices = dao.devices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 首页卡片上的快速开关。 */
    fun quickPower(device: RemoteDeviceEntity) {
        viewModelScope.launch {
            if (device.type == DeviceType.AC.name) {
                executor.sendAc(device, power = !device.acPower, mode = null, fan = null, temp = null)
            } else {
                executor.sendMainKey(device, preferOn = null)
            }
        }
    }

    fun delete(device: RemoteDeviceEntity) {
        viewModelScope.launch {
            dao.deleteButtonsOf(device.id)
            dao.deleteDevice(device)
            feedback.post("已删除 ${device.name}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddDevice: () -> Unit,
    onOpenDevice: (Long) -> Unit,
    onVoiceGuide: (Long) -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val devices by vm.devices.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(devices) {
        ShortcutsHelper.update(context, devices)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("万能红外遥控") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddDevice,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加设备") },
            )
        },
    ) { padding ->
        if (devices.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        onOpen = { onOpenDevice(device.id) },
                        onPower = { vm.quickPower(device) },
                        onVoice = { onVoiceGuide(device.id) },
                        onDelete = { vm.delete(device) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有设备", style = MaterialTheme.typography.titleMedium)
            Text(
                "点右下角添加电视、灯、空调等红外设备",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: RemoteDeviceEntity,
    onOpen: () -> Unit,
    onPower: () -> Unit,
    onVoice: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = typeIcon(device.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                val subtitle = buildString {
                    if (device.room.isNotBlank()) append(device.room).append(" · ")
                    if (device.brand.isNotBlank()) append(device.brand)
                    if (device.model.isNotBlank()) append(" ").append(device.model)
                    if (device.type == DeviceType.AC.name) {
                        append(" · ").append(if (device.acPower) "开机" else "关机")
                        device.acTemp?.let { append(" ").append(it).append("℃") }
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPower) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = "开关")
            }
            IconButton(onClick = onVoice) {
                Icon(Icons.Default.GraphicEq, contentDescription = "语音接入")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "删除")
            }
        }
    }
}

fun typeIcon(type: String) = when (DeviceType.from(type)) {
    DeviceType.TV -> Icons.Rounded.Tv
    DeviceType.BOX -> Icons.Rounded.Devices
    DeviceType.LIGHT -> Icons.Rounded.Lightbulb
    DeviceType.FAN -> Icons.Rounded.Air
    DeviceType.AC -> Icons.Rounded.AcUnit
    DeviceType.PROJECTOR -> Icons.Rounded.Videocam
    DeviceType.AUDIO -> Icons.Rounded.Speaker
    DeviceType.GENERIC -> Icons.Rounded.Devices
}
