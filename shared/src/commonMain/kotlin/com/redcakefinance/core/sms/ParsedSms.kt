package com.redcakefinance.core.sms

import com.redcakefinance.core.model.Direction

/**
 * 单条短信的解析结果。
 *
 * 输入：由 [SmsParser.parse] 构造。
 * 输出：merchant（商户）、amountFen（分）、direction（收支方向）、confidence（置信度）。
 */
data class ParsedSms(
    val merchant: String,
    val amountFen: Long,
    val direction: String,
    val confidence: Double = 1.0,
) {
    /** 是否退款。 */
    val isRefund: Boolean get() = direction == Direction.REFUND
}
