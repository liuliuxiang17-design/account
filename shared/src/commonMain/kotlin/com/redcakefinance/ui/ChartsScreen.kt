package com.redcakefinance.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.redcakefinance.core.analysis.ChartSeries
import com.redcakefinance.core.analysis.Charts
import com.redcakefinance.core.format.Fmt
import com.redcakefinance.core.storage.Ledger
import com.redcakefinance.platform.PlatformClock
import com.redcakefinance.ui.theme.AccentGold
import com.redcakefinance.ui.theme.AccentRed
import com.redcakefinance.ui.theme.AccentRedLight
import com.redcakefinance.ui.theme.DarkSurface
import com.redcakefinance.ui.theme.DarkSurfaceHigh
import com.redcakefinance.ui.theme.OnDarkFaint
import com.redcakefinance.ui.theme.OnDarkPrimary
import com.redcakefinance.ui.theme.OnDarkSub

/**
 * 图表页：余额曲线 + 月度净额。
 *
 * 两张图都用 Canvas 自绘，不引入第三方图表库；坐标轴文字放在画布外用 Text 排，
 * 既避免在 Canvas 里画文字的跨平台差异，也让文字跟随系统字号缩放。
 */
@Composable
fun ChartsScreen(ledger: Ledger, modifier: Modifier = Modifier) {
    val charts = remember(ledger) { ChartSeries.build(ledger, PlatformClock.nowMillis()) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BalanceCard(charts)
        MonthlyCard(charts)
        Spacer(Modifier.height(16.dp))
    }
}

/** 余额曲线卡。 */
@Composable
private fun BalanceCard(charts: Charts) {
    val points = charts.balancePoints
    ChartCard(
        title = "余额曲线",
        subtitle = "各账户余额合计随时间变化",
        footnote = if (points.isEmpty()) {
            "还没有余额快照。带「余额」字样的银行短信记进来后，这里会自动出现曲线。"
        } else {
            "共 ${points.size} 个观测点 · 最新 " + Fmt.fenToYuan(points.last().balanceFen)
        },
    ) {
        if (points.size < 2) {
            ChartPlaceholder(if (points.isEmpty()) "暂无数据" else "至少需要两条余额快照")
        } else {
            // 纵轴范围：留 8% 余量，避免曲线贴边
            val minFen = points.minOf { it.balanceFen }
            val maxFen = points.maxOf { it.balanceFen }
            val span = (maxFen - minFen).coerceAtLeast(1L)
            val padded = span / 12 // ≈8%
            val lo = (minFen - padded).toDouble()
            val hi = (maxFen + padded).toDouble()
            val range = (hi - lo).coerceAtLeast(1.0)

            Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                val w = size.width
                val h = size.height

                // 三条横向参考线
                val gridColor = DarkSurfaceHigh
                for (i in 0..2) {
                    val y = h * i / 2f
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }

                fun yOf(fen: Long): Float = (h - ((fen - lo) / range) * h).toFloat()

                val path = Path()
                points.forEachIndexed { i, p ->
                    val x = if (points.size == 1) w / 2f else w * i / (points.size - 1).toFloat()
                    val y = yOf(p.balanceFen)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, AccentRedLight, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                // 点少时补上节点，避免只剩一条没有锚点的线
                if (points.size <= 24) {
                    points.forEachIndexed { i, p ->
                        val x = if (points.size == 1) w / 2f else w * i / (points.size - 1).toFloat()
                        drawCircle(AccentRedLight, radius = 3.dp.toPx(), center = Offset(x, yOf(p.balanceFen)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.fenToYuan(minFen), style = MaterialTheme.typography.labelSmall, color = OnDarkFaint)
                Text(Fmt.fenToYuan(maxFen), style = MaterialTheme.typography.labelSmall, color = OnDarkFaint)
            }
        }
    }
}

/** 月度净额柱状图卡：零线上方为净收入、下方为净支出。 */
@Composable
private fun MonthlyCard(charts: Charts) {
    val data = charts.monthlyNet
    ChartCard(
        title = "月度净额",
        subtitle = if (charts.netFromBalance) "按余额快照差统计" else "按解析金额统计（尚无余额快照）",
        footnote = if (data.all { it.second == 0L }) {
            "最近半年还没有可统计的变动。"
        } else {
            "正数=净收入，负数=净支出 · " + Fmt.fenToYuan(data.sumOf { it.second })
        },
    ) {
        if (data.isEmpty()) {
            ChartPlaceholder("暂无数据")
        } else {
            val maxAbs = data.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1L).toDouble()
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val w = size.width
                val h = size.height
                val mid = h / 2f               // 零线固定在中间：正负同尺度，视觉不误导
                val slot = w / data.size
                val barW = (slot * 0.52f).coerceAtMost(26.dp.toPx())

                drawLine(OnDarkFaint, Offset(0f, mid), Offset(w, mid), strokeWidth = 1f)

                data.forEachIndexed { i, (_, fen) ->
                    if (fen == 0L) return@forEachIndexed
                    val ratio = (kotlin.math.abs(fen) / maxAbs).toFloat()
                    val barH = (mid - 2.dp.toPx()) * ratio
                    val cx = slot * i + slot / 2f
                    val top = if (fen > 0) mid - barH else mid
                    drawRect(
                        color = if (fen > 0) AccentGold else AccentRed,
                        topLeft = Offset(cx - barW / 2f, top),
                        size = androidx.compose.ui.geometry.Size(barW, barH),
                        style = androidx.compose.ui.graphics.drawscope.Fill,
                    )
                }
            }
            // 月份标签：只显示月份数字，年份变化在下面一行标注
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                data.forEach { (ym, _) ->
                    Text(
                        ym.substring(5, 7),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                data.first().first.substring(0, 4) + " – " + data.last().first.substring(0, 4),
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkFaint,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 图表卡片外壳：标题 + 副标题 + 内容 + 页脚说明。 */
@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    footnote: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = OnDarkPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "  " + subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkSub,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(10.dp))
            Text(footnote, style = MaterialTheme.typography.labelSmall, color = OnDarkFaint)
        }
    }
}

/** 图表的空状态占位。 */
@Composable
private fun ChartPlaceholder(text: String) {
    Box(
        Modifier.fillMaxWidth().height(120.dp).background(DarkSurfaceHigh, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = OnDarkFaint)
    }
}
