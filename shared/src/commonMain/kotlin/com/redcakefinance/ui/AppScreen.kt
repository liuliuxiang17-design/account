package com.redcakefinance.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.redcakefinance.core.analysis.LedgerSummary
import com.redcakefinance.core.analysis.Summary
import com.redcakefinance.core.format.Fmt
import com.redcakefinance.core.model.Direction
import com.redcakefinance.core.model.Transaction
import com.redcakefinance.core.storage.ImportResult
import com.redcakefinance.core.storage.LedgerRepository
import com.redcakefinance.platform.Clipboard
import com.redcakefinance.platform.PlatformClock
import com.redcakefinance.ui.theme.AccentGold
import com.redcakefinance.ui.theme.AccentRed
import com.redcakefinance.ui.theme.AccentRedLight
import com.redcakefinance.ui.theme.DarkBg
import com.redcakefinance.ui.theme.DarkSurface
import com.redcakefinance.ui.theme.DarkSurfaceHigh
import com.redcakefinance.ui.theme.DarkSurfaceHighest
import com.redcakefinance.ui.theme.ErrorRed
import com.redcakefinance.ui.theme.OnDarkFaint
import com.redcakefinance.ui.theme.OnDarkPrimary
import com.redcakefinance.ui.theme.OnDarkSub
import kotlinx.coroutines.delay

/** 顶部提示条：成功为金色、失败为红色，几秒后自动消失。 */
private data class Banner(val text: String, val isError: Boolean)

/**
 * 主界面：概览 + 两个分页（流水 / 图表）+ 底部切换条。
 *
 * iOS 无法读取系统短信库，所以导入入口是「复制一条银行短信 → 点按钮」；
 * 解析、去重、记账、余额快照全部在 [LedgerRepository] 里完成。
 */
@Composable
fun AppScreen() {
    val repo = remember { LedgerRepository() }
    var ledger by remember { mutableStateOf(repo.load()) }
    var banner by remember { mutableStateOf<Banner?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }

    val sorted = remember(ledger) { ledger.transactions.sortedByDescending { it.occurredAt } }
    val summary = remember(ledger) { LedgerSummary.of(ledger, PlatformClock.nowMillis()) }

    LaunchedEffect(banner) {
        if (banner != null) {
            delay(4200)
            banner = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DarkBg)
            .safeDrawingPadding(),
    ) {
        Header(
            count = ledger.transactions.size,
            onSettings = { showSettings = true },
        )
        OverviewCard(summary)

        if (tab == 0) {
            ImportPanel(
                repo = repo,
                onResult = { result ->
                    when (result) {
                        is ImportResult.Added -> {
                            ledger = repo.load()
                            val sign = if (result.transaction.direction == Direction.EXPENSE) "-" else "+"
                            banner = Banner(
                                "已记账 $sign${Fmt.fenToYuan(result.transaction.amountFen)} · ${result.transaction.merchant}",
                                false,
                            )
                        }
                        ImportResult.Duplicate -> banner = Banner("这条短信已经记过了", true)
                        is ImportResult.Failed -> banner = Banner(result.reason, true)
                    }
                },
                onBanner = { text -> banner = Banner(text, true) },
                modifier = Modifier.weight(1f),
            ) {
                banner?.let { b -> BannerBar(b) }
                Spacer(Modifier.height(10.dp))
                if (sorted.isEmpty()) {
                    EmptyState(Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sorted, key = { it.id }) { txn ->
                            TransactionRow(txn, onLongPress = { pendingDelete = txn })
                        }
                    }
                }
            }
        } else {
            ChartsScreen(ledger, Modifier.weight(1f))
        }

        BottomDock(tab) { tab = it }
    }

    if (showSettings) {
        SettingsDialog(
            ledger = ledger,
            repo = repo,
            onLedgerChanged = { ledger = it },
            onBanner = { text, isError -> banner = Banner(text, isError) },
            onDismiss = { showSettings = false },
        )
    }

    pendingDelete?.let { txn ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔流水？") },
            text = { Text("${txn.merchant} · ${Fmt.signedByDirection(txn.direction, txn.amountFen)}\n\n删除后不影响系统短信，但无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    ledger = repo.deleteTransaction(txn.id)
                    pendingDelete = null
                    banner = Banner("已删除", false)
                }) { Text("删除", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 导入区：读取剪贴板按钮 + 提示条 + 列表（内容由调用方传入）。 */
@Composable
private fun ImportPanel(
    repo: LedgerRepository,
    onResult: (ImportResult) -> Unit,
    onBanner: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Button(
            onClick = {
                val text = Clipboard.readText()
                if (text == null) onBanner("剪贴板是空的，先去短信里复制一条银行短信")
                else onResult(repo.importFromText(text, PlatformClock.nowMillis()))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentRed,
                contentColor = OnDarkPrimary,
            ),
        ) {
            Text("读取剪贴板并记账", style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

/** 提示条。 */
@Composable
private fun BannerBar(b: Banner) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (b.isError) DarkSurfaceHigh else DarkSurface,
    ) {
        Text(
            b.text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (b.isError) ErrorRed else AccentGold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 底部两页切换条。 */
@Composable
private fun BottomDock(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp)
            .background(DarkSurface, RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockItem("流水", selected == 0, Modifier.weight(1f)) { onSelect(0) }
        DockItem("图表", selected == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun DockItem(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .background(if (selected) DarkSurfaceHighest else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AccentRedLight else OnDarkSub,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 标题栏：应用名 + 已记条数 + 设置入口。 */
@Composable
private fun Header(count: Int, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("红糕镇财务", style = MaterialTheme.typography.headlineSmall, color = OnDarkPrimary)
            Text(
                if (count == 0) "本地离线 · 数据只在这台手机里" else "共 $count 笔 · 长按可删除",
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkFaint,
            )
        }
        TextButton(onClick = onSettings) {
            Text("设置", style = MaterialTheme.typography.labelLarge, color = OnDarkSub)
        }
    }
}

/** 概览卡：本月收入 / 本月支出 / 结余。 */
@Composable
private fun OverviewCard(summary: Summary) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Metric("本月收入", "+" + Fmt.fenToYuan(summary.incomeFen), AccentGold)
            VerticalDivider()
            Metric("本月支出", "-" + Fmt.fenToYuan(summary.expenseFen), AccentRed)
            VerticalDivider()
            Metric("结余", Fmt.fenToYuan(summary.balanceFen), OnDarkPrimary)
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(Modifier.width(1.dp).height(30.dp).background(DarkSurfaceHigh))
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnDarkSub)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** 单条流水：商户 + 来源/时间 + 金额；长按删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(txn: Transaction, onLongPress: () -> Unit) {
    val isExpense = txn.direction == Direction.EXPENSE
    val amountColor = if (isExpense) AccentRed else AccentGold
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {},
            onLongClick = onLongPress,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    txn.merchant,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnDarkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    (txn.sender ?: "未知来源") + " · " + Fmt.timeShort(txn.occurredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkFaint,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                Fmt.signedByDirection(txn.direction, txn.amountFen),
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 空状态：告诉用户下一步该做什么。 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有记录", style = MaterialTheme.typography.titleMedium, color = OnDarkPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "在短信里长按一条银行扣款通知 → 拷贝\n" +
                    "回到这里点「读取剪贴板并记账」",
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkSub,
            )
        }
    }
}
