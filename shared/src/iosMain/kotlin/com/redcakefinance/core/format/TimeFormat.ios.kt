package com.redcakefinance.core.format

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.timeIntervalSince1970

/**
 * iOS 侧时间格式化实现。
 *
 * `timeIntervalSince1970` 来自 Objective-C category，在 Kotlin/Native 里是扩展，
 * 必须显式 import 才能用；创建用 NSDate 的指定构造器，避免依赖 category 类方法。
 * 时区不显式设置：NSDateFormatter 默认就用系统本地时区，与 Android 侧一致。
 */
actual object TimeFormat {
    private fun formatter(pattern: String): NSDateFormatter = NSDateFormatter().apply {
        dateFormat = pattern
        locale = NSLocale(localeIdentifier = "zh_CN")
    }

    actual fun format(millis: Long, pattern: String): String =
        formatter(pattern).stringFromDate(NSDate(timeIntervalSince1970 = millis / 1000.0))

    actual fun parse(text: String, pattern: String): Long? {
        val date = formatter(pattern).dateFromString(text.trim()) ?: return null
        return (date.timeIntervalSince1970 * 1000.0).toLong()
    }
}
