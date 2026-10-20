package com.redcakefinance.core.sms

import com.redcakefinance.core.format.TimeFormat

/**
 * 短信里的附加信息：发件方签名与正文自带的时间。
 *
 * 这两样在 Android 上由系统短信库直接给出，iOS 侧只能从正文里推断：
 *  - 发件方：取 `【…】` 里的机构名（如「工商银行」），用作账户分组键；
 *  - 时间：取「8月24日16:07」这类写法，缺失或非法时回落到「现在」。
 */
object SmsMeta {
    /** 未识别出机构时的账户名。 */
    const val UNKNOWN_SENDER = "未知来源"

    private val SIGNATURE_RE = Regex("【([^】]{2,20})】")

    private val DATE_TIME_RE = Regex(
        "(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2})[:：](\\d{2})",
    )

    /**
     * 从 `【…】` 提取机构名；没有则返回 null。
     * 直接复用 `SmsParser` 已剔除的品牌签名格式，不做额外猜测。
     */
    fun extractSender(body: String): String? {
        val raw = SIGNATURE_RE.find(body)?.groupValues?.get(1)?.trim() ?: return null
        if (raw.isEmpty()) return null
        return raw
    }

    /**
     * 解析正文里的「X月X日 HH:mm」为 epoch millis。
     *
     * 没写年份时按「最近一次发生的该日期」处理：先用当年，若落在未来（超过一天）
     * 则回退一年。这样 12 月的短信在 1 月导入也不会跑到来年。
     *
     * @param nowMillis 当前时间，用于补年份与判定未来
     * @return 解析结果；正文没有可识别的时间时返回 [nowMillis]
     */
    fun parseOccurredAt(body: String, nowMillis: Long): Long {
        val m = DATE_TIME_RE.find(body) ?: return nowMillis
        val explicitYear = m.groupValues[1].toIntOrNull()
        val month = m.groupValues[2].toIntOrNull() ?: return nowMillis
        val day = m.groupValues[3].toIntOrNull() ?: return nowMillis
        val hour = m.groupValues[4].toIntOrNull() ?: return nowMillis
        val minute = m.groupValues[5].toIntOrNull() ?: return nowMillis
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) return nowMillis

        // 本月 1 号减一天 = 上个月的最后一天，用来判断「回退一年」的边界
        val currentYear = TimeFormat.format(nowMillis, "yyyy").toIntOrNull() ?: return nowMillis
        val years = if (explicitYear != null) listOf(explicitYear) else listOf(currentYear, currentYear - 1)

        var fallback: Long? = null
        for (year in years) {
            val text = "${year}-${pad2(month)}-${pad2(day)} ${pad2(hour)}:${pad2(minute)}"
            val t = TimeFormat.parse(text, "yyyy-MM-dd HH:mm") ?: continue
            if (t <= nowMillis + DAY_MS) return t
            if (fallback == null) fallback = t
        }
        // 显式写了年份但落在未来（如跨时区误差）时，仍按解析结果处理
        return fallback ?: nowMillis
    }

    private fun pad2(v: Int): String = v.toString().padStart(2, '0')

    private const val DAY_MS = 24L * 3600L * 1000L
}
