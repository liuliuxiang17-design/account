package com.redcakefinance.core.format

/**
 * 时间格式化/解析的平台实现（`yyyy-MM-dd HH:mm` 等 Unicode 模式串）。
 *
 * 放在 expect/actual 里，是为了让 [Fmt] 不依赖任何平台的日期库：
 * Android 侧走 `java.text.SimpleDateFormat`，iOS 侧走 `NSDateFormatter`，
 * 两者都使用同一套 Unicode 模式串，行为一致。
 */
expect object TimeFormat {
    /** epoch millis → 按 [pattern] 格式化（本地时区）。 */
    fun format(millis: Long, pattern: String): String

    /** 按 [pattern] 解析文本 → epoch millis；非法输入返回 null，不抛异常。 */
    fun parse(text: String, pattern: String): Long?
}
