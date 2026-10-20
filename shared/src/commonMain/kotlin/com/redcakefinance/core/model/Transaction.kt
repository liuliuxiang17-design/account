package com.redcakefinance.core.model

/**
 * 已确认流水实体。金额统一以「分」存储（Long），避免浮点误差。
 *
 * 由短信导入 / 大爆炸确认 / CSV 导入构造；`origin = BALANCE_GAP` 的行是对账占位，不是真实交易。
 *
 * 注意：本类是跨平台纯数据模型，不带 Room 注解（iOS 端不使用 Room）。
 * Android 端如需持久化，另建带注解的实体或在 Room KMP 稳定后合并。
 */
data class Transaction(
    val id: Long = 0,
    val smsId: Long?,
    /** 短信发件号码（补录/粘贴等无 smsId 的记录也能归入银行） */
    val sender: String? = null,
    val merchant: String,
    val amountFen: Long,
    /** Direction.EXPENSE / INCOME / REFUND */
    val direction: String,
    val occurredAt: Long,
    val categoryId: Long?,
    /** 自动分类置信度 0..1；人工确认后置 1 */
    val confidence: Double = 0.0,
    val rawBody: String,
    val importedAt: Long,
    /** 来源：`TransactionOrigin.IMPORTED`（默认）或 `TransactionOrigin.BALANCE_GAP`。 */
    val origin: Int = TransactionOrigin.IMPORTED,
)
