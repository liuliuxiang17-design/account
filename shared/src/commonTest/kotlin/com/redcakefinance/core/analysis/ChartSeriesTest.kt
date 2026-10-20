package com.redcakefinance.core.analysis

import com.redcakefinance.core.format.TimeFormat
import com.redcakefinance.core.model.BalanceCheckpoint
import com.redcakefinance.core.model.Direction
import com.redcakefinance.core.model.Transaction
import com.redcakefinance.core.storage.Ledger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 图表序列测试：月份平移、余额曲线取点、月度净额口径与缺月补齐。
 */
class ChartSeriesTest {
    private fun at(text: String): Long = TimeFormat.parse(text, "yyyy-MM-dd HH:mm")!!

    private fun txn(id: Long, fen: Long, time: Long, dir: String = Direction.EXPENSE) = Transaction(
        id = id, smsId = null, sender = "工商银行", merchant = "m", amountFen = fen,
        direction = dir, occurredAt = time, categoryId = null, confidence = 1.0,
        rawBody = "", importedAt = 0L,
    )

    private fun cp(id: Long, fen: Long, time: Long) =
        BalanceCheckpoint(id = id, sender = "工商银行", balanceFen = fen, capturedAt = time, rawBody = "")

    @Test fun `月份平移跨年`() {
        assertEquals("2025-12", ChartSeries.shiftMonth("2026-01", -1))
        assertEquals("2026-01", ChartSeries.shiftMonth("2025-12", 1))
        assertEquals("2026-03", ChartSeries.shiftMonth("2026-05", -2))
        assertEquals("2027-02", ChartSeries.shiftMonth("2026-12", 2))
    }

    @Test fun `缺月补零且月份连续`() {
        val now = at("2026-10-20 12:00")
        val ledger = Ledger(
            transactions = listOf(txn(1, 10_000, at("2026-10-05 10:00"))),
        )
        val charts = ChartSeries.build(ledger, now, monthsBack = 3)
        assertEquals(listOf("2026-08", "2026-09", "2026-10"), charts.monthlyNet.map { it.first })
        assertEquals(listOf(0L, 0L, -10_000L), charts.monthlyNet.map { it.second })
    }

    @Test fun `没有余额快照时用解析金额`() {
        val now = at("2026-10-20 12:00")
        val ledger = Ledger(
            transactions = listOf(
                txn(1, 10_000, at("2026-10-05 10:00"), Direction.EXPENSE),
                txn(2, 3_000, at("2026-10-06 10:00"), Direction.INCOME),
            ),
        )
        val charts = ChartSeries.build(ledger, now, monthsBack = 1)
        assertFalse(charts.netFromBalance)
        assertEquals(-7_000L, charts.monthlyNet.single().second)
    }

    @Test fun `有余额快照时以快照差为准`() {
        val now = at("2026-10-20 12:00")
        val ledger = Ledger(
            // 解析金额与真实变动故意不一致：以余额为准，结果应为 -10_000
            transactions = listOf(txn(1, 9_500, at("2026-10-05 10:00"))),
            checkpoints = listOf(
                cp(1, 100_000, at("2026-09-30 10:00")),
                cp(2, 90_000, at("2026-10-10 10:00")),
            ),
        )
        val charts = ChartSeries.build(ledger, now, monthsBack = 2)
        assertTrue(charts.netFromBalance)
        assertEquals(-10_000L, charts.monthlyNet.last().second)
    }

    @Test fun `余额曲线取各账户合计`() {
        val now = at("2026-10-20 12:00")
        val ledger = Ledger(
            checkpoints = listOf(
                cp(1, 100_000, at("2026-10-01 10:00")),
                cp(2, 120_000, at("2026-10-02 10:00")),
            ),
        )
        val charts = ChartSeries.build(ledger, now)
        assertEquals(2, charts.balancePoints.size)
        assertEquals(100_000L, charts.balancePoints.first().balanceFen)
        assertEquals(120_000L, charts.balancePoints.last().balanceFen)
    }

    @Test fun `空账本不崩溃`() {
        val charts = ChartSeries.build(Ledger(), at("2026-10-20 12:00"))
        assertTrue(charts.balancePoints.isEmpty())
        assertTrue(charts.monthlyNet.all { it.second == 0L })
        assertFalse(charts.netFromBalance)
    }
}
