package com.redcakefinance.core.analysis

import com.redcakefinance.core.format.TimeFormat
import com.redcakefinance.core.model.Direction
import com.redcakefinance.core.storage.Ledger

/** 概览数字（分）：本期收入、本期支出、结余。 */
data class Summary(
    val incomeFen: Long,
    val expenseFen: Long,
    val balanceFen: Long,
    /** true 表示结余来自余额快照；false 表示没有任何快照、退回「收入 − 支出」。 */
    val balanceFromSnapshot: Boolean,
)

/**
 * 概览统计。
 *
 * 口径与 Android 版一致：结余优先取「各账户最新余额快照之和」（存量真值），
 * 一条快照都没有时才退回「收入 − 支出」；流水口径只统计当月。
 */
object LedgerSummary {
    /** 当前自然月的起点（毫秒）。 */
    fun monthStart(nowMillis: Long): Long {
        val ym = TimeFormat.format(nowMillis, "yyyy-MM")
        return TimeFormat.parse("$ym-01 00:00", "yyyy-MM-dd HH:mm") ?: nowMillis
    }

    /**
     * @param nowMillis 当前时间；计算窗口为 [monthStart, nowMillis]
     */
    fun of(ledger: Ledger, nowMillis: Long): Summary {
        val from = monthStart(nowMillis)
        val inMonth = ledger.transactions.filter { it.occurredAt in from..nowMillis }

        val income = inMonth.filter { it.direction == Direction.INCOME }.sumOf { it.amountFen }
        val expense = inMonth.filter { it.direction == Direction.EXPENSE }.sumOf { it.amountFen }

        val latestPerSender = ledger.checkpoints
            .groupBy { it.sender }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.capturedAt } }
        val hasSnapshot = latestPerSender.isNotEmpty()
        val balance = if (hasSnapshot) latestPerSender.sumOf { it.balanceFen } else income - expense

        return Summary(
            incomeFen = income,
            expenseFen = expense,
            balanceFen = balance,
            balanceFromSnapshot = hasSnapshot,
        )
    }
}
