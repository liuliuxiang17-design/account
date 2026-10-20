package com.redcakefinance.core.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JVM 侧时间格式化实现，仅供本机跑测试使用（不进入 iOS / Android 产物）。
 * 与 Android 侧保持一致，便于本地验证 `commonTest` 的断言。
 */
actual object TimeFormat {
    actual fun format(millis: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.CHINA).format(Date(millis))

    actual fun parse(text: String, pattern: String): Long? = runCatching {
        SimpleDateFormat(pattern, Locale.CHINA).parse(text.trim())?.time
    }.getOrNull()
}
