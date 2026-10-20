package com.redcakefinance.core.analysis

import com.redcakefinance.core.model.BalanceCheckpoint
import com.redcakefinance.core.model.Direction
import com.redcakefinance.core.model.Transaction
import com.redcakefinance.core.sms.SmsParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 余额真值测试：区间净额、缺口、快照去重与合计曲线、余额提取。
 *
 * 重复/差额检测（旧 `DiffEngine`）已删除：真重复由 `smsId` 去重拦住，
 * 其余让账对不上的重复会由余额对账表现为反向缺口。
 */
class BalanceTruthTest {
    private val t0 = 1_700_000_000_000L
    private val t1 = t0 + 100_000L
    private val t2 = t0 + 200_000L

    private fun cp(id: Long, sender: String, fen: Long, time: Long) =
        BalanceCheckpoint(id = id, sender = sender, balanceFen = fen, capturedAt = time, rawBody = "")

    private fun txn(id: Long, sender: String, fen: Long, time: Long, dir: String = Direction.EXPENSE) =
        Transaction(id = id, smsId = id, sender = sender, merchant = "m", amountFen = fen,
            direction = dir, occurredAt = time, categoryId = null, confidence = 1.0, rawBody = "", importedAt = 0)

    @Test fun `余额差与流水净额吻合_无缺口`() {
        val cps = listOf(cp(1, "工行", 100_000, t0), cp(2, "工行", 90_000, t1))
        val flows = listOf(txn(1, "工行", 10_000, t0 + 50_000))
        assertTrue(BalanceTruth.gaps(cps, flows).isEmpty())
    }

    @Test fun `流水缺失_有缺口`() {
        val cps = listOf(cp(1, "工行", 100_000, t0), cp(2, "工行", 90_000, t1))
        val flows = listOf(txn(1, "工行", 9_500, t0 + 50_000))
        val r = BalanceTruth.gaps(cps, flows)
        assertEquals(1, r.size); assertEquals(-500L, r[0].gapFen)
        assertEquals(Direction.EXPENSE, r[0].missingDirection)
    }

    @Test fun `多账户不串算`() {
        // A 与 B 的快照交错：A 少 100 元、B 少 30 元，流水只记录了 A 的 100 元
        val cps = listOf(
            cp(1, "A", 100_000, t0),
            cp(2, "B", 50_000, t0 + 10_000),
            cp(3, "A", 90_000, t1),
            cp(4, "B", 47_000, t1 + 10_000),
        )
        val flows = listOf(txn(1, "A", 10_000, t0 + 50_000))
        val r = BalanceTruth.gaps(cps, flows)
        // A 对得上 → 无缺口；B 缺 30 元 → 一条缺口，且不被 A 的流水冲掉
        assertEquals(1, r.size)
        assertEquals("B", r[0].sender)
        assertEquals(-3_000L, r[0].gapFen)
    }

    @Test fun `重复快照去重后只有一步`() {
        val cps = listOf(
            cp(1, "工行", 100_000, t0),
            cp(2, "工行", 100_000, t0), // 重复扫描写进来的同一快照
            cp(3, "工行", 90_000, t1),
        )
        assertEquals(1, BalanceTruth.steps(cps).size)
        assertEquals(-10_000L, BalanceTruth.steps(cps).first().deltaFen)
    }

    @Test fun `合计余额曲线_按各账户最新余额求和`() {
        val cps = listOf(
            cp(1, "A", 100_000, t0),
            cp(2, "B", 20_000, t0 + 5_000),
            cp(3, "A", 150_000, t1),
            cp(4, "B", 20_000, t2),
        )
        val series = BalanceTruth.totalSeries(cps)
        assertEquals(3, series.size)
        assertEquals(100_000L, series[0].balanceFen)
        assertEquals(120_000L, series[1].balanceFen)
        assertEquals(170_000L, series[2].balanceFen)
    }

    /** 一一对应：区间内恰好一笔且金额吻合 → 精确对应。 */
    @Test fun `一一对应_1对1精确匹配`() {
        val cps = listOf(cp(1, "工行", 100_000, t0), cp(2, "工行", 90_000, t1))
        val flows = listOf(txn(7, "工行", 10_000, t0 + 50_000))
        val rec = BalanceTruth.match(cps, flows)
        assertEquals(1, rec.intervals.size)
        assertEquals(MatchKind.EXACT_1TO1, rec.intervals[0].kind)
        assertEquals(listOf(7L), rec.intervals[0].flows.map { it.txnId })
        assertTrue(rec.unexplained.isEmpty())
        assertTrue(rec.orphans.isEmpty())
    }

    /** 一一对应：多笔流水之和等于余额差 → 求和对应，逐笔都要记在区间名下。 */
    @Test fun `一一对应_多笔求和匹配`() {
        val cps = listOf(cp(1, "工行", 100_000, t0), cp(2, "工行", 90_000, t1))
        val flows = listOf(
            txn(1, "工行", 3_000, t0 + 10_000),
            txn(2, "工行", 3_000, t0 + 20_000),
            txn(3, "工行", 4_000, t0 + 30_000),
        )
        val rec = BalanceTruth.match(cps, flows)
        assertEquals(MatchKind.SUM_MANY, rec.intervals[0].kind)
        assertEquals(setOf(1L, 2L, 3L), rec.intervals[0].flows.map { it.txnId }.toSet())
        assertTrue(rec.orphans.isEmpty())
    }

