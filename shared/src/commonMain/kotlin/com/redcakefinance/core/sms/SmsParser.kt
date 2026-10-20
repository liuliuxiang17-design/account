package com.redcakefinance.core.sms

import com.redcakefinance.core.model.Direction
import kotlin.math.roundToLong

/**
 * 短信解析引擎（余额锚定版）：以「余额」为可靠基准取交易金额、判收支方向，
 * 中文大写兜底；纯逻辑，不依赖平台 API，可跨平台单测。
 *
 * 典型格式（示例数据均为虚构）：
 * `尾号6812卡8月24日16:07支出(消费支付宝-张三)98元，余额12,345.67元。【工商银行】`
 *  - 金额 = 方向锚点之后、余额之前的第一笔「数字+元」（手续费等后续金额不误取）；
 *  - 余额 = 「余额/当前余额/可用余额…」后的数字，独立提取，绝不当作交易金额；
 *  - 方向 = 三组锚点（退款/支出/收入）中最先出现者决定，而不是无条件「收入优先」。
 *
 * 输入：短信正文（body）、发件人（sender）。
 * 输出：ParsedSms（商户/金额分/方向/置信度）或 null；extractBalanceFen 输出余额（分）。
 */
object SmsParser {
    /** 「数字+元」金额（允许千分位逗号与 1~2 位小数）。 */
    private val AMOUNT_YUAN_RE = Regex("""(\d[\d,]*(?:\.\d{1,2})?)\s*元""")

    /** 余额表达式：引导词 + 可选冒号 + 可选币种 + 数字 + 元（含「可用余额」「当前余额」等变体）。 */
    private val BALANCE_RE = Regex(
        "(?:账户余额|可用余额|当前余额|卡余额|活期余额|余额|结余)" +
            "\\s*[为是:：]?\\s*(?:人民币|RMB|rmb|￥|¥)?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)\\s*元",
    )

    private val AMOUNT_ALT_RE = Regex("""(?:￥|¥|RMB|rmb|人民币)\s*(\d[\d,]*(?:\.\d{1,2})?)""")
    private val CN_AMOUNT_RE = Regex("""([零壹贰叁肆伍陆柒捌玖拾佰仟万亿]+)\s*元""")

    /** 验证码/系统通知类：无余额时永远不是流水。 */
    private val HARD_NOISE = listOf(
        Regex("验证码|校验码|动态码|动态密码|安全码|取件码|取货码"),
        Regex("(?:登录|注册|激活)(?:验证|码)"),
    )

    /** 营销词：仅在没有余额证据（无「余额」字样）时否决，避免把「支付立减5元」当流水。 */
    private val PROMO_NOISE = listOf(
        Regex("满\\d+(?:\\.\\d+)?元?减"), Regex("立减"), Regex("优惠券"), Regex("秒杀"),
        Regex("大促"), Regex("抢购"), Regex("中奖"), Regex("恭喜"), Regex("抽签"),
        Regex("免费领"), Regex("兑换"), Regex("积分"), Regex("提额"), Regex("推荐办卡"),
    )

    /** 支出锚点（按语义强度排列，仅用于定位首次出现位置）。 */
    private val EXPENSE_ANCHORS = listOf(
        "支出", "消费", "付款", "扣款", "扣费", "转出", "还款", "支付", "代扣", "缴费", "充值", "刷卡", "取现",
    )
    fun expenseWords(): List<String> = EXPENSE_ANCHORS

    /** 收入锚点。 */
    private val INCOME_ANCHORS = listOf(
        "收入", "存入", "到账", "转入", "入账", "工资", "收款", "收到", "利息", "结息", "退款入账",
    )
    fun incomeWords(): List<String> = INCOME_ANCHORS

    /** 退款锚点（余额增加，与收入同向但单独标注）。 */
    private val REFUND_ANCHORS = listOf("退款", "返还", "冲正", "退回", "退货")
    fun refundWords(): List<String> = REFUND_ANCHORS

