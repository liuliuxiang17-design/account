package com.redcakefinance.core.model

/** 收支方向常量。 */
object Direction {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val REFUND = "REFUND"
}

/**
 * 流水的来源（origin）。
 *
 * 用它区分「真实交易」与「系统占位补录」，而不是靠商户名：
 * 商户名是给人看的显示字段，用户随时可能改，改完就不该脱离系统管理；
 * 也不该因为某个真实商户恰好叫「余额差额」就被当成补录排除掉。
 */
object TransactionOrigin {
    /** 短信导入 / 粘贴 / 分享 / CSV 等外部来源。 */
    const val IMPORTED = 0

    /** 余额对账补录：不是真实交易，只用来让「余额 = 流水累计」在数字上成立。 */
    const val BALANCE_GAP = 1
}
