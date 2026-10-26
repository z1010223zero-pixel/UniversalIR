package com.zero.universalir.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.zero.universalir.MainActivity
import com.zero.universalir.R
import com.zero.universalir.data.db.RemoteDeviceEntity

/** 每个设备生成"开机"动态快捷方式（长按图标直达，语音助手/搜索也可命中）。 */
object ShortcutsHelper {
    private const val MAX_SHORTCUTS = 4

    fun update(context: Context, devices: List<RemoteDeviceEntity>) {
        val shortcuts = devices.take(MAX_SHORTCUTS).map { device ->
            val uri = if (device.type == "ac") {
                "uniir://ac/${device.id}?power=on"
            } else {
                "uniir://device/${device.id}/on"
            }
            ShortcutInfoCompat.Builder(context, "device_${device.id}")
                .setShortLabel(device.name)
                .setLongLabel(device.name)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_remote))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
                )
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}