    /** 「转账」歧义：转入（收到/入账/来自）与转出（向 X 转账/转给）。 */
    private val TRANSFER_IN_RE = Regex("收到.*转账|来自.*转账|转账.*(?:入账|到账)|入账.*转账|转入")
    private val TRANSFER_OUT_RE = Regex("向[^，。；\\s]+转账|转账(?:给|到)|转出|转帐支出")

    private val MERCHANT_GUIDES = listOf(
        Regex("""收款方[:：]?\s*([^，,。.;；\s]+)"""),
        Regex("""商户[:：]\s*([^，,。.;；\s]+)"""),
        Regex("""项目[:：]?\s*([^，,。.;；\s]+)"""),
        Regex("""摘要[:：]\s*([^，,。.;；\s]+)"""),
        Regex("""付款方[:：]?\s*([^，,。.;；\s]+)"""),
        Regex("""原商户[:：]\s*([^，,。.;；\s]+)"""),
        Regex("""商家[:：]\s*([^，,。.;；\s]+)"""),
    )

    /** 括号描述里需要剥掉的动作前缀（渠道前缀如 支付宝-/财付通- 保留）。 */
    private val DESC_PREFIXES = listOf("快捷支付", "网上支付", "消费", "付款", "支付", "转账", "代扣", "缴费", "收入", "存入", "退款")

    /** 渠道名：描述以它们开头时一律不剥前缀（否则「支付宝-」会被「支付」剥成「宝-」）。 */
    private val CHANNEL_NAMES = listOf("支付宝", "财付通", "微信支付", "网银在线", "银联", "云闪付")

    private val CN_NUM = mapOf(
        '零' to 0L, '壹' to 1L, '贰' to 2L, '叁' to 3L, '肆' to 4L,
        '伍' to 5L, '陆' to 6L, '柒' to 7L, '捌' to 8L, '玖' to 9L,
    )

    /** 解析单条短信；返回 null 表示应忽略（无金额、验证码、营销等）。 */
    fun parse(body: String, sender: String = ""): ParsedSms? {
        val balanceFen = extractBalanceFen(body)
        // 验证码/通知类：即使含金额也不入账（带余额的银行流水不受影响）
        if (balanceFen == null && HARD_NOISE.any { it.containsMatchIn(body) }) return null
        // 营销短信（无余额证据时否决，如「支付立减5元」「满100元减20元」）
        if (balanceFen == null && PROMO_NOISE.any { it.containsMatchIn(body) }) return null
        val amountFen = extractAmountFen(body) ?: return null
        // 必须有可信锚点：含余额、含收支/转账关键词，或带￥/RMB/人民币币种标记
        // （纯数字的广告短信没有这些，会被挡掉）
        val hasAnchor = balanceFen != null ||
            firstAnchorIndex(body) >= 0 ||
            body.contains("转账") ||
            AMOUNT_ALT_RE.containsMatchIn(body)
        if (!hasAnchor) return null
        val direction = detectDirection(body)
        val merchant = extractMerchant(body) ?: sender.ifBlank { return null }
        // 置信度：有余额佐证的流水最可信；仅靠关键词的次之
        val confidence = when {
            balanceFen != null -> 0.95
            firstAnchorIndex(body) >= 0 -> 0.7
            else -> 0.5
        }
        return ParsedSms(merchant = merchant, amountFen = amountFen, direction = direction, confidence = confidence)
    }

