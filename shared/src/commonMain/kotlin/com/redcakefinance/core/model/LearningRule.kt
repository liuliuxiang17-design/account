package com.redcakefinance.core.model

/**
 * 学习规则：商户关键词 → 类型；hitCount 用于置信度 hits/(hits+2)。
 *
 * 纯数据模型，不带 Room 注解。
 */
data class LearningRule(
    val id: Long = 0,
    val keyword: String,
    val categoryId: Long,
    val hitCount: Int = 0,
    val lastCorrectedAt: Long = 0,
    /** 规则分组：0=泛用规则（默认） 1=隐私规则 */
    val privacy: Int = 0,
)
