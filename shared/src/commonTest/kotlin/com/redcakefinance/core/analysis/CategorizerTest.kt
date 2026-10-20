package com.redcakefinance.core.analysis

import com.redcakefinance.core.model.LearningRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 分类引擎单元测试：默认规则、通配匹配、前缀剥离与学习规则命中。
 *
 * 输入：内存构造的 LearningRule 列表。
 * 输出：断言 Categorizer.match 返回的 categoryId。
 */
class CategorizerTest {
    /** 分类名 → id 的测试映射（与预设分类的 id 约定保持一致）。 */
    private val catIds = mapOf("餐饮" to 1L, "交通" to 2L, "购物" to 3L, "收入" to 7L)

    /** 按默认规则构造的规则表（只保留测试关心的四类）。 */
    private val defaultRules: List<LearningRule> =
        Categorizer.DEFAULT_RULES.mapNotNull { (kw, catName) ->
            catIds[catName]?.let { LearningRule(keyword = kw, categoryId = it, hitCount = 10) }
        }

    private fun rulesOf(keyword: String, catId: Long) =
        listOf(LearningRule(keyword = keyword, categoryId = catId, hitCount = 10))

    @Test fun `瑞幸咖啡归餐饮`() {
        assertEquals(1L, Categorizer.match("瑞幸咖啡", defaultRules, emptyList()))
    }

    @Test fun `滴滴出行归交通`() {
        assertEquals(2L, Categorizer.match("滴滴出行", defaultRules, emptyList()))
    }

    @Test fun `京东商城归购物`() {
        assertEquals(3L, Categorizer.match("京东商城", defaultRules, emptyList()))
    }

    @Test fun `工资归收入`() {
        assertEquals(7L, Categorizer.match("工资", defaultRules, emptyList()))
    }

    @Test fun `未知商户未分类`() {
        assertNull(Categorizer.match("神秘小店XYZ", defaultRules, emptyList()))
    }

    @Test fun `通配_星号结尾匹配任意前缀`() {
        val rules = rulesOf("*士多店", 3L)
        assertEquals(3L, Categorizer.match("财付通-士多店", rules, emptyList()))
        assertEquals(3L, Categorizer.match("支付宝-士多店", rules, emptyList()))
        // "士多店" 后跟 "1"，*士多店（必须以 士多店 结尾）不匹配
        assertNull(Categorizer.match("支付宝-士多店1", rules, emptyList()))
    }

    @Test fun `通配_前后星号匹配包含`() {
        val rules = rulesOf("*士多店*", 3L)
        assertEquals(3L, Categorizer.match("支付宝-士多店1", rules, emptyList()))
    }

    @Test fun `通配_结尾星号匹配前缀`() {
        // 财付通-* = 以"财付通-"开头（匹配任意后续）
        val rules = rulesOf("财付通-*", 3L)
        assertEquals(3L, Categorizer.match("财付通-麦当劳", rules, emptyList()))
        assertNull(Categorizer.match("支付宝-麦当劳", rules, emptyList()))
    }

    @Test fun `前缀剥离_无通配规则命中带前缀商户`() {
        // 规则「麦当劳」（无通配），商户「支付宝-麦当劳」：按屏蔽前缀剥离后命中
        val rules = rulesOf("麦当劳", 1L)
        val prefixes = listOf("支付宝-", "财付通-")
        assertEquals(1L, Categorizer.match("支付宝-麦当劳", rules, prefixes))
    }

    @Test fun `前缀剥离_通配规则命中不同前缀`() {
        // 学习时「财付通-士多店」→「*士多店」，匹配「支付宝-士多店」
        val rules = rulesOf("*士多店", 3L)
        val prefixes = listOf("支付宝-", "财付通-")
        assertEquals(3L, Categorizer.match("支付宝-士多店", rules, prefixes))
    }

    /** 通配匹配的边界：星号可为空、可多次、可连续；无星号时要求整串相等；正则元字符按字面处理。 */
    @Test fun `通配匹配_边界情形`() {
        // 无星号 = 整串相等
        assertTrue(Categorizer.globMatch("麦当劳", "麦当劳"))
        assertFalse(Categorizer.globMatch("麦当劳", "麦当劳店"))
        assertFalse(Categorizer.globMatch("麦当劳", "老麦当劳"))
        // 星号可匹配空串
        assertTrue(Categorizer.globMatch("财付通-*", "财付通-"))
        // 中间星号
        assertTrue(Categorizer.globMatch("阿里*巴巴", "阿里巴巴"))
        assertTrue(Categorizer.globMatch("阿里*巴巴", "阿里XX巴巴"))
        assertFalse(Categorizer.globMatch("阿里*巴巴", "阿里巴"))
        // 连续星号等价于一个
        assertTrue(Categorizer.globMatch("a**b", "axxb"))
        // 空模式只匹配空串
        assertTrue(Categorizer.globMatch("", ""))
        assertFalse(Categorizer.globMatch("", "x"))
        // 正则元字符按字面比较，不当正则用
        assertTrue(Categorizer.globMatch("店(新)", "店(新)"))
        assertFalse(Categorizer.globMatch("店(新)", "店新"))
        assertFalse(Categorizer.globMatch("店.新", "店X新"))
        // 纯星号匹配任意
        assertTrue(Categorizer.globMatch("*", "任何内容"))
    }
}
