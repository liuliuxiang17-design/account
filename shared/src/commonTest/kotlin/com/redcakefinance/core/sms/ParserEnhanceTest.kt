package com.redcakefinance.core.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 解析增强测试：RMB/人民币变体、促销短信与验证码忽略。
 */
class ParserEnhanceTest {
    @Test fun `RMB变体`() {
        val r = SmsParser.parse("【XX】您已消费 RMB 88.00，感谢使用。", "XX")!!
        assertEquals(8800L, r.amountFen)
    }

    @Test fun `人民币变体`() {
        val r = SmsParser.parse("【YY】人民币 12.50 已扣除。", "YY")!!
        assertEquals(1250L, r.amountFen)
    }

    @Test fun `促销短信忽略`() {
        assertNull(SmsParser.parse("【天猫】618大促满100元减20元，速来抢购！", "天猫"))
        assertNull(SmsParser.parse("【XX银行】您获得积分兑换好礼机会，点击查看优惠。", "XX银行"))
    }

    @Test fun `验证码忽略`() {
        assertNull(SmsParser.parse("【验证码】您的登录验证码是123456，5分钟内有效。", "10086"))
    }
}
