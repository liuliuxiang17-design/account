package com.redcakefinance.core.analysis

import com.redcakefinance.core.model.BalanceCheckpoint
import com.redcakefinance.core.model.Direction
import com.redcakefinance.core.model.Transaction
import kotlin.math.abs

/**
 * 合计余额曲线上的一个点：同一时刻各账户已知余额之和（分）。
 */
data class BalancePoint(val capturedAt: Long, val balanceFen: Long)

/** 同一发件人相邻两次余额快照之间的真实变动。 */
data class BalanceStep(
    val sender: String,
    val fromTime: Long,
    val toTime: Long,
    val fromBalanceFen: Long,
    val toBalanceFen: Long,
) {
    val deltaFen: Long get() = toBalanceFen - fromBalanceFen
}

/**
 * 区间对账缺口：余额差 − 该区间流水净额（同一发件人，区间半开 (fromTime, toTime]）。
 *
 * [flowNetFen] 是**归因调整后**的净额：见 [BalanceTruth.reconcile]。
 */
data class BalanceGap(
    val sender: String,
    val fromTime: Long,
    val toTime: Long,
    val deltaFen: Long,
    val flowNetFen: Long,
    /**
     * 归因判定为「与相邻区间等额反向」的伪差，已互相抵消。
     * 这类区间的每一段单独看都对不上，合起来却分毫不差——典型成因是
     * 同一笔流水被时间戳放到了隔壁区间，或中间插了一条同值余额快照。
     */
    val canceled: Boolean = false,
    /** 从相邻区间借入的流水 id（仅用于展示，钱没有凭空出现）。 */
    val movedIn: List<Long> = emptyList(),
    /** 借给相邻区间的流水 id。 */
    val movedOut: List<Long> = emptyList(),
) {
    val gapFen: Long get() = deltaFen - flowNetFen
    /** 缺口方向：>0 表示「余额多出来的钱」= 少记了收入；<0 表示少记了支出。 */
    val missingDirection: String get() = if (gapFen > 0) Direction.INCOME else Direction.EXPENSE
}

/** 一一对应校验里，单笔流水的去向。 */
enum class FlowRole {
    /** 被本区间认领（金额连同其它流水一起解释了本区间余额差）。 */
    CLAIMED,
    /** 从相邻区间借入：它本该属于本区间。 */
    MOVED_IN,
    /** 借给相邻区间：它不该属于本区间。 */
    MOVED_OUT,
}

/** 单笔流水在区间匹配中的归属。 */
data class FlowMatch(
    val txnId: Long,
    val signedFen: Long,
    val role: FlowRole,
)

/** 区间的匹配结论（逐笔一一对应的结果）。 */
enum class MatchKind {
    /** 区间内恰好一笔，金额与余额差分毫不差。 */
    EXACT_1TO1,
    /** 区间内多笔，净额与余额差吻合。 */
    SUM_MANY,
    /** 余额差为零且没有流水。 */
    ZERO,
    /** 靠与相邻区间交换一笔流水才吻合（原先按时间戳放错了地方）。 */
    RESOLVED_BY_MOVE,
    /** 与相邻区间等额反向但挪不动单笔（多笔凑成），判为边界伪差。 */
    CANCELED,
    /** 解释不了：真缺口。 */
    UNEXPLAINED,
}

/** 一个区间的完整校验结果：余额差、认领到的流水、结论与残差。 */
data class IntervalMatch(
    val sender: String,
    val fromTime: Long,
    val toTime: Long,
    val deltaFen: Long,
    val flowNetFen: Long,
    val kind: MatchKind,
    /** 本区间最终认领的流水（含借入的）。 */
    val flows: List<FlowMatch>,
) {
    val gapFen: Long get() = deltaFen - flowNetFen
    /** 缺口方向：>0 表示少记了收入，<0 表示少记了支出。 */
    val missingDirection: String get() = if (gapFen > 0) Direction.INCOME else Direction.EXPENSE

    /** 转成对账视图（界面与补录用的结构）。 */
    fun toGap(): BalanceGap = BalanceGap(
        sender = sender,
        fromTime = fromTime,
        toTime = toTime,
        deltaFen = deltaFen,
        flowNetFen = flowNetFen,
        canceled = kind == MatchKind.CANCELED,
        movedIn = flows.filter { it.role == FlowRole.MOVED_IN }.map { it.txnId },
        movedOut = flows.filter { it.role == FlowRole.MOVED_OUT }.map { it.txnId },
    )
}

