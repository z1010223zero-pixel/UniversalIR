package com.zero.universalir.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
                title = {
                    Column {
                        Text("万能红外遥控", style = MaterialTheme.typography.titleLarge)
                        if (devices.isNotEmpty()) {
                            Text(
                                "${devices.size} 台设备",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
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
            EmptyState(Modifier.padding(padding), onAddDevice)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
private fun EmptyState(modifier: Modifier = Modifier, onAddDevice: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("还没有设备", style = MaterialTheme.typography.titleMedium)
            Text(
                "添加电视、空调、灯等红外设备后，这里会出现遥控卡片",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            TextButton(onClick = onAddDevice) { Text("添加第一台设备") }
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
    var menuOpen by remember { mutableStateOf(false) }
    val isAc = DeviceType.from(device.type) == DeviceType.AC

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = typeIcon(device.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Row {
                    IconButton(onClick = onPower, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "开关",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("语音/自动化接入") },
                                leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onVoice()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除设备") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                device.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitleOf(device),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (isAc) {
                Text(
                    buildString {
                        append(if (device.acPower) "已开机" else "已关机")
                        device.acTemp?.let { append(" · ").append(it).append("℃") }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (device.acPower) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private fun subtitleOf(device: RemoteDeviceEntity): String = buildString {
    if (device.room.isNotBlank()) append(device.room).append(" · ")
    if (device.brand.isNotBlank()) append(device.brand)
    if (device.model.isNotBlank()) append(" ").append(device.model)
}.ifBlank { "未设置位置" }

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