    /**
     * 金额提取（分）：以「余额」为分界，取方向锚点之后、余额之前的第一笔「数字+元」。
     * 排除余额本身、手续费等后续金额；无「元」时降级到「锚点后第一个非日期数字」。
     */
    fun extractAmountFen(body: String): Long? {
        val balance = BALANCE_RE.find(body)
        val balanceStart = balance?.range?.first ?: body.length
        val anchor = firstAnchorIndex(body)
        // 排除被余额表达式自身覆盖的那笔金额
        val outsideBalance = AMOUNT_YUAN_RE.findAll(body).filter { m ->
            balance == null || m.range.last < balance.range.first || m.range.first > balance.range.last
        }.toList()

        fun pick(list: List<MatchResult>): Long? {
            list.forEach { m ->
                val fen = fenOf(m.groupValues[1])
                if (fen != null && fen > 0L) return fen
            }
            return null
        }

        val beforeBalance = outsideBalance.filter { it.range.first < balanceStart }
        // 1) 标准流水：锚点之后、余额之前的第一笔（手续费在后面也不会被误取）
        pick(beforeBalance.filter { anchor < 0 || it.range.first > anchor })?.let { return it }
        // 2) 锚点前只有一笔（描述里带金额）时，取余额之前最靠近余额的那笔
        pick(beforeBalance.reversed())?.let { return it }
        // 3) 余额在前、金额在后
        pick(outsideBalance.filter { it.range.first > balanceStart })?.let { return it }

        // 4) 无「元」单位兜底：锚点后的第一个非日期/时间数字
        if (anchor >= 0) {
            val tail = body.substring(anchor)
            val numRe = Regex("""(\d[\d,]*(?:\.\d{1,2})?)(?![\d.,])""")
            numRe.findAll(tail).forEach { m ->
                val after = tail.getOrNull(m.range.last + 1)
                if (after == '月' || after == '日' || after == '年' || after == '时' ||
                    after == '分' || after == '秒' || after == '期' || after == '%'
                ) {
                    return@forEach
                }
                val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@forEach
                if (v.isFinite() && v > 0.0) return (v * 100).roundToLong()
            }
        }
        // 5) ￥/RMB 变体（无「元」字样）
        AMOUNT_ALT_RE.find(body)?.let { m ->
            val v = m.groupValues[1].replace(",", "").toDoubleOrNull()
            if (v != null && v.isFinite() && v > 0.0) return (v * 100).roundToLong()
        }
        // 6) 中文大写兜底
        val cn = CN_AMOUNT_RE.find(body) ?: return null
        return chineseToFen(cn.groupValues[1])
    }

    /**
     * 收支方向三态：退款 / 支出 / 收入三组锚点中**最先出现**者胜。
     * 必须按位置比较，不能「收入词优先」：`你已支付19.9元，收款方：瑞幸` 这类的收入词在后面，
     * 优先判收入就会把支出记成收入。
     * 判方向前还要剔除【…】品牌签名与括号描述 —— 否则 `【支付宝】…退款` 里的「支付」会抢判。
     */
    fun detectDirection(body: String): String {
        val text = stripBrandAndBrackets(body)
        val r = firstIndexOf(text, REFUND_ANCHORS)
        val e = firstIndexOf(text, EXPENSE_ANCHORS)
        val i = firstIndexOf(text, INCOME_ANCHORS)
        val min = listOf(r, e, i).filter { it >= 0 }.minOrNull()
            ?: return if (body.contains("转账")) {
                if (TRANSFER_IN_RE.containsMatchIn(body)) Direction.INCOME else Direction.EXPENSE
            } else {
                Direction.EXPENSE
            }
        return when (min) {
            r -> Direction.REFUND
            e -> Direction.EXPENSE
            else -> Direction.INCOME
        }
    }

    /** 去掉【…】品牌签名与 (…)（…）括号描述（换成空格，不改变其余文字顺序）。 */
    private fun stripBrandAndBrackets(body: String): String =
        body.replace(Regex("【[^】]*】"), " ")
            .replace(Regex("[(（][^)）]*[)）]"), " ")

