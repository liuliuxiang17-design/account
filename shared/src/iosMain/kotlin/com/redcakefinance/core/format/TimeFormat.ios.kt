package com.redcakefinance.core.format

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

/**
 * iOS 侧时间格式化实现。
 *
 * NSDate 的原生时间基准是 2001-01-01 00:00:00 UTC（reference date），不是 Unix epoch；
 * 两者相差 [REFERENCE_DATE_OFFSET_SEC] 秒。这里只在 reference date 上做加减，
 * 不依赖 `dateWithTimeIntervalSince1970` / `timeIntervalSince1970` 这些 category 扩展，
 * 因此不需要额外的 platform.Foundation 导入。
 *
 * 时区不显式设置：NSDateFormatter 默认就用系统本地时区，与 Android 侧一致。
 */
actual object TimeFormat {
    /** Unix epoch（1970-01-01）到 Apple reference date（2001-01-01）之间的秒数。 */
    private const val REFERENCE_DATE_OFFSET_SEC = 978_307_200.0

    private fun formatter(pattern: String): NSDateFormatter = NSDateFormatter().apply {
        dateFormat = pattern
        locale = NSLocale(localeIdentifier = "zh_CN")
    }

    actual fun format(millis: Long, pattern: String): String {
        val date = NSDate(
            timeIntervalSinceReferenceDate = millis / 1000.0 - REFERENCE_DATE_OFFSET_SEC,
        )
        return formatter(pattern).stringFromDate(date)
    }

    actual fun parse(text: String, pattern: String): Long? {
        val date = formatter(pattern).dateFromString(text.trim()) ?: return null
        return ((date.timeIntervalSinceReferenceDate + REFERENCE_DATE_OFFSET_SEC) * 1000.0).toLong()
    }
}
