package com.jarvis.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * اختبارات عقد المهمة (القسم 63): لا مهمة بدون هدف ومعايير نجاح صريحة.
 */
class TaskContractTest {

    @Test
    fun `عقد صالح يُقبل`() {
        val contract = TaskContract(
            objective = "إنشاء تقرير PDF",
            constraints = listOf("بدون إنترنت غير مطلوب"),
            availableTools = listOf("file_writer", "pdf_generator"),
            risk = RiskLevel.LOW,
            successCriteria = listOf("الملف موجود", "الملف يفتح بنجاح", "يحتوي الأقسام المطلوبة"),
        )
        assertEquals("إنشاء تقرير PDF", contract.objective)
        assertEquals(3, contract.successCriteria.size)
    }

    @Test
    fun `عقد بدون معايير نجاح يرفض`() {
        try {
            TaskContract(
                objective = "مهمة",
                constraints = emptyList(),
                availableTools = emptyList(),
                risk = RiskLevel.LOW,
                successCriteria = emptyList(),
            )
            fail("كان يجب رفض عقد بدون معايير نجاح")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }

    @Test
    fun `عقد بدون هدف يرفض`() {
        try {
            TaskContract(
                objective = "   ",
                constraints = emptyList(),
                availableTools = emptyList(),
                risk = RiskLevel.LOW,
                successCriteria = listOf("نتيجة موجودة"),
            )
            fail("كان يجب رفض عقد بدون هدف")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }
}