    /** 一一对应：早于第一条余额快照的流水不该静默通过，必须报成无主。 */
    @Test fun `一一对应_无主流水被报出`() {
        val cps = listOf(cp(1, "工行", 100_000, t1), cp(2, "工行", 90_000, t2))
        val flows = listOf(
            txn(9, "工行", 5_000, t0),          // 早于第一条快照 → 无主
            txn(8, "工行", 10_000, t1 + 50_000), // 区间内，正常认领
        )
        val rec = BalanceTruth.match(cps, flows)
        assertEquals(1, rec.orphans.size)
        assertEquals(9L, rec.orphans[0].txnId)
        assertEquals(MatchKind.EXACT_1TO1, rec.intervals[0].kind)
    }

    /** 一一对应：靠相邻交换解决时，明确标出「借入/借出」的流水。 */
    @Test fun `一一对应_相邻交换解决并记下借还`() {
        val cps = listOf(
            cp(1, "工行", 100_000, t0),
            cp(2, "工行", 100_000, t1),
            cp(3, "工行", 90_000, t2),
        )
        val flows = listOf(txn(5, "工行", 10_000, t0 + 50_000))
        val rec = BalanceTruth.match(cps, flows)
        assertEquals(2, rec.intervals.size)
        assertTrue(rec.intervals.all { it.kind == MatchKind.RESOLVED_BY_MOVE })
        // 第一段把它借出、第二段借入
        assertEquals(listOf(5L), rec.intervals[0].flows.filter { it.role == FlowRole.MOVED_OUT }.map { it.txnId })
        assertEquals(listOf(5L), rec.intervals[1].flows.filter { it.role == FlowRole.MOVED_IN }.map { it.txnId })
        assertTrue(rec.unexplained.isEmpty())
    }

    @Test fun `归因匹配_等额反向的伪差被抵消`() {
        // 快照：1000 → 1000（同值再拍一次，把区间切成两段）→ 900
        val cps = listOf(
            cp(1, "工行", 100_000, t0),
            cp(2, "工行", 100_000, t1),
            cp(3, "工行", 90_000, t2),
        )
        // 那笔 100 元支出被时间戳放到了第一段里
        val flows = listOf(txn(1, "工行", 10_000, t0 + 50_000))
        // 归因前：第一段 gap=+100（多出收入）、第二段 gap=−100（少记支出）
        val raw = BalanceTruth.intervals(cps, flows)
        assertEquals(100_00L, raw[0].gapFen)
        assertEquals(-100_00L, raw[1].gapFen)
        // 归因后：流水被挪到能解释余额差的那一段，两段都归零，不再报缺口
        assertTrue(BalanceTruth.gaps(cps, flows).isEmpty())
    }

    /** 归因匹配不能把真缺口也"抵消"掉：只有单边残差时必须原样保留。 */
    @Test fun `归因匹配_单边真缺口仍然保留`() {
        val cps = listOf(
            cp(1, "工行", 100_000, t0),
            cp(2, "工行", 85_000, t1),
        )
        val flows = listOf(txn(1, "工行", 10_000, t0 + 50_000))
        val r = BalanceTruth.gaps(cps, flows)
        assertEquals(1, r.size)
        assertEquals(-5_000L, r[0].gapFen)
    }

    /** 等额反向但由多笔小额凑成（挪不动单笔）→ 判为伪差，不报缺口，但标记 canceled 供界面说明。 */
    @Test fun `归因匹配_多笔凑成的反向残差标记为已抵消`() {
        val cps = listOf(
            cp(1, "工行", 100_000, t0),
            cp(2, "工行", 100_000, t1),
            cp(3, "工行", 94_000, t2),
        )
        val flows = listOf(
            txn(1, "工行", 3_000, t0 + 20_000),
            txn(2, "工行", 3_000, t0 + 40_000),
        )
        val rows = BalanceTruth.reconcile(cps, flows)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.canceled })
        assertTrue(BalanceTruth.gaps(cps, flows).isEmpty())
    }

    @Test fun `月净额_把余额差记到后一条快照所在月`() {
        val aug = 1_693_000_000_000L // 2023-08 附近
        val sep = aug + 40L * 24 * 3600 * 1000
        val cps = listOf(
            cp(1, "工行", 100_000, aug),
            cp(2, "工行", 80_000, sep),
        )
        val buckets = BalanceTruth.netByBucket(cps, Long.MIN_VALUE, Long.MAX_VALUE) { time ->
            com.redcakefinance.core.format.TimeFormat.format(time, "yyyy-MM")
        }
        assertEquals(1, buckets.size)
        assertEquals(-20_000L, buckets[0].second)
    }
}

class BalanceExtractTest {
    @Test fun `提取余额`() {
        assertEquals(8520L, SmsParser.extractBalanceFen("【支付宝】您尾号1234的支付宝账户于08月24日10:15向美团平台支付18.50元，余额85.20元。"))
        assertEquals(123456L, SmsParser.extractBalanceFen("【工商银行】您尾号5678卡消费200.00元，余额1,234.56元。"))
    }

    @Test fun `余额变体写法`() {
        assertEquals(1234567L, SmsParser.extractBalanceFen("支出(消费支付宝-张三)98元，余额12,345.67元。【工商银行】"))
        assertEquals(100_00L, SmsParser.extractBalanceFen("您已成功充值50.00元，当前余额100.00元。"))
        assertEquals(123456L, SmsParser.extractBalanceFen("可用余额：1,234.56元"))
        assertEquals(123456L, SmsParser.extractBalanceFen("账户余额为人民币1,234.56元"))
    }

    @Test fun `无余额返回null`() {
        assertEquals(null, SmsParser.extractBalanceFen("【交通银行】您的信用卡已还款3,000.00元，交易成功。"))
    }
}
