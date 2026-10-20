package com.redcakefinance.core.model

/**
 * 短信原文（候选流水）。
 *
 * 去重用 smsId（provider 的 _id）；原文按保留策略清空后，结构化结果仍然可用。
 */
data class SmsMessage(
    val smsId: Long,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val isImported: Boolean = false,
)
