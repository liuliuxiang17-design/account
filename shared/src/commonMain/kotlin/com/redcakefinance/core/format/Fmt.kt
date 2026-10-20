package com.redcakefinance.core.format

import com.redcakefinance.core.model.Direction
import kotlin.math.roundToLong

/**
 * 展示/输入格式化工具（金额、时间、号码）。
 *
 * 输入：分（Long）/ 元字符串 / 时间戳 / 号码字符串。
 * 输出：中文万分位金额串、带符号金额、MM-dd HH:mm、格式化号码、解析后的分/时间戳；
 *      所有解析对非法输入返回 null，不抛异常。
 */
object Fmt {
    /**
     * 分 → 元字符串，中文万分位（4 位一组）展示，如 1234567 → ¥1,2345.67。
     * @param fen 金额（分），可为负（用于差额展示）。
     */
    fun fenToYuan(fen: Long): String {
        val negative = fen < 0
        val abs = if (negative) -fen else fen
        val yuan = abs / 100
        val cents = abs % 100
        // 中文万分位：整数部分从右向左每 4 位一组用逗号分隔（如 12345.67 → 1,2345.67）
        val grouped = yuan.toString().reversed().chunked(4).joinToString(",").reversed()
        val centsText = cents.toString().padStart(2, '0')
        return (if (negative) "-" else "") + "¥" + grouped + "." + centsText
    }

    /**
     * 带符号元字符串：负数 -¥xx，正数 +¥xx。
     * @param fen 金额（分）。
     */
    fun fenToSignedYuan(fen: Long): String =
        if (fen < 0) "-" + fenToYuan(-fen) else "+" + fenToYuan(fen)

    /**
     * 按收支方向显示符号金额：支出 -¥xx，收入/退款 +¥xx。
     * @param direction Direction.EXPENSE / INCOME / REFUND
     * @param fen 金额（分，恒为正）
     */
    fun signedByDirection(direction: String, fen: Long): String =
        if (direction == Direction.EXPENSE) "-" + fenToYuan(fen) else "+" + fenToYuan(fen)

    /**
     * epoch millis → MM-dd HH:mm（本地时区）。
     */
    fun timeShort(millis: Long): String = TimeFormat.format(millis, "MM-dd HH:mm")

    /**
     * epoch millis → MM-dd（本地时区）。
     */
    fun dateShort(millis: Long): String = TimeFormat.format(millis, "MM-dd")

    /**
     * 号码展示格式化：常见国家/地区区号与主号码之间加空格（+86 / +852 / +1 / +81 …）。
     * 先长后短匹配区号表；未匹配的 + 号码保持原样。
     * @param phone 原始号码
     * @return 格式化后的展示串
     */
    fun formatPhone(phone: String): String {
        if (phone.startsWith("+86") && phone.length > 3) return "+86 " + phone.substring(3)
        if (phone.startsWith("0086") && phone.length > 4) return "+86 " + phone.substring(4)
        val prefix = if (phone.startsWith("00") && phone.length > 4) 2 else 0
        if (prefix > 0) {
            (3 downTo 1).forEach { len ->
                if (phone.length > prefix + len) {
                    val code = phone.substring(prefix, prefix + len)
                    if (COUNTRY_CODES.contains(code)) return "+" + code + " " + phone.substring(prefix + len)
                }
            }
        }
        if (phone.startsWith("+") && phone.length > 3) {
            (3 downTo 1).forEach { len ->
                if (phone.length > 1 + len) {
                    val code = phone.substring(1, 1 + len)
                    if (COUNTRY_CODES.contains(code)) return "+" + code + " " + phone.substring(1 + len)
                }
            }
        }
        return phone
    }

    /** 常见国家/地区区号表（含港澳台）。 */
    private val COUNTRY_CODES = setOf(
        "86", "852", "853", "886", "1", "7", "81", "82", "44", "33", "49", "39", "34",
        "65", "60", "61", "64", "66", "63", "84", "90", "31", "32", "41", "46", "47", "48",
        "351", "353", "358", "91", "55", "52", "971", "966", "972", "20", "30", "43", "45",
    )

    /**
     * 解析用户输入的元字符串为「分」。
     * 容忍千分位/万分位逗号、¥ 符号、元 后缀、空白。
     * @param input 如 "18.50"、"1,299.00"、"¥50"、"50元"。
     * @return 分（Long）；空串、非数字、非正数、溢出返回 null。
     */
    fun yuanToFen(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace("¥", "").replace("元", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val v = cleaned.toDoubleOrNull() ?: return null
        if (!v.isFinite() || v <= 0.0) return null
        return (v * 100).roundToLong()
    }

    /**
     * 解析 "yyyy-MM-dd HH:mm" 文本为 epoch millis。
     * @return 毫秒时间戳；格式非法或不可解析返回 null（不抛异常）。
     */
    fun parseTime(text: String): Long? = TimeFormat.parse(text, "yyyy-MM-dd HH:mm")
}
