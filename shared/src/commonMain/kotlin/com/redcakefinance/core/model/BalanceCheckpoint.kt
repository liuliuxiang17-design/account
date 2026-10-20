package com.redcakefinance.core.model

/**
 * 余额快照：从含「余额」的短信中提取，是全部统计的**可靠基准**
 * （金额曲线、变化直方图、对账缺口都由它推导）。
 *
 * 唯一性约定：同发件人 + 同毫秒只允许一条（由调用方保证去重）。
 */
data class BalanceCheckpoint(
    val id: Long = 0,
    val sender: String,
    val balanceFen: Long,
    val capturedAt: Long,
    val rawBody: String,
)
