package com.redcakefinance.core.format

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone

/** iOS 侧时间格式化实现。 */
actual object TimeFormat {
    private fun formatter(pattern: String): NSDateFormatter = NSDateFormatter().apply {
        dateFormat = pattern
        locale = NSLocale("zh_CN")
        timeZone = NSTimeZone.localTimeZone
    }

    actual fun format(millis: Long, pattern: String): String {
        val date = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)
        return formatter(pattern).stringFromDate(date)
    }

    actual fun parse(text: String, pattern: String): Long? {
        val date = formatter(pattern).dateFromString(text.trim()) ?: return null
        return (date.timeIntervalSince1970 * 1000.0).toLong()
    }
}
