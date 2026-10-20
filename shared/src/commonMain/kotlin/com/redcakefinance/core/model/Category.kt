package com.redcakefinance.core.model

/**
 * 消费类型标签（预设 + 用户自定义）。
 *
 * 纯数据模型，不带 Room 注解。
 */
data class Category(
    val id: Long = 0,
    val name: String,
    /** ARGB 颜色 */
    val colorArgb: Int,
    val isPreset: Boolean = false,
    val sortOrder: Int = 0,
)
