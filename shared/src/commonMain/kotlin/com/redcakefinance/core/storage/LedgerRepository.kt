package com.redcakefinance.core.storage

import com.redcakefinance.core.model.BalanceCheckpoint
import com.redcakefinance.core.model.Transaction
import com.redcakefinance.core.model.TransactionOrigin
import com.redcakefinance.core.sms.SmsMeta
import com.redcakefinance.core.sms.SmsParser
import com.redcakefinance.platform.KeyValueStore

/** 一次剪贴板导入的结果。 */
sealed interface ImportResult {
    /** 已记账；[checkpoint] 为本次顺带捕获的余额快照（短信里没有余额时为 null）。 */
    data class Added(val transaction: Transaction, val checkpoint: BalanceCheckpoint?) : ImportResult

    /** 同一条短信之前已经导过（按正文完全一致判定）。 */
    data object Duplicate : ImportResult

    /** 没认出这是条银行短信，或剪贴板为空。 */
    data class Failed(val reason: String) : ImportResult
}

/**
 * 账本仓库：负责读写账本文本，以及把一条短信变成一笔流水。
 *
 * 与 Android 版不同，iOS 拿不到系统短信库，所以「发件人」与「交易时间」都从正文推断
 * （见 [SmsMeta]）；推断不出来时分别回落到「未知来源」和「现在」。
 */
class LedgerRepository {

    /** 读取整份账本；没有存过时返回空账本。 */
    fun load(): Ledger = LedgerCodec.decode(KeyValueStore.getString(KEY).orEmpty())

    /** 保存整份账本。 */
    fun save(ledger: Ledger) {
        KeyValueStore.putString(KEY, LedgerCodec.encode(ledger))
    }

    /**
     * 把剪贴板里的一段文本当作一条短信导入。
     *
     * 只取第一段非空文本：从短信 App 复制过来时常带多余空行。
     *
     * @param raw 剪贴板原文
     * @param nowMillis 当前时间（用于补交易年份、记录导入时间）
     */
    fun importFromText(raw: String, nowMillis: Long): ImportResult {
        val body = raw.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (body.isBlank()) return ImportResult.Failed("剪贴板是空的")

        val ledger = load()
        if (ledger.transactions.any { it.rawBody == body }) return ImportResult.Duplicate

        val sender = SmsMeta.extractSender(body) ?: SmsMeta.UNKNOWN_SENDER
        val parsed = SmsParser.parse(body, sender)
            ?: return ImportResult.Failed("没认出金额，可能不是银行短信")

        val occurredAt = SmsMeta.parseOccurredAt(body, nowMillis)
        val txn = Transaction(
            id = (ledger.transactions.maxOfOrNull { it.id } ?: 0L) + 1,
            smsId = null,
            sender = sender,
            merchant = parsed.merchant,
            amountFen = parsed.amountFen,
            direction = parsed.direction,
            occurredAt = occurredAt,
            categoryId = null,
            confidence = parsed.confidence,
            rawBody = body,
            importedAt = nowMillis,
            origin = TransactionOrigin.IMPORTED,
        )

        // 余额快照：统计口径的基础，只有正文带余额时才产生
        val balanceFen = SmsParser.extractBalanceFen(body)
        val checkpoint = balanceFen?.let { fen ->
            BalanceCheckpoint(
                id = (ledger.checkpoints.maxOfOrNull { it.id } ?: 0L) + 1,
                sender = sender,
                balanceFen = fen,
                capturedAt = occurredAt,
                rawBody = body,
            )
        }

        save(
            ledger.copy(
                transactions = ledger.transactions + txn,
                checkpoints = if (checkpoint != null) ledger.checkpoints + checkpoint else ledger.checkpoints,
            ),
        )
        return ImportResult.Added(txn, checkpoint)
    }

    /** 删除一笔流水（余额快照保留：它记录的是银行当时的真实状态）。 */
    fun deleteTransaction(id: Long): Ledger {
        val ledger = load()
        val updated = ledger.copy(transactions = ledger.transactions.filterNot { it.id == id })
        save(updated)
        return updated
    }

    /** 清空流水与余额快照，保留在用的设置。 */
    fun clearAll(): Ledger {
        save(Ledger())
        return Ledger()
    }

    /** 导出整份账本文本（备份用，格式见 [LedgerCodec]）。 */
    fun exportText(): String = LedgerCodec.encode(load())

    /**
     * 从备份文本整份恢复。
     *
     * 采用「整体替换」而不是合并：恢复的语义是把手机上的账本还原到备份那一刻，
     * 合并会在重复导入时产出难以解释的重复流水。
     *
     * @return 恢复后的账本；文本不是本应用的备份格式时返回 null（原数据不动）
     */
    fun restoreFromText(text: String): Ledger? {
        if (!text.contains(LedgerCodec.HEADER)) return null
        val restored = LedgerCodec.decode(text)
        if (restored.transactions.isEmpty() && restored.checkpoints.isEmpty()) return null
        save(restored)
        return restored
    }

    private companion object {
        const val KEY = "ledger_v1"
    }
}
