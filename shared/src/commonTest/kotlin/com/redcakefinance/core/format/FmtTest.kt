package com.redcakefinance.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 金额/时间格式化与解析测试。
 */
class FmtTest {
    @Test fun `金额文本转分_正常`() {
        assertEquals(1850L, Fmt.yuanToFen("18.50"))
        assertEquals(129900L, Fmt.yuanToFen("1,299.00"))
        assertEquals(5000L, Fmt.yuanToFen("¥50"))
        assertEquals(2000L, Fmt.yuanToFen("20元"))
    }

    @Test fun `金额文本转分_非法输入返回null`() {
        assertNull(Fmt.yuanToFen(""))
        assertNull(Fmt.yuanToFen("abc"))
        assertNull(Fmt.yuanToFen("0"))
        assertNull(Fmt.yuanToFen("-5"))
        assertNull(Fmt.yuanToFen("1e999"))
    }

    @Test fun `时间文本解析`() {
        assertNotNull(Fmt.parseTime("2026-08-24 10:15"))
        assertNull(Fmt.parseTime("乱写"))
        assertNull(Fmt.parseTime(""))
    }

    @Test fun `分转元展示`() {
        assertEquals("¥18.50", Fmt.fenToYuan(1850L))
        assertEquals("-¥1.00", Fmt.fenToSignedYuan(-100L))
    }
}