    /**
     * 商户提取：引导词优先；其次取「方向锚点之后的第一个括号描述」（去掉消费/付款等动作前缀）；
     * 再次「向 X 支付」「在 X 消费」「收入 X」；都没有则由调用方回落为发件人。
     */
    fun extractMerchant(body: String): String? {
        for (re in MERCHANT_GUIDES) {
            val v = re.find(body)?.groupValues?.get(1)?.trim() ?: continue
            if (v.isNotEmpty()) return v
        }
        val anchor = firstAnchorIndex(body)
        val parens = Regex("""[（(]([^（()）]{1,40})[)）]""").findAll(body).toList()
        // 优先方向锚点之后的括号（前面的括号常是尾号/账号说明）
        val ordered = parens.filter { anchor < 0 || it.range.first > anchor } + parens.filter { anchor >= 0 && it.range.first <= anchor }
        ordered.forEach { m ->
            val v = cleanDesc(m.groupValues[1])
            if (v.isNotEmpty()) return v
        }
        Regex("""收入([^0-9，,。.;；\s]+)""").find(body)?.let { return it.groupValues[1].trim() }
        Regex("""向([^，,。.;；\s]+)支付""").find(body)?.let { return it.groupValues[1].trim() }
        Regex("""在([^，,。.;；\s]+)消费""").find(body)?.let { return it.groupValues[1].trim() }
        return null
    }

    /** 余额提取（分）：支持「余额/当前余额/可用余额/结余」+ 可选冒号 + 可选币种。 */
    fun extractBalanceFen(body: String): Long? {
        val m = BALANCE_RE.find(body) ?: return null
        val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (!v.isFinite() || v < 0.0) return null
        return (v * 100).roundToLong()
    }

    /**
     * 括号描述清洗：只剥掉**一个**动作前缀与首尾标点，纯动作词（如「消费」）视为无商户。
     *
     * 只剥一个，是因为「消费支付宝-某商户」剥掉「消费」后还剩「支付宝-某商户」——
     * 若继续按列表剥「支付」，就会把渠道名「支付宝」剥成「宝-某商户」。
     */
    private fun cleanDesc(raw: String): String {
        var v = raw.trim().trim('，', ',', '。', '.', '；', ';', '：', ':')
        val isChannel = CHANNEL_NAMES.any { v.startsWith(it) }
        if (!isChannel) {
            val prefix = DESC_PREFIXES.firstOrNull { v.length > it.length && v.startsWith(it) }
            if (prefix != null) v = v.removePrefix(prefix).trim()
        }
        // 仅剩动作词 → 无有效商户名
        if (v.isEmpty() || DESC_PREFIXES.any { it == v }) return ""
        return v
    }

    /** 三组锚点中最先出现的下标；都不存在返回 -1。 */
    private fun firstAnchorIndex(body: String): Int {
        val idx = listOf(
            firstIndexOf(body, EXPENSE_ANCHORS),
            firstIndexOf(body, INCOME_ANCHORS),
            firstIndexOf(body, REFUND_ANCHORS),
        ).filter { it >= 0 }
        return idx.minOrNull() ?: -1
    }

    /** 一组关键词在正文中最早出现的下标；都不存在返回 -1。 */
    private fun firstIndexOf(body: String, words: List<String>): Int {
        var min = -1
        words.forEach { w ->
            val i = body.indexOf(w)
            if (i >= 0 && (min < 0 || i < min)) min = i
        }
        return min
    }

    /** 「数字串」→ 分（去千分位、允许 1~2 位小数）；非法/非正返回 null。 */
    private fun fenOf(raw: String): Long? {
        val v = raw.replace(",", "").toDoubleOrNull() ?: return null
        if (!v.isFinite() || v <= 0.0) return null
        return (v * 100).roundToLong()
    }

    /** 中文大写金额 → 分。 */
    private fun chineseToFen(s: String): Long? {
        var total = 0L; var section = 0L; var num = 0L
        for (ch in s) {
            when (ch) {
                '零' -> {}
                '拾' -> { section += (if (num == 0L) 1L else num) * 10L; num = 0L }
                '佰' -> { section += (if (num == 0L) 1L else num) * 100L; num = 0L }
                '仟' -> { section += (if (num == 0L) 1L else num) * 1000L; num = 0L }
                '万' -> { total += (section + num) * 10000L; section = 0L; num = 0L }
                '亿' -> { total = (total + section + num) * 100000000L; section = 0L; num = 0L }
                else -> num = CN_NUM[ch] ?: return null
            }
        }
        return (total + section + num) * 100L
    }
}
