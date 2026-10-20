package com.redcakefinance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.redcakefinance.ui.theme.DarkBg
import com.redcakefinance.ui.theme.DarkSurface
import com.redcakefinance.ui.theme.DarkSurfaceHigh
import com.redcakefinance.ui.theme.ErrorRed
import com.redcakefinance.ui.theme.OnDarkFaint
import com.redcakefinance.ui.theme.OnDarkPrimary
import com.redcakefinance.ui.theme.OnDarkSub
import kotlinx.coroutines.delay

/** 顶部提示条：成功为金色、失败为红色，几秒后自动消失。 */
private data class Banner(val text: String, val isError: Boolean)

/**
 * 主界面：概览 + 「读取剪贴板」入口 + 流水列表。
 *
 * iOS 无法读取系统短信库，所以入口是「复制一条银行短信 → 点按钮」；
 * 解析、去重、记账、余额快照全部在 [LedgerRepository] 里完成。
 */
@Composable
fun AppScreen() {
    val repo = remember { LedgerRepository() }
    var ledger by remember { mutableStateOf(repo.load()) }
    var banner by remember { mutableStateOf<Banner?>(null) }

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
        Header(count = ledger.transactions.size)
        OverviewCard(summary)

        Button(
            onClick = {
                val text = Clipboard.readText()
                if (text == null) {
                    banner = Banner("剪贴板是空的，先去短信里复制一条银行短信", true)
                } else {
                    banner = when (val result = repo.importFromText(text, PlatformClock.nowMillis())) {
                        is ImportResult.Added -> {
                            ledger = repo.load()
                            val sign = if (result.transaction.direction == Direction.EXPENSE) "-" else "+"
                            Banner("已记账 $sign${Fmt.fenToYuan(result.transaction.amountFen)} · ${result.transaction.merchant}", false)
                        }
                        ImportResult.Duplicate -> Banner("这条短信已经记过了", true)
                        is ImportResult.Failed -> Banner(result.reason, true)
                    }
                }
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

        banner?.let { b ->
            Spacer(Modifier.height(8.dp))
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
                items(sorted, key = { it.id }) { txn -> TransactionRow(txn) }
            }
        }
    }
}

/** 标题栏：应用名 + 已记条数。 */
@Composable
private fun Header(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text("红糕镇财务", style = MaterialTheme.typography.headlineSmall, color = OnDarkPrimary)
            Text(
                if (count == 0) "本地离线 · 数据只在这台手机里" else "共 $count 笔 · 数据只在这台手机里",
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkFaint,
            )
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
            Divider()
            Metric("本月支出", "-" + Fmt.fenToYuan(summary.expenseFen), AccentRed)
            Divider()
            Metric("结余", Fmt.fenToYuan(summary.balanceFen), OnDarkPrimary)
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).height(30.dp).background(DarkSurfaceHigh))
}

@Composable
private fun Metric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnDarkSub)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** 单条流水：商户 + 来源/时间 + 金额。 */
@Composable
private fun TransactionRow(txn: Transaction) {
    val isExpense = txn.direction == Direction.EXPENSE
    val amountColor = if (isExpense) AccentRed else AccentGold
    Surface(shape = RoundedCornerShape(16.dp), color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
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
