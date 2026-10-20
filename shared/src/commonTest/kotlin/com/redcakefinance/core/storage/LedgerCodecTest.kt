package com.redcakefinance.core.storage

import com.redcakefinance.core.model.BalanceCheckpoint
import com.redcakefinance.core.model.Direction
import com.redcakefinance.core.model.Transaction
import com.redcakefinance.core.model.TransactionOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 账本文本编解码测试：往返一致、特殊字符转义、坏行不致命、空输入安全。
 *
 * 这些用例是「本机存储不会悄悄丢数据」的唯一保证，改动格式时必须同步维护。
 */
class LedgerCodecTest {
    private fun txn(
        id: Long = 1,
        merchant: String = "瑞幸咖啡",
        rawBody: String = "【工商银行】您尾号5678卡消费12.50元，余额1,000.00元。",
        sender: String? = "工商银行",
        categoryId: Long? = null,
        smsId: Long? = null,
    ) = Transaction(
        id = id, smsId = smsId, sender = sender, merchant = merchant,
        amountFen = 1250L, direction = Direction.EXPENSE, occurredAt = 1_700_000_000_000L,
        categoryId = categoryId, confidence = 0.95, rawBody = rawBody,
        importedAt = 1_700_000_100_000L, origin = TransactionOrigin.IMPORTED,
    )

    @Test fun `空文本解码为空账本`() {
        assertTrue(LedgerCodec.decode("").transactions.isEmpty())
        assertTrue(LedgerCodec.decode("   ").transactions.isEmpty())
    }

    @Test fun `没有版本头视为无效`() {
        assertTrue(LedgerCodec.decode("随便一段文字").transactions.isEmpty())
    }

    @Test fun `单条流水往返一致`() {
        val original = Ledger(transactions = listOf(txn()))
        val decoded = LedgerCodec.decode(LedgerCodec.encode(original))
        assertEquals(1, decoded.transactions.size)
        assertEquals(original.transactions[0], decoded.transactions[0])
    }

    @Test fun `余额快照往返一致`() {
        val cp = BalanceCheckpoint(
            id = 7, sender = "招商银行", balanceFen = 123_456L,
            capturedAt = 1_700_000_000_000L, rawBody = "余额1,234.56元",
        )
        val decoded = LedgerCodec.decode(LedgerCodec.encode(Ledger(checkpoints = listOf(cp))))
        assertEquals(listOf(cp), decoded.checkpoints)
    }

    @Test fun `可空字段保留null还是空串`() {
        val decoded = LedgerCodec.decode(
            LedgerCodec.encode(Ledger(transactions = listOf(txn(sender = null, categoryId = null, smsId = null)))),
        )
        val t = decoded.transactions.single()
        assertNull(t.sender)
        assertNull(t.categoryId)
        assertNull(t.smsId)
    }

    @Test fun `空字符串字段不会变成null`() {
        val decoded = LedgerCodec.decode(
            LedgerCodec.encode(Ledger(transactions = listOf(txn(sender = "")))),
        )
        assertEquals("", decoded.transactions.single().sender)
    }

    @Test fun `制表符与换行被转义后仍能还原`() {
        val messy = "商户\t含制表符\n含换行\r含回车\\含反斜杠"
        val decoded = LedgerCodec.decode(
            LedgerCodec.encode(Ledger(transactions = listOf(txn(merchant = messy, rawBody = messy)))),
        )
        val t = decoded.transactions.single()
        assertEquals(messy, t.merchant)
        assertEquals(messy, t.rawBody)
    }

    @Test fun `坏行被跳过而不是整份报废`() {
        val good = LedgerCodec.encode(Ledger(transactions = listOf(txn())))
        val broken = good + "T\t不是数字\t-\t=x\n" + "G\tunknown\n"
        val decoded = LedgerCodec.decode(broken)
        assertEquals(1, decoded.transactions.size)
    }

    @Test fun `未知转义序列按字面保留`() {
        assertEquals("a\\qb", LedgerCodec.unescape("a\\qb"))
    }
}
