package com.redcakefinance.platform

import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIPasteboard

/** NSUserDefaults 实现：数据留在 App 沙盒，随 App 卸载一并删除。 */
actual object KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(key)

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}

/**
 * UIPasteboard 实现。
 *
 * 读取会触发 iOS 16+ 的一次性「允许粘贴？」系统授权；因为入口是用户主动点的按钮，
 * 弹窗出现在预期内，可接受度最高。
 */
actual object Clipboard {
    actual fun readText(): String? {
        val board = UIPasteboard.generalPasteboard
        if (!board.hasStrings()) return null
        val text = board.string ?: return null
        return text.ifBlank { null }
    }
}

/**
 * 基于 NSDate 的时钟。
 *
 * 只用 NSDate 自身声明的 `timeIntervalSinceReferenceDate`（基准是 2001-01-01 UTC），
 * 不依赖 `timeIntervalSince1970` 之类 category 扩展。
 */
actual object PlatformClock {
    private const val REFERENCE_DATE_OFFSET_SEC = 978_307_200.0

    actual fun nowMillis(): Long =
        ((NSDate().timeIntervalSinceReferenceDate + REFERENCE_DATE_OFFSET_SEC) * 1000.0).toLong()
}
