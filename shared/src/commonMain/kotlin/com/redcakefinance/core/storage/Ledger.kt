package com.redcakefinance.core.storage

import com.redcakefinance.core.model.BalanceCheckpoint
import com.redcakefinance.core.model.Transaction

/**
 * 整份账本：流水 + 余额快照。
 *
 * 余额快照是统计口径的基础（曲线、月度净额、对账都由相邻快照之差推导），
 * 所以它和流水同属一份数据，必须一起保存、一起迁移。
 */
data class Ledger(
    val transactions: List<Transaction> = emptyList(),
    val checkpoints: List<BalanceCheckpoint> = emptyList(),
)
