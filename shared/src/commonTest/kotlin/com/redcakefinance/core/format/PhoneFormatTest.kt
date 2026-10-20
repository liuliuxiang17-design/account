package com.redcakefinance.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 号码展示格式化测试（区号加空格）。
 */
class PhoneFormatTest {
    @Test fun `中国区号`() {
        assertEquals("+86 13800138000", Fmt.formatPhone("+8613800138000"))
        assertEquals("+86 13800138000", Fmt.formatPhone("008613800138000"))
    }

    @Test fun `港澳台`() {
        assertEquals("+852 91234567", Fmt.formatPhone("+85291234567"))
        assertEquals("+886 912345678", Fmt.formatPhone("+886912345678"))
    }

    @Test fun `其他常见区号`() {
        assertEquals("+1 4155550100", Fmt.formatPhone("+14155550100"))
        assertEquals("+81 9012345678", Fmt.formatPhone("+819012345678"))
        assertEquals("+44 7911123456", Fmt.formatPhone("+447911123456"))
    }

    @Test fun `未匹配保持原样`() {
        assertEquals("+99912345678", Fmt.formatPhone("+99912345678"))
        assertEquals("95588", Fmt.formatPhone("95588"))
    }
}