/** 单个发件人的校验结果（并行计算的最小单位）。 */
class SenderMatch(
    val intervals: List<IntervalMatch>,
    /** 时间上不落在任何区间里的流水：第一条快照之前，或晚于最后一条快照。 */
    val orphans: List<FlowMatch>,
)

/** 全量校验结果：逐区间的匹配明细 + 无主流水。 */
class Reconciliation(
    val intervals: List<IntervalMatch>,
    val orphans: List<FlowMatch>,
) {
    /** 解释不了的区间（真缺口，需要提示或补录）。 */
    val unexplained: List<IntervalMatch> get() = intervals.filter { it.kind == MatchKind.UNEXPLAINED }

    /** 逐笔精确对应上的区间数（1:1 与求和两种都算对得上）。 */
    val matchedCount: Int
        get() = intervals.count {
            it.kind == MatchKind.EXACT_1TO1 || it.kind == MatchKind.SUM_MANY ||
                it.kind == MatchKind.ZERO || it.kind == MatchKind.RESOLVED_BY_MOVE
        }

    /** 转成对账视图（含 gap 为 0 的区间）。 */
    fun toGaps(): List<BalanceGap> = intervals.map { it.toGap() }
}

/**
 * 余额真值引擎：把「短信里的余额」当作唯一可靠基准，推导余额曲线、净额直方图与对账缺口。
 * 这是全工程「余额口径」的唯一权威说明，其它处只引用、不重复。
 *
 * 为什么不用解析出的流水金额汇总：短信会漏读、正则会取错（把手续费或余额当成交易额），
 * 而余额是银行给的数字——相邻两次快照之差就是真实净变动，不需要任何推测。
 *
 * 约定：区间一律半开 (fromTime, toTime]；对账只比较同一发件人（不同账户的余额交错，混算必错）。
 */
object BalanceTruth {
    /**
     * 一次去重 + 排序后的时间线。
     * 一次刷新里要用到「曲线 / 净额分桶 / 区间对账」三份结果，共用同一份时间线即可，
     * 不必每样都重新排序分组（快照多时这是主要开销）。
     */
    class Timeline internal constructor(
        /** 去重并按 (capturedAt, sender) 升序的快照。 */
        val checkpoints: List<BalanceCheckpoint>,
        /** 相邻快照的真实变动，按 toTime 升序。 */
        val steps: List<BalanceStep>,
    ) {
        /** 按发件人切分的区间（各自按 toTime 升序）：对账逐账户独立计算，也是并行化的切分点。 */
        val stepsBySender: Map<String, List<BalanceStep>> = steps.groupBy { it.sender }
            .mapValues { (_, list) -> list.sortedBy { it.toTime } }
    }

    /** 流水净额符号：支出为负，收入/退款为正。 */
    fun signedFen(t: Transaction): Long =
        if (t.direction == Direction.EXPENSE) -t.amountFen else t.amountFen

    /**
     * 去重：同发件人同毫秒只保留一条（后写入者为准）。
     * 只跟「每人最新一条」比较时，重复扫描同一批短信会把同一快照反复插入。
     */
    fun dedupe(checkpoints: List<BalanceCheckpoint>): List<BalanceCheckpoint> =
        checkpoints
            .sortedBy { it.capturedAt }
            .groupBy { it.sender to it.capturedAt }
            .map { (key, group) -> group.last().copy(sender = key.first, capturedAt = key.second) }
            .sortedWith(compareBy({ it.capturedAt }, { it.sender }))

    /** 构建时间线（去重 + 求相邻差），供下面所有按时间线的重载共用。 */
    fun timeline(checkpoints: List<BalanceCheckpoint>): Timeline {
        val sorted = dedupe(checkpoints)
        val steps = sorted.groupBy { it.sender }.flatMap { (sender, list) ->
            (1 until list.size).mapNotNull { i ->
                val a = list[i - 1]
                val b = list[i]
                if (b.capturedAt <= a.capturedAt) {
                    null
                } else {
                    BalanceStep(
                        sender = sender,
                        fromTime = a.capturedAt,
                        toTime = b.capturedAt,
                        fromBalanceFen = a.balanceFen,
                        toBalanceFen = b.balanceFen,
                    )
                }
            }
        }.sortedBy { it.toTime }
        return Timeline(sorted, steps)
    }

