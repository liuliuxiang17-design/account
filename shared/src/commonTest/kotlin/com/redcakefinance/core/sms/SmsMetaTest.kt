package com.redcakefinance.core.sms

import com.redcakefinance.core.format.TimeFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 短信附加信息推断测试：机构名与正文时间。
 *
 * iOS 拿不到系统短信库，这两项只能从正文猜，所以边界情形要写清楚。
 */
class SmsMetaTest {
    private fun at(text: String): Long = TimeFormat.parse(text, "yyyy-MM-dd HH:mm")!!

    @Test fun `提取机构名`() {
        assertEquals("工商银行", SmsMeta.extractSender("尾号6812卡消费98元，余额100元。【工商银行】"))
        assertEquals("支付宝", SmsMeta.extractSender("【支付宝】您已支付18.50元。"))
    }

    @Test fun `没有签名返回null`() {
        assertNull(SmsMeta.extractSender("您尾号1234卡消费98.00"))
    }

    @Test fun `解析正文时间`() {
        val now = at("2026-10-20 12:00")
        assertEquals(at("2026-08-24 16:07"), SmsMeta.parseOccurredAt("8月24日16:07支出98元", now))
    }

    @Test fun `带年份时按年份解析`() {
        val now = at("2026-10-20 12:00")
        assertEquals(at("2025-03-08 09:05"), SmsMeta.parseOccurredAt("2025年3月8日09:05支出98元", now))
    }

    @Test fun `跨年短信回退到上一年`() {
        // 1 月导入 12 月的短信，不能算成来年
        val now = at("2026-01-05 10:00")
        assertEquals(at("2025-12-24 16:07"), SmsMeta.parseOccurredAt("12月24日16:07支出98元", now))
    }

    @Test fun `正文没有时间时回落到现在`() {
        val now = at("2026-10-20 12:00")
        assertEquals(now, SmsMeta.parseOccurredAt("您尾号1234卡消费98.00", now))
    }

    @Test fun `非法日期不参与解析`() {
        val now = at("2026-10-20 12:00")
        assertEquals(now, SmsMeta.parseOccurredAt("13月40日25:99支出98元", now))
    }
}
