package com.redcakefinance.core.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Android 侧时间格式化实现。 */
actual object TimeFormat {
    actual fun format(millis: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.CHINA).format(Date(millis))

    actual fun parse(text: String, pattern: String): Long? = runCatching {
        SimpleDateFormat(pattern, Locale.CHINA).parse(text.trim())?.time
    }.getOrNull()
}