    /**
     * 合计余额曲线：按时间推进，每个发件人取其最新余额后求和（同一时刻只出一个点）。
     * 未出现过的发件人从它的第一条快照起才计入，曲线上表现为一个台阶（真实「新账户接入」）。
     */
    fun totalSeries(timeline: Timeline): List<BalancePoint> {
        val sorted = timeline.checkpoints
        if (sorted.isEmpty()) return emptyList()
        val latest = HashMap<String, Long>()
        val out = ArrayList<BalancePoint>(sorted.size)
        var lastTime = Long.MIN_VALUE
        var lastTotal = Long.MIN_VALUE
        sorted.forEach { cp ->
            latest[cp.sender] = cp.balanceFen
            if (cp.capturedAt != lastTime) {
                val total = latest.values.sum()
                if (total != lastTotal || out.isEmpty()) {
                    out.add(BalancePoint(cp.capturedAt, total))
                    lastTotal = total
                }
                lastTime = cp.capturedAt
            } else {
                // 同一毫秒的多个发件人：合并成一个点
                val total = latest.values.sum()
                if (out.isNotEmpty() && total != out.last().balanceFen) {
                    out[out.size - 1] = BalancePoint(cp.capturedAt, total)
                }
                lastTotal = total
            }
        }
        return out
    }

    /** 合计余额曲线（传入原始快照列表的便捷重载）。 */
    fun totalSeries(checkpoints: List<BalanceCheckpoint>): List<BalancePoint> =
        totalSeries(timeline(checkpoints))

    /** 相邻快照的真实变动（按发件人分组，各自求差）。 */
    fun steps(checkpoints: List<BalanceCheckpoint>): List<BalanceStep> = timeline(checkpoints).steps

    /**
     * 区间净额（直方图数据源）：把每一步余额变动记到「后一条快照」所在的时间桶。
     * @param from 起始毫秒（含）
     * @param to 结束毫秒（不含；Long.MAX_VALUE 表示不限）
     * @param labelOf 把毫秒映射成桶标签（如 "2026-08"）
     * @return 桶标签 → 净额（分），按标签升序；无数据返回空列表
     */
    fun netByBucket(
        timeline: Timeline,
        from: Long,
        to: Long,
        labelOf: (Long) -> String,
    ): List<Pair<String, Long>> =
        timeline.steps
            .filter { it.toTime >= from && it.toTime < to && it.deltaFen != 0L }
            .groupBy { labelOf(it.toTime) }
            .map { (label, list) -> label to list.sumOf { it.deltaFen } }
            .sortedBy { it.first }

    /** 区间净额（传入原始快照列表的便捷重载）。 */
    fun netByBucket(
        checkpoints: List<BalanceCheckpoint>,
        from: Long,
        to: Long,
        labelOf: (Long) -> String,
    ): List<Pair<String, Long>> = netByBucket(timeline(checkpoints), from, to, labelOf)

    /**
     * 区间对账：每个余额区间内该发件人的流水净额 vs 余额差。
     * 必须按发件人分组：不同账户的余额快照交错，跨发件人汇总流水净额必然算错。
     *
     * @param flows 全部已知流水（已导入 + 待导入短信解析结果，避免重复补录）
     * @return 全部区间的对账结果（含 gap 为 0 的），按区间结束时间升序
     */
    fun intervals(timeline: Timeline, flows: List<Transaction>): List<BalanceGap> {
        if (timeline.checkpoints.isEmpty()) return emptyList()
        val bySender = flows.groupBy { it.sender ?: "" }
        return timeline.steps.map { step ->
            val inRange = bySender[step.sender].orEmpty().filter {
                it.occurredAt > step.fromTime && it.occurredAt <= step.toTime
            }
            BalanceGap(
                sender = step.sender,
                fromTime = step.fromTime,
                toTime = step.toTime,
                deltaFen = step.deltaFen,
                flowNetFen = inRange.sumOf { signedFen(it) },
            )
        }
    }

    /** 区间对账（传入原始快照列表的便捷重载）。 */
    fun intervals(checkpoints: List<BalanceCheckpoint>, flows: List<Transaction>): List<BalanceGap> =
        intervals(timeline(checkpoints), flows)

