package com.redcakefinance.core.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 无「元」单位金额解析测试：扣款/消费裸数字、积分不误杀、营销忽略。
 */
class NoUnitAmountTest {
    @Test fun `扣款无元单位`() {
        val r = SmsParser.parse("【XX银行】您尾号1234卡扣款成功98.00，余额1000.00。", "XX银行")!!
        assertEquals(9800L, r.amountFen)
    }

    @Test fun `消费无元单位`() {
        val r = SmsParser.parse("【YY支付】消费45.50元，余额200.00元。", "YY支付")!!
        assertEquals(4550L, r.amountFen)
    }

    @Test fun `交易短信含积分不被误杀`() {
        val r = SmsParser.parse("【工行】您尾号6812卡消费50元，获赠10积分，余额90.00元。【工商银行】", "工商银行")!!
        assertEquals(5000L, r.amountFen)
    }

    @Test fun `积分兑换营销短信仍忽略`() {
        assertNull(SmsParser.parse("【XX银行】您有积分可兑换100元礼品，点击查看。", "XX银行"))
    }
}
