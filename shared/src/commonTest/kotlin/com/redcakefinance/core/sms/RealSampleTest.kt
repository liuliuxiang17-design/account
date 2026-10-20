package com.redcakefinance.core.sms

import com.redcakefinance.core.format.Fmt
import com.redcakefinance.core.model.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 工行格式短信回归：主体是「尾号xxxx卡X月X日XX:XX支出(描述)X元，余额X元。【银行】」这一族，
 * 外加几个专治旧实现误判的边界（方向、红包商户、手续费、余额在前、无单位金额）。
 *
 * 隐私约定：本文件所有卡尾号、姓名、商户、余额**均为虚构**，
 * 不得把真实银行短信原文粘进来（正文会被打进测试报告与构建缓存，等于二次外泄）。
 */
class RealSampleTest {
    @Test fun `工行样本1_支付宝张三98元`() {
        val r = SmsParser.parse(
            "尾号6812卡8月24日16:07支出(消费支付宝-张三)98元，余额12,345.67元。【工商银行】",
            "工商银行",
        )!!
        assertEquals(9800L, r.amountFen)
        assertEquals("支付宝-张三", r.merchant)
        assertEquals(Direction.EXPENSE, r.direction)
    }

    @Test fun `工行样本2_财付通某奶茶店9元`() {
        val r = SmsParser.parse(
            "尾号6812卡8月23日10:51支出(消费财付通-某奶茶店)9元，余额23,456.78元。【工商银行】",
            "工商银行",
        )!!
        assertEquals(900L, r.amountFen)
        assertEquals("财付通-某奶茶店", r.merchant)
        assertEquals(Direction.EXPENSE, r.direction)
    }

    @Test fun `工行样本3_信使展期服务费3元`() {
        val r = SmsParser.parse(
            "尾号6812卡8月23日01:15工商银行支出(信使展期服务费)3元，余额34,567.89元。【工商银行】",
            "工商银行",
        )!!
        assertEquals(300L, r.amountFen)
        assertEquals("信使展期服务费", r.merchant)
        assertEquals(Direction.EXPENSE, r.direction)
    }

    @Test fun `工行样本4_收入带括号描述`() {
        val r = SmsParser.parse(
            "尾号6812卡8月24日09:12收入(工资)8,000.00元，余额45,678.90元。【工商银行】",
            "工商银行",
        )!!
        assertEquals(800000L, r.amountFen)
        assertEquals("工资", r.merchant)
        assertEquals(Direction.INCOME, r.direction)
    }

    /** 「你已支付19.9元，收款方：某咖啡店」不能因为「收款方」被判成收入。 */
    @Test fun `微信支付_收款方不应判成收入`() {
        val r = SmsParser.parse("你已支付19.90元，收款方：某咖啡店(某商场店)。", "微信支付")!!
        assertEquals(1990L, r.amountFen)
        assertEquals(Direction.EXPENSE, r.direction)
        assertEquals("某咖啡店(某商场店)", r.merchant)
    }

    /** 商户名含「红包」不应当成营销短信丢弃。 */
    @Test fun `红包商户仍是支出`() {
        val r = SmsParser.parse(
            "尾号6812卡8月24日20:11支出(消费财付通-红包)50元，余额56,789.01元。【工商银行】",
            "工商银行",
        )!!
        assertEquals(5000L, r.amountFen)
        assertEquals("财付通-红包", r.merchant)
        assertEquals(Direction.EXPENSE, r.direction)
    }

    /** 手续费排在交易金额之后时，不能把手续费当成交易额。 */
    @Test fun `带手续费时取交易金额而不是手续费`() {
        val r = SmsParser.parse(
            "尾号6812卡8月24日20:11支出(消费财付通-某商户)18.50元，手续费0.10元，余额67,890.12元。【工商银行】",
            "工商银行",
        )!!
        assertEquals(1850L, r.amountFen)
    }

    @Test fun `余额写在前面也能取到金额`() {
        val r = SmsParser.parse("余额12,345.67元，本次支出18.50元。【工商银行】", "工商银行")!!
        assertEquals(1850L, r.amountFen)
        assertEquals(Direction.EXPENSE, r.direction)
        assertEquals(1234567L, SmsParser.extractBalanceFen("余额12,345.67元，本次支出18.50元。【工商银行】"))
    }

    @Test fun `无元字金额兜底`() {
        val r = SmsParser.parse("您尾号1234卡消费98.00", "95588")!!
        assertEquals(9800L, r.amountFen)
    }

    @Test fun `余额查询短信不产生流水`() {
        assertNull(SmsParser.parse("您尾号6812卡当前余额12,345.67元。【工商银行】", "工商银行"))
    }

    @Test fun `营销立减不当流水`() {
        assertNull(SmsParser.parse("【XX支付】使用红包支付立减5元，快来领取", "XX支付"))
    }

    @Test fun `验证码含元也不当流水`() {
        assertNull(SmsParser.parse("您的验证码123456，可抵扣10元红包，5分钟内有效", "10086"))
    }

    @Test fun `退款方向优先于支出词`() {
        val r = SmsParser.parse(
            "您的退款申请成功，退款金额45.00元已退回您的余额，原商户：某出行平台。",
            "支付宝",
        )!!
        assertEquals(4500L, r.amountFen)
        assertEquals(Direction.REFUND, r.direction)
        assertNotNull(r.merchant)
    }

    @Test fun `万分位展示`() {
        assertEquals("¥98.00", Fmt.fenToYuan(9800L))
        assertEquals("¥1,2345.67", Fmt.fenToYuan(1234567L))
        assertEquals("¥1234,5678.90", Fmt.fenToYuan(1234567890L))
        assertEquals("¥1,2345.67", Fmt.fenToYuan(-1234567L).removePrefix("-"))
    }

    @Test fun `万分位输入解析容忍`() {
        assertEquals(1234567L, Fmt.yuanToFen("1,2345.67"))
    }
}