    /**
     * 区间对账缺口：只返回对不上的区间。
     * @param toleranceFen 容差：|缺口| ≤ 容差视为对得上（默认 0，即分毫不差）
     */
    fun gaps(
        timeline: Timeline,
        flows: List<Transaction>,
        toleranceFen: Long = 0L,
    ): List<BalanceGap> = reconcile(timeline, flows)
        .filter { !it.canceled && abs(it.gapFen) > toleranceFen }

    /** 区间对账缺口（传入原始快照列表的便捷重载）。 */
    fun gaps(
        checkpoints: List<BalanceCheckpoint>,
        flows: List<Transaction>,
        toleranceFen: Long = 0L,
    ): List<BalanceGap> = gaps(timeline(checkpoints), flows, toleranceFen)

    /**
     * 一一对应校验（单发件人）：把该发件人的流水与它的余额区间逐段配对。
     *
     * 复杂度 O(区间 + 流水)：流水按时间升序、区间按 toTime 升序且首尾相接，
     * 因此用双指针归并一次就能分桶，不再"每个区间扫一遍全部流水"。
     * 发件人之间互不影响，调用方可按发件人并行（见 [match]）。
     *
     * 匹配分档（见 [MatchKind]）：1:1 精确 / 多笔求和 / 空区间 / 靠相邻交换解决 / 边界伪差 / 真缺口。
     * 时间上不落在任何区间里的流水归入 [SenderMatch.orphans]，**不再静默通过**——
     * 那通常是重复导入，或早于第一条余额快照的历史流水。
     *
     * @param ordered 该发件人的区间（按 toTime 升序）
     * @param flows 该发件人的全部已知流水
     */
    fun matchSender(sender: String, ordered: List<BalanceStep>, flows: List<Transaction>): SenderMatch {
        if (ordered.isEmpty()) return SenderMatch(emptyList(), emptyList())
        val n = ordered.size
        val buckets = Array(n) { mutableListOf<Transaction>() }
        val originalIds = Array(n) { emptySet<Long>() }
        val orphans = mutableListOf<FlowMatch>()

        // 双指针归并分桶：f.occurredAt ∈ (fromTime, toTime]
        var idx = 0
        flows.sortedBy { it.occurredAt }.forEach { f ->
            while (idx < n && f.occurredAt > ordered[idx].toTime) idx++
            if (idx >= n) {
                orphans += FlowMatch(f.id, signedFen(f), FlowRole.CLAIMED) // 晚于最后一条快照
                return@forEach
            }
            if (f.occurredAt > ordered[idx].fromTime) {
                buckets[idx].add(f)
            } else {
                orphans += FlowMatch(f.id, signedFen(f), FlowRole.CLAIMED) // 早于第一条快照
            }
        }
        for (i in 0 until n) originalIds[i] = buckets[i].mapTo(HashSet()) { it.id }

        fun net(i: Int): Long = buckets[i].sumOf { signedFen(it) }
        fun residual(i: Int): Long = ordered[i].deltaFen - net(i)

        // 相邻纠错：两边残差等额反向时，把能解释残差的那一笔挪到对面（多轮直到稳定）
        var changed = true
        var guard = 0
        while (changed && guard++ <= n) {
            changed = false
            for (i in 0 until n - 1) {
                val ri = residual(i)
                val rj = residual(i + 1)
                if (ri == 0L || rj == 0L || ri + rj != 0L) continue
                val fromI = buckets[i].firstOrNull { signedFen(it) == -ri }
                if (fromI != null) {
                    buckets[i].remove(fromI)
                    buckets[i + 1].add(fromI)
                    changed = true
                    continue
                }
                val fromJ = buckets[i + 1].firstOrNull { signedFen(it) == rj }
                if (fromJ != null) {
                    buckets[i + 1].remove(fromJ)
                    buckets[i].add(fromJ)
                    changed = true
                }
            }
        }

        val finalResidual = LongArray(n) { residual(it) }
        val intervalMatches = ordered.mapIndexed { i, step ->
            val ids = buckets[i].mapTo(HashSet()) { it.id }
            val movedIn = ids.any { it !in originalIds[i] }
            val movedOut = originalIds[i].any { it !in ids }
            val kind = when {
                movedIn || movedOut -> MatchKind.RESOLVED_BY_MOVE
                finalResidual[i] == 0L && ids.isEmpty() && step.deltaFen == 0L -> MatchKind.ZERO
                finalResidual[i] == 0L && ids.size == 1 -> MatchKind.EXACT_1TO1
                finalResidual[i] == 0L -> MatchKind.SUM_MANY
                // 挪不动的等额反向残差（由多笔小额凑成）仍属边界伪差
                (i > 0 && finalResidual[i - 1] == -finalResidual[i]) ||
                    (i < n - 1 && finalResidual[i + 1] == -finalResidual[i]) -> MatchKind.CANCELED
                else -> MatchKind.UNEXPLAINED
            }
            val flowMatches = buckets[i].map { f ->
                FlowMatch(
                    txnId = f.id,
                    signedFen = signedFen(f),
                    role = when {
                        f.id !in originalIds[i] -> FlowRole.MOVED_IN
                        else -> FlowRole.CLAIMED
                    },
                )
            } + originalIds[i].filter { it !in ids }.map { FlowMatch(it, 0L, FlowRole.MOVED_OUT) }

            IntervalMatch(
                sender = sender,
                fromTime = step.fromTime,
                toTime = step.toTime,
                deltaFen = step.deltaFen,
                flowNetFen = net(i),
                kind = kind,
                flows = flowMatches,
            )
        }
        return SenderMatch(intervalMatches, orphans)
    }

