package com.redcakefinance.core.storage

import com.redcakefinance.core.model.BalanceCheckpoint
import com.redcakefinance.core.model.Transaction
import com.redcakefinance.core.model.TransactionOrigin

/**
 * 账本文本编解码（制表符分隔，一行一条记录）。
 *
 * 为什么不直接上 JSON 库：这份数据的字段是定长的扁平结构，
 * 而「多引一个序列化依赖」意味着又多一个与 Kotlin 版本耦合的组件。
 * 这里用显式转义 + 定长字段，格式可读、可手工修补，并且能用单测逐条验证往返一致。
 *
 * 字段里的制表符、换行、反斜杠会被转义，因此按制表符切分是安全的。
 * 可空字段用前缀区分：`-` 表示 null，`=` 后跟转义后的值表示非空。
 */
object LedgerCodec {
    private const val HEADER = "REDCAKE1"
    private const val SEP = '\t'

    private const val KIND_TXN = "T"
    private const val KIND_CHECKPOINT = "C"

    /** 编码整份账本。 */
    fun encode(ledger: Ledger): String = buildString {
        append(HEADER).append('\n')
        ledger.transactions.forEach { t ->
            append(KIND_TXN)
            append(SEP).append(t.id)
            append(SEP).append(nullableLong(t.smsId))
            append(SEP).append(nullableString(t.sender))
            append(SEP).append(escape(t.merchant))
            append(SEP).append(t.amountFen)
            append(SEP).append(escape(t.direction))
            append(SEP).append(t.occurredAt)
            append(SEP).append(nullableLong(t.categoryId))
            append(SEP).append(t.confidence)
            append(SEP).append(escape(t.rawBody))
            append(SEP).append(t.importedAt)
            append(SEP).append(t.origin)
            append('\n')
        }
        ledger.checkpoints.forEach { c ->
            append(KIND_CHECKPOINT)
            append(SEP).append(c.id)
            append(SEP).append(escape(c.sender))
            append(SEP).append(c.balanceFen)
            append(SEP).append(c.capturedAt)
            append(SEP).append(escape(c.rawBody))
            append('\n')
        }
    }

    /**
     * 解码账本。无法识别或字段数不符的行会被跳过，绝不抛异常——
     * 一条坏记录不该让整份账本打不开。
     *
     * @param text 空串 / 无版本头的文本返回空账本
     */
    fun decode(text: String): Ledger {
        if (text.isBlank()) return Ledger()
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != HEADER) return Ledger()

        val transactions = mutableListOf<Transaction>()
        val checkpoints = mutableListOf<BalanceCheckpoint>()

        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val f = line.split(SEP)
            when (f.firstOrNull()) {
                KIND_TXN -> decodeTxn(f)?.let { transactions += it }
                KIND_CHECKPOINT -> decodeCheckpoint(f)?.let { checkpoints += it }
            }
        }
        return Ledger(transactions, checkpoints)
    }

    private fun decodeTxn(f: List<String>): Transaction? {
        if (f.size != 13) return null
        val id = f[1].toLongOrNull() ?: return null
        val amountFen = f[5].toLongOrNull() ?: return null
        val occurredAt = f[7].toLongOrNull() ?: return null
        val importedAt = f[11].toLongOrNull() ?: return null
        return Transaction(
            id = id,
            smsId = parseNullableLong(f[2]),
            sender = parseNullableString(f[3]),
            merchant = unescape(f[4]),
            amountFen = amountFen,
            direction = unescape(f[6]),
            occurredAt = occurredAt,
            categoryId = parseNullableLong(f[8]),
            confidence = f[9].toDoubleOrNull() ?: 0.0,
            rawBody = unescape(f[10]),
            importedAt = importedAt,
            origin = f[12].toIntOrNull() ?: TransactionOrigin.IMPORTED,
        )
    }

    private fun decodeCheckpoint(f: List<String>): BalanceCheckpoint? {
        if (f.size != 6) return null
        val id = f[1].toLongOrNull() ?: return null
        val balanceFen = f[3].toLongOrNull() ?: return null
        val capturedAt = f[4].toLongOrNull() ?: return null
        return BalanceCheckpoint(
            id = id,
            sender = unescape(f[2]),
            balanceFen = balanceFen,
            capturedAt = capturedAt,
            rawBody = unescape(f[5]),
        )
    }

    private fun nullableLong(v: Long?): String = if (v == null) "-" else "=$v"

    private fun nullableString(v: String?): String = if (v == null) "-" else "=" + escape(v)

    private fun parseNullableLong(s: String): Long? =
        if (s.startsWith("=")) s.substring(1).toLongOrNull() else null

    private fun parseNullableString(s: String): String? =
        if (s.startsWith("=")) unescape(s.substring(1)) else null

    /** 转义：反斜杠、制表符、换行、回车。 */
    fun escape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    /** 反转义；无法识别的转义序列按字面保留。 */
    fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> { out.append('\\'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    'n' -> { out.append('\n'); i += 2 }
                    'r' -> { out.append('\r'); i += 2 }
                    else -> { out.append(c); i += 1 }
                }
            } else {
                out.append(c); i += 1
            }
        }
        return out.toString()
    }
}
