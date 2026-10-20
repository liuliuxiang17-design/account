package com.redcakefinance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redcakefinance.core.storage.Ledger
import com.redcakefinance.core.storage.LedgerRepository
import com.redcakefinance.platform.Clipboard
import com.redcakefinance.ui.theme.DarkSurfaceHigh
import com.redcakefinance.ui.theme.ErrorRed
import com.redcakefinance.ui.theme.OnDarkFaint
import com.redcakefinance.ui.theme.OnDarkSub
import com.redcakefinance.ui.theme.AccentGold

/**
 * 设置与备份面板。
 *
 * 备份走剪贴板而不是文件：iOS 上没有文件选择器时，剪贴板是全平台一致、
 * 且不需要任何额外权限的通道——导出后粘贴到备忘录或微信文件传输助手即可长期保存。
 *
 * @param onLedgerChanged 恢复或清空后回调最新账本，供上层刷新界面
 * @param onBanner 顶部提示（文案, 是否错误）
 */
@Composable
fun SettingsDialog(
    ledger: Ledger,
    repo: LedgerRepository,
    onLedgerChanged: (Ledger) -> Unit,
    onBanner: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置与备份") },
        text = {
            Column {
                Text(
                    "共 ${ledger.transactions.size} 笔流水 · ${ledger.checkpoints.size} 条余额快照",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "免费签名每 7 天要重装一次，重装前请先导出备份；卸载会清空本机数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkFaint,
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        val ok = Clipboard.writeText(repo.exportText())
                        onBanner(
                            if (ok) "备份已复制，请粘贴到备忘录或文件传输助手保存" else "复制失败，请重试",
                            !ok,
                        )
                        if (ok) onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("复制备份到剪贴板") }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmRestore = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("从剪贴板恢复备份") }

                Spacer(Modifier.height(16.dp))
                Text("危险操作", style = MaterialTheme.typography.labelMedium, color = OnDarkSub)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { confirmWipe = true }) {
                    Text("清空全部流水与快照", color = ErrorRed)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("用剪贴板内容覆盖当前账本？") },
            text = {
                Text(
                    "会把当前 ${ledger.transactions.size} 笔流水整体替换成剪贴板里的备份，" +
                        "无法撤销。剪贴板内容不是本应用的备份时不会改动任何数据。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    val text = Clipboard.readText()
                    val restored = text?.let { repo.restoreFromText(it) }
                    if (restored == null) {
                        onBanner("剪贴板里没有可用的备份", true)
                    } else {
                        onLedgerChanged(restored)
                        onBanner("已恢复 ${restored.transactions.size} 笔流水", false)
                        onDismiss()
                    }
                }) { Text("覆盖恢复", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("取消") } },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("清空全部数据？") },
            text = { Text("将删除 ${ledger.transactions.size} 笔流水与 ${ledger.checkpoints.size} 条余额快照，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    onLedgerChanged(repo.clearAll())
                    onBanner("已清空", false)
                    onDismiss()
                }) { Text("清空", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("取消") } },
        )
    }
}
