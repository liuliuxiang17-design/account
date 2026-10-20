package com.redcakefinance.platform

import java.util.prefs.Preferences

/**
 * JVM 侧实现，仅用于本机跑测试。
 * 剪贴板返回 null：JVM 环境可能无图形界面，不引入 AWT 依赖。
 */
actual object KeyValueStore {
    private val prefs: Preferences = Preferences.userRoot().node("com/redcakefinance")

    actual fun getString(key: String): String? = prefs.get(key, null)

    actual fun putString(key: String, value: String) {
        prefs.put(key, value)
    }
}

actual object Clipboard {
    actual fun readText(): String? = null
}

actual object PlatformClock {
    actual fun nowMillis(): Long = System.currentTimeMillis()
}
