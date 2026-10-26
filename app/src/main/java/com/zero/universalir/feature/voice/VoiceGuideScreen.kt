package com.zero.universalir.feature.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zero.universalir.data.db.RemoteDao
import com.zero.universalir.data.db.RemoteDeviceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class VoiceGuideViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    dao: RemoteDao,
) : ViewModel() {
    private val deviceId: Long = savedStateHandle["id"] ?: 0L

    val device = dao.deviceFlow(deviceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceGuideScreen(
    deviceId: Long,
    onBack: () -> Unit,
    vm: VoiceGuideViewModel = hiltViewModel(),
) {
    val device by vm.device.collectAsState()
    val clipboard = LocalClipboardManager.current
    val d = device

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音接入 · ${d?.name ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (d == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "把下面的链接配置到语音助手/自动化工具里，即可用语音触发红外发射。链接用设备 id，改名后依然有效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- 小爱 ----------
            item {
                GuideCard(
                    title = "小米 · 小爱同学",
                    steps = listOf(
                        "① 打开小爱同学 App → 技能中心 → 小爱训练",
                        "② 创建训练：说法填「${d.room}${d.name}打开」",
                        "③ 执行对象选「打开网址」，粘贴下方链接",
                        "④ 一句训练对应一个动作；换动作就再建一条",
                        "⑤ 也可对超级小爱说「打开万能红外遥控」，用专家模式自动点击面板",
                    ),
                )
                val links = buildList {
                    if (d.type == "AC") {
                        add("开机" to acUri(d, "power=on"))
                        add("关机" to acUri(d, "power=off"))
                        add("制冷 26 度" to acUri(d, "temp=26&mode=cool"))
                        add("制热 26 度" to acUri(d, "temp=26&mode=heat"))
                        add("自动 24 度" to acUri(d, "temp=24&mode=auto"))
                    } else {
                        add("打开 ${d.name}" to deviceUri(d, "on"))
                        add("关闭 ${d.name}" to deviceUri(d, "off"))
                        add("开关切换" to deviceUri(d, "toggle"))
                    }
                }
                links.forEach { (label, uri) -> CopyRow(label, uri) { clipboard.setText(AnnotatedString(uri)) } }
            }

            // ---------- 小艺 ----------
            item {
                GuideCard(
                    title = "华为/荣耀 · 小艺",
                    steps = listOf(
                        "① 零配置：对小艺说「打开万能红外遥控」，进入面板点击操作",
                        "② HarmonyOS 6：小艺 App → 小艺学技能，把「打开面板→点开关」教给小艺存成语音技能",
                        "③ 长按桌面图标可直达本设备的开机动作（已自动生成）",
                        "④ 未来上架应用市场后可接入 Intents Kit 实现原生语义直达",
                    ),
                )
                val uri = if (d.type == "AC") acUri(d, "power=on") else deviceUri(d, "on")
                CopyRow("设备直达链接", uri) { clipboard.setText(AnnotatedString(uri)) }
            }

            // ---------- Tasker ----------
            item {
                GuideCard(
                    title = "Tasker / MacroDroid",
                    steps = listOf(
                        "发送广播 action = com.zero.universalir.SEND",
                        "extras：device=设备名或id；电视/灯加 key=按键名",
                        "空调：power=on/off、mode=cool/heat/dry/fan_only/auto、fan=low/mid/high/auto、temp=数字",
                    ),
                )
                val example = if (d.type == "AC") {
                    "am broadcast -a com.zero.universalir.SEND --es device \"${d.name}\" --es temp 26 --es mode cool"
                } else {
                    "am broadcast -a com.zero.universalir.SEND --es device \"${d.name}\" --es key 开机"
                }
                CopyRow("示例命令", example) { clipboard.setText(AnnotatedString(example)) }
            }

            // ---------- 桌面快捷方式 ----------
            item {
                GuideCard(
                    title = "桌面快捷方式",
                    steps = listOf(
                        "已自动为最近 4 台设备生成「开机」快捷方式",
                        "长按桌面图标 → 长按快捷方式 → 可拖到桌面单独放",
                        "也可被小艺建议、搜索、第三方桌面调用",
                    ),
                )
            }
        }
    }
}

private fun deviceUri(d: RemoteDeviceEntity, action: String) =
    "uniir://device/${d.id}/$action"

private fun acUri(d: RemoteDeviceEntity, query: String) =
    "uniir://ac/${d.id}?$query"

@Composable
private fun GuideCard(title: String, steps: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CopyRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "复制 $label")
        }
    }
}
