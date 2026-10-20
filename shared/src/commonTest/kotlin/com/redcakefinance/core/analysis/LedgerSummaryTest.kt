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
 * 概览口径测试：当月收入/支出、结余优先取余额快照。
 */
class LedgerSummaryTest {
    private fun at(text: String): Long = TimeFormat.parse(text, "yyyy-MM-dd HH:mm")!!

    private fun txn(id: Long, fen: Long, time: Long, dir: String) = Transaction(
        id = id, smsId = null, sender = "工商银行", merchant = "m", amountFen = fen,
        direction = dir, occurredAt = time, categoryId = null, confidence = 1.0,
        rawBody = "", importedAt = 0L,
    )

    private fun cp(id: Long, sender: String, fen: Long, time: Long) =
        BalanceCheckpoint(id = id, sender = sender, balanceFen = fen, capturedAt = time, rawBody = "")

    @Test fun `只统计当月流水`() {
        val now = at("2026-10-20 12:00")
        val ledger = Ledger(
            transactions = listOf(
                txn(1, 10_000, at("2026-10-05 10:00"), Direction.EXPENSE),
                txn(2, 5_000, at("2026-10-06 10:00"), Direction.INCOME),
                txn(3, 99_999, at("2026-09-30 23:59"), Direction.EXPENSE), // 上月，不计
            ),
        )
        val s = LedgerSummary.of(ledger, now)
        assertEquals(10_000L, s.expenseFen)
        assertEquals(5_000L, s.incomeFen)
        assertFalse(s.balanceFromSnapshot)
        assertEquals(5_000L - 10_000L, s.balanceFen)
    }

    @Test fun `结余优先取各账户最新快照`() {
        val now = at("2026-10-20 12:00")
        val ledger = Ledger(
            transactions = listOf(txn(1, 10_000, at("2026-10-05 10:00"), Direction.EXPENSE)),
            checkpoints = listOf(
                cp(1, "工商银行", 100_000, at("2026-10-04 10:00")),
                cp(2, "工商银行", 80_000, at("2026-10-06 10:00")),   // 最新
                cp(3, "招商银行", 50_000, at("2026-10-07 10:00")),
            ),
        )
        val s = LedgerSummary.of(ledger, now)
        assertTrue(s.balanceFromSnapshot)
        assertEquals(80_000L + 50_000L, s.balanceFen)
    }

    @Test fun `退款不计入收入`() {
        val now = at("2026-10-20 12:00")
        val ledger = Ledger(
            transactions = listOf(txn(1, 4_500, at("2026-10-05 10:00"), Direction.REFUND)),
        )
        val s = LedgerSummary.of(ledger, now)
        assertEquals(0L, s.incomeFen)
        assertEquals(0L, s.expenseFen)
    }
}
