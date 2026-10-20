package com.redcakefinance.core.analysis

import com.redcakefinance.core.model.LearningRule

/**
 * 消费类型分类引擎：全部走学习规则表（通配符匹配），默认规则作为种子写入表中，
 * 在「学习规则」页可编辑 —— 内置默认分类规则同样可改。
 *
 * 匹配规则：
 *  - "*" 是通配符，匹配任意字符序列（含空）；
 *  - "*关键词" = 以关键词结尾；"关键词*" = 以关键词开头；"*关键词*" = 包含关键词；
 *  - 例："*士多店" 匹配「财付通-士多店」「支付宝-士多店」，不匹配「支付宝-士多店1」；
 *    "*士多店*" 则都匹配。
 *
 * 输入：商户名、学习规则列表、可选屏蔽前缀列表。
 * 输出：命中的 categoryId；未命中返回 null。
 */
object Categorizer {
    /** 默认规则种子（keyword, 分类名）：首次建库/升级时写入 learning_rule，可编辑。 */
    val DEFAULT_RULES: List<Pair<String, String>> = listOf(
        "*美团*" to "餐饮", "*饿了么*" to "餐饮", "*麦当劳*" to "餐饮", "*瑞幸*" to "餐饮",
        "*星巴克*" to "餐饮", "*肯德基*" to "餐饮", "*汉堡王*" to "餐饮", "*外卖*" to "餐饮", "*餐厅*" to "餐饮",
        "*滴滴*" to "交通", "*高德*" to "交通", "*12306*" to "交通", "*铁路*" to "交通", "*地铁*" to "交通",
        "*加油*" to "交通", "*停车*" to "交通", "*出行*" to "交通", "*公交*" to "交通",
        "*京东*" to "购物", "*淘宝*" to "购物", "*天猫*" to "购物", "*拼多多*" to "购物", "*唯品会*" to "购物", "*苏宁*" to "购物",
        "*猫眼*" to "娱乐", "*腾讯视频*" to "娱乐", "*爱奇艺*" to "娱乐", "*网易云*" to "娱乐", "*Steam*" to "娱乐", "*游戏*" to "娱乐",
        "*医院*" to "医疗", "*药店*" to "医疗", "*药房*" to "医疗", "*诊所*" to "医疗",
        "*电费*" to "居住", "*水费*" to "居住", "*燃气*" to "居住", "*房租*" to "居住", "*物业*" to "居住", "*供暖*" to "居住",
        "*工资*" to "收入", "*代发*" to "收入", "*奖金*" to "收入", "*报销*" to "收入", "*分红*" to "收入",
    )

    /**
     * 纯内存匹配：规则列表由调用方一次性取出后传入。
     * 逐条查库（每个商户都查一次全表）在重算几百条时既慢又会把数据库读写排满。
     *
     * @param merchant 商户名
     * @param rules 全部学习规则（已加载）
     * @param prefixes 商户屏蔽前缀
     * @return 命中的 categoryId；未命中返回 null
     */
    fun match(merchant: String, rules: List<LearningRule>, prefixes: List<String>): Long? {
        if (merchant.isBlank() || rules.isEmpty()) return null
        val merchantCandidates = buildList {
            add(merchant)
            prefixes.forEach { p ->
                val literal = p.replace("*", "")
                if (literal.isNotEmpty() && merchant.startsWith(literal)) add(merchant.removePrefix(literal))
            }
        }
        var bestId: Long? = null
        var bestChars = -1
        var bestStars = -1
        for (r in rules) {
            if (merchantCandidates.any { globMatch(r.keyword, it) }) {
                val (chars, stars) = specificity(r.keyword)
                if (chars > bestChars || (chars == bestChars && stars > bestStars)) {
                    bestId = r.categoryId
                    bestChars = chars
                    bestStars = stars
                }
            }
        }
        return bestId
    }

    /**
     * 规则特异性：去除通配符后的有效字符数与通配符数（连续 * 先合并，如 *1*****23* → *1*23*）。
     * @param pattern 规则模式
     * @return (有效字符数, 通配符数)
     */
    fun specificity(pattern: String): Pair<Int, Int> {
        val norm = pattern.replace(Regex("\\*+"), "*")
        return norm.count { it != '*' } to norm.count { it == '*' }
    }

    /**
     * 通配匹配：`*` 匹配任意字符序列（含空），其余字符按字面比较，整串必须匹配。
     *
     * 用双指针 + 回溯，不用正则：重新归类时是「规则数 × 流水数 × 候选名」量级的调用，
     * 每次编译一条正则会把这里变成热点（几万次编译）。
     *
     * @param pattern 含 `*` 的模式
     * @param text 待匹配文本
     * @return 整串是否匹配
     */
    fun globMatch(pattern: String, text: String): Boolean {
        var p = 0
        var t = 0
        var starAt = -1
        var resumeFrom = 0
        while (t < text.length) {
            when {
                p < pattern.length && pattern[p] == text[t] -> { p++; t++ }
                p < pattern.length && pattern[p] == '*' -> { starAt = p++; resumeFrom = t }
                starAt >= 0 -> { p = starAt + 1; resumeFrom++; t = resumeFrom }
                else -> return false
            }
        }
        while (p < pattern.length && pattern[p] == '*') p++
        return p == pattern.length
    }
}
