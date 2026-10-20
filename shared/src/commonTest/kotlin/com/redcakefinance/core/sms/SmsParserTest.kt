package com.redcakefinance.core.sms

import com.redcakefinance.core.model.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 短信解析引擎单元测试：金额/商户/方向/退款/验证码忽略/中文大写兜底等样例。
 *
 * 输入：内存构造的短信正文（全部虚构）。
 * 输出：断言 ParsedSms 各字段或 null。
 */
class SmsParserTest {
    private fun p(body: String, sender: String = "") = SmsParser.parse(body, sender)

    @Test fun `样例1 支付宝美团支付`() {
        val r = p("【支付宝】您尾号1234的支付宝账户于08月24日10:15向美团平台支付18.50元，余额85.20元。", "支付宝")!!
        assertEquals(1850L, r.amountFen); assertEquals("美团平台", r.merchant); assertEquals(Direction.EXPENSE, r.direction)
    }

    @Test fun `样例2 微信瑞幸`() {
        val r = p("【微信支付】你已支付19.90元，收款方：瑞幸咖啡(中关村店)。", "微信支付")!!
        assertEquals(1990L, r.amountFen); assertEquals("瑞幸咖啡(中关村店)", r.merchant)
    }

    @Test fun `样例3 工行永辉`() {
        val r = p("【工商银行】您尾号5678卡8月24日10:20消费支出200.00元，商户：永辉超市，余额1234.56元。", "工商银行")!!
        assertEquals(20000L, r.amountFen); assertEquals("永辉超市", r.merchant); assertEquals(Direction.EXPENSE, r.direction)
    }

    @Test fun `样例4 建行京东千分位`() {
        val r = p("【建设银行】8月24日11:00您在京东商城消费1,299.00元，余额5,000.00元。", "建设银行")!!
        assertEquals(129900L, r.amountFen); assertEquals("京东商城", r.merchant)
    }

    @Test fun `样例5 交行麦当劳`() {
        val r = p("【交通银行】您8月24日消费12.50元，商户：麦当劳。", "交通银行")!!
        assertEquals(1250L, r.amountFen); assertEquals("麦当劳", r.merchant)
    }

    @Test fun `样例6 中行加油站`() {
        val r = p("【中国银行】您尾号8888卡支出35.50元，商户：中国石油加油站，余额999.00元。", "中国银行")!!
        assertEquals(3550L, r.amountFen); assertEquals("中国石油加油站", r.merchant)
    }

    @Test fun `样例7 京东PLUS扣款`() {
        val r = p("【京东金融】自动扣款99.00元，项目：京东PLUS会员续费。", "京东金融")!!
        assertEquals(9900L, r.amountFen); assertEquals("京东PLUS会员续费", r.merchant)
    }

    @Test fun `样例8 移动充值`() {
        val r = p("【中国移动】您已成功充值50.00元，当前余额100.00元。", "中国移动")!!
        assertEquals(5000L, r.amountFen); assertEquals("中国移动", r.merchant)
    }

    @Test fun `样例9 招行工资收入`() {
        val r = p("【招商银行】您账户今日收入工资12,000.00元，余额20,000.00元。", "招商银行")!!
        assertEquals(1200000L, r.amountFen); assertEquals("工资", r.merchant); assertEquals(Direction.INCOME, r.direction)
    }

    @Test fun `样例10 农行货款转入`() {
        val r = p("【农业银行】您账户8月24日转入2,000.00元，摘要：货款，余额3,000.00元。", "农业银行")!!
        assertEquals(200000L, r.amountFen); assertEquals("货款", r.merchant); assertEquals(Direction.INCOME, r.direction)
    }

    @Test fun `样例11 网商转账张三`() {
        val r = p("【网商银行】收到转账500.00元，付款方：张三。", "网商银行")!!
        assertEquals(50000L, r.amountFen); assertEquals("张三", r.merchant); assertEquals(Direction.INCOME, r.direction)
    }

    @Test fun `样例12 支付宝退款滴滴`() {
        val r = p("【支付宝】您的退款申请成功，退款金额45.00元已退回您的余额，原商户：滴滴出行。", "支付宝")!!
        assertEquals(4500L, r.amountFen); assertEquals("滴滴出行", r.merchant); assertEquals(Direction.REFUND, r.direction)
    }

    @Test fun `样例13 交行信用卡还款`() {
        val r = p("【交通银行】您的信用卡已还款3,000.00元，交易成功。", "交通银行")!!
        assertEquals(300000L, r.amountFen); assertEquals(Direction.EXPENSE, r.direction)
    }

    @Test fun `样例14 验证码忽略`() {
        assertNull(p("【中国移动】您的验证码是123456，5分钟内有效。", "10086"))
    }

    @Test fun `样例15 通知忽略`() {
        assertNull(p("【工商银行】尊敬的客户，您的账户信息已更新。", "工商银行"))
    }

    @Test fun `中文大写金额兜底`() {
        val r = p("【XX银行】您消费壹佰贰拾元整。", "XX银行")!!
        assertEquals(12000L, r.amountFen)
    }

    @Test fun `收入短信_收到转账_判收入`() {
        val r = SmsParser.parse("您尾号1234账户收到转账5000.00元。", "95588")
        assertNotNull(r)
        assertEquals("INCOME", r!!.direction)
    }
}
