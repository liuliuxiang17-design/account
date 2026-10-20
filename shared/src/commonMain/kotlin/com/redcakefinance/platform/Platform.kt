package com.redcakefinance.platform

/**
 * 键值存储：只用来保存整份账本文本，不涉及路径管理。
 *
 * iOS 侧落在 NSUserDefaults（沙盒内的 plist），JVM 侧落在用户偏好里。
 */
expect object KeyValueStore {
    /** 读取；不存在返回 null。 */
    fun getString(key: String): String?

    /** 写入（覆盖）。 */
    fun putString(key: String, value: String)
}

/** 读取剪贴板文本；无内容或不可用时返回 null。 */
expect object Clipboard {
    fun readText(): String?

    /** 写入剪贴板；不可用时返回 false。 */
    fun writeText(text: String): Boolean
}

/** 当前时间（epoch millis）。 */
expect object PlatformClock {
    fun nowMillis(): Long
}