    /**
     * 一一对应校验（全部发件人，顺序执行）。
     * 需要并行时由调用方按 [Timeline.stepsBySender] 切分，分别调用 [matchSender]。
     */
    fun match(timeline: Timeline, flows: List<Transaction>): Reconciliation {
        if (timeline.checkpoints.isEmpty()) return Reconciliation(emptyList(), emptyList())
        val flowsBySender = flows.groupBy { it.sender ?: "" }
        val intervals = ArrayList<IntervalMatch>(timeline.steps.size)
        val orphans = ArrayList<FlowMatch>()
        timeline.stepsBySender.toSortedMap().forEach { (sender, steps) ->
            val r = matchSender(sender, steps, flowsBySender[sender].orEmpty())
            intervals += r.intervals
            orphans += r.orphans
        }
        return Reconciliation(
            intervals.sortedWith(compareBy({ it.sender }, { it.toTime })),
            orphans,
        )
    }

    /** 一一对应校验（传入原始快照列表的便捷重载）。 */
    fun match(checkpoints: List<BalanceCheckpoint>, flows: List<Transaction>): Reconciliation =
        match(timeline(checkpoints), flows)

    /**
     * 区间对账（对外兼容视图）：在 [match] 的结果上给出「余额差 / 流水净额 / 缺口」。
     *
     * 归因规则由 [matchSender] 负责：相邻区间等额反向的残差先互相抵消
     * （流水被时间戳放错区间、或区间被同值快照切碎），剩下的残差才是真缺口。
     *
     * @return 全部区间（含已抵消的），按发件人与区间结束时间排序
     */
    fun reconcile(timeline: Timeline, flows: List<Transaction>): List<BalanceGap> =
        match(timeline, flows).toGaps()

    /** 区间对账（传入原始快照列表的便捷重载）。 */
    fun reconcile(checkpoints: List<BalanceCheckpoint>, flows: List<Transaction>): List<BalanceGap> =
        reconcile(timeline(checkpoints), flows)

    /**
     * 某条流水的时间点是否落在「有余额佐证」的区间里（用于交叉核验广告/伪造短信）。
     * 在发件人的区间序列上二分查找，避免每条短信都扫一遍全部区间。
     *
     * @return 该时间点所属区间的余额差；找不到返回 null
     */
    fun deltaAround(timeline: Timeline, sender: String, time: Long): Long? {
        val steps = timeline.stepsBySender[sender] ?: return null
        var lo = 0
        var hi = steps.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = steps[mid]
            when {
                time <= s.fromTime -> hi = mid - 1
                time > s.toTime -> lo = mid + 1
                else -> return s.deltaFen
            }
        }
        return null
    }

    /** 同 [deltaAround]，传入原始快照列表的便捷重载。 */
    fun deltaAround(checkpoints: List<BalanceCheckpoint>, sender: String, time: Long): Long? =
        deltaAround(timeline(checkpoints), sender, time)
}
