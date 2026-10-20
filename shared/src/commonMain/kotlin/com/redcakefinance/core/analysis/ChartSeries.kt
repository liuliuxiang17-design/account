package com.redcakefinance.core.analysis

import com.redcakefinance.core.format.TimeFormat
import com.redcakefinance.core.model.Direction
import com.redcakefinance.core.storage.Ledger

/**
 * 图表数据：余额曲线 + 月度净额。
 *
 * @param balancePoints 各账户余额合计随时间变化的点
 * @param monthlyNet 月份（yyyy-MM）→ 净额（分），连续月份已补齐 0
 * @param netFromBalance true 表示净额来自余额快照差；false 表示没有任何快照、退回解析金额
 */
data class Charts(
    val balancePoints: List<BalancePoint>,
    val monthlyNet: List<Pair<String, Long>>,
    val netFromBalance: Boolean,
)

/**
 * 把账本换算成图表用的序列。
 *
 * 口径与 Android 版一致：余额是唯一真值——曲线取各账户最新余额之和，
 * 月度净额取相邻快照之差；完全没有余额快照时才退回「收入 − 支出」。
 */
object ChartSeries {
    /** 最近 [monthsBack] 个自然月（含当月）的序列。 */
    fun build(ledger: Ledger, nowMillis: Long, monthsBack: Int = 6): Charts {
        val timeline = BalanceTruth.timeline(ledger.checkpoints)
        val useBalance = timeline.steps.isNotEmpty()

        val endYm = TimeFormat.format(nowMillis, "yyyy-MM")
        val startYm = shiftMonth(endYm, -(monthsBack - 1))
        val from = TimeFormat.parse("$startYm-01 00:00", "yyyy-MM-dd HH:mm") ?: 0L
        val nextYm = shiftMonth(endYm, 1)
        val to = TimeFormat.parse("$nextYm-01 00:00", "yyyy-MM-dd HH:mm") ?: (nowMillis + 1)

        val raw: Map<String, Long> = if (useBalance) {
            BalanceTruth.netByBucket(timeline, from, to) { TimeFormat.format(it, "yyyy-MM") }.toMap()
        } else {
            ledger.transactions
                .filter { it.occurredAt >= from && it.occurredAt < to }
                .groupBy { TimeFormat.format(it.occurredAt, "yyyy-MM") }
                .mapValues { (_, list) ->
                    list.sumOf { if (it.direction == Direction.EXPENSE) -it.amountFen else it.amountFen }
                }
        }

        // 补齐没有数据的月份：柱子按月份等距排列，缺月留空更符合直觉
        val months = (0 until monthsBack).map { shiftMonth(startYm, it) }
        return Charts(
            balancePoints = BalanceTruth.totalSeries(timeline),
            monthlyNet = months.map { it to (raw[it] ?: 0L) },
            netFromBalance = useBalance,
        )
    }

    /**
     * 月份字符串平移：`shiftMonth("2026-01", -1)` → `2025-12`。
     * @param delta 正数向后、负数向前
     */
    fun shiftMonth(ym: String, delta: Int): String {
        val year = ym.substring(0, 4).toIntOrNull() ?: return ym
        val month = ym.substring(5, 7).toIntOrNull() ?: return ym
        var total = year * 12 + (month - 1) + delta
        var y = total / 12
        var m = total % 12
        if (m < 0) {
            m += 12
            y -= 1
        }
        return "$y-" + m.plus(1).toString().padStart(2, '0')
    }
}
