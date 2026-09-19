package com.jarvis.mobile.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * اختبارات عقد محرك التحقق (Phase 11 — SPEC.md):
 * - عقد التحقق لكل خطوة: ماذا كان يجب أن يحدث؟ ما الدليل القابل للرصد؟
 * - الحالات الإلزامية: نجاح حقيقي / فشل حقيقي / دليل ناقص → لا SUCCESS أبداً.
 * - مصادر الإثبات: حالة الملف / النتيجة / حدث النظام / قراءة الشاشة.
 * - المحرك نقي وحتمي: نفس المدخلات → نفس النتيجة.
 */
class VerificationEngineContractTest {

    private val engine = VerificationEngine()

    private fun fileContract() = VerificationEngine.VerificationContract(
        stepId = "step-1",
        expectation = "الملف المطلوب موجود باسم محدد",
        evidenceRequired = listOf("الملف موجود", "الملف غير فارغ"),
    )

    // ------------------------------------------------------------------
    // 1) العقد نفسه: لا عقد تحقق بلا توقع أو بلا دليل
    // ------------------------------------------------------------------

    @Test
    fun `عقد تحقق بلا دليل مطلوب يرفض - لا تحقق بلا دليل قابل للرصد`() {
        try {
            VerificationEngine.VerificationContract(
                stepId = "s",
                expectation = "شيء ما",
                evidenceRequired = emptyList(),
            )
            fail("كان يجب رفض عقد بلا دليل مطلوب")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }

    @Test
    fun `عقد تحقق بلا توقع أو بلا معرف يرفض`() {
        try {
            VerificationEngine.VerificationContract(stepId = "  ", expectation = "توقع", evidenceRequired = listOf("دليل"))
            fail("كان يجب رفض عقد بلا معرّف")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
        try {
            VerificationEngine.VerificationContract(stepId = "s", expectation = " ", evidenceRequired = listOf("دليل"))
            fail("كان يجب رفض عقد بلا توقع")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }

    // ------------------------------------------------------------------
    // 2) نجاح حقيقي: كل الدليل موجود ومستوفى
    // ------------------------------------------------------------------

    @Test
    fun `نجاح حقيقي - دليل كامل مستوفى من كل المصادر`() {
        val result = engine.verify(
            fileContract(),
            listOf(
                VerificationEngine.Evidence.FileEvidence("الملف موجود", "/docs/تقرير.pdf", exists = true),
                VerificationEngine.Evidence.FileEvidence("الملف غير فارغ", "/docs/تقرير.pdf", exists = true, sizeBytes = 2048),
            ),
        )
        assertTrue(result.isSuccess)
        assertTrue(result is VerificationEngine.VerificationResult.Success)
    }

    @Test
    fun `نجاح حقيقي بكل مصادر الإثبات الأربعة`() {
        val contract = VerificationEngine.VerificationContract(
            stepId = "step-full",
            expectation = "أُرسل النموذج وظهر تأكيد على الشاشة",
            evidenceRequired = listOf("ملف الحفظ", "نتيجة الإرسال", "حدث النظام", "قراءة الشاشة"),
        )
        val result = engine.verify(
            contract,
            listOf(
                VerificationEngine.Evidence.FileEvidence("ملف الحفظ", "/data/نسخة.pdf", exists = true, sizeBytes = 10),
                VerificationEngine.Evidence.ResultEvidence("نتيجة الإرسال", "خادم الاستقبال أكد 200", success = true),
                VerificationEngine.Evidence.SystemEventEvidence("حدث النظام", "ACTION_SEND_COMPLETED", observed = true),
                VerificationEngine.Evidence.ScreenEvidence("قراءة الشاشة", "رسالة تم الإرسال ظاهرة", matches = true),
            ),
        )
        assertTrue(result.isSuccess)
    }

    // ------------------------------------------------------------------
    // 3) فشل حقيقي: دليل موجود لكنه غير مستوفى
    // ------------------------------------------------------------------

    @Test
    fun `فشل حقيقي - الملف غير موجود`() {
        val result = engine.verify(
            fileContract(),
            listOf(
                VerificationEngine.Evidence.FileEvidence("الملف موجود", "/docs/تقرير.pdf", exists = false),
                VerificationEngine.Evidence.FileEvidence("الملف غير فارغ", "/docs/تقرير.pdf", exists = false, sizeBytes = null),
            ),
        )
        assertTrue(result is VerificationEngine.VerificationResult.Failure)
        assertFalse(result.isSuccess)
        assertEquals(fileContract().stepId, result.stepId)
    }

    @Test
    fun `فشل حقيقي - نتيجة الخطوة فاشلة رغم وجود باقي الدليل`() {
        val result = engine.verify(
            fileContract(),
            listOf(
                VerificationEngine.Evidence.FileEvidence("الملف موجود", "/docs/تقرير.pdf", exists = true, sizeBytes = 5),
                VerificationEngine.Evidence.ResultEvidence("الملف غير فارغ", "الكتابة فشلت في منتصفها", success = false),
            ),
        )
        assertTrue(result is VerificationEngine.VerificationResult.Failure)
    }

    @Test
    fun `فشل حقيقي - ملف صفري الحجم لا يعد دليلا كافيا`() {
        val result = engine.verify(
            fileContract(),
            listOf(
                VerificationEngine.Evidence.FileEvidence("الملف موجود", "/docs/تقرير.pdf", exists = true, sizeBytes = 10),
                VerificationEngine.Evidence.FileEvidence("الملف غير فارغ", "/docs/تقرير.pdf", exists = true, sizeBytes = 0),
            ),
        )
        assertTrue("ملف 0 بايت ليس دليل نجاح", result is VerificationEngine.VerificationResult.Failure)
    }

    @Test
    fun `فشل حقيقي - حدث نظام لم يُرصد وقراءة شاشة غير مطابقة`() {
        val contract = VerificationEngine.VerificationContract(
            stepId = "step-events",
            expectation = "المزامنة اكتملت",
            evidenceRequired = listOf("حدث النظام", "قراءة الشاشة"),
        )
        val observedMiss = engine.verify(
            contract,
            listOf(
                VerificationEngine.Evidence.SystemEventEvidence("حدث النظام", "SYNC_FINISHED", observed = false),
                VerificationEngine.Evidence.ScreenEvidence("قراءة الشاشة", "شريط التقدم لا يزال ظاهراً", matches = false),
            ),
        )
        assertTrue(observedMiss is VerificationEngine.VerificationResult.Failure)
    }

    // ------------------------------------------------------------------
    // 4) دليل ناقص → Inconclusive وليس SUCCESS أبداً
    // ------------------------------------------------------------------

    @Test
    fun `دليل ناقص يعطي Inconclusive - لا SUCCESS ببعض الدليل`() {
        val result = engine.verify(
            fileContract(),
            listOf(
                VerificationEngine.Evidence.FileEvidence("الملف موجود", "/docs/تقرير.pdf", exists = true, sizeBytes = 10),
                // بند "الملف غير فارغ" بلا أي دليل
            ),
        )
        assertTrue(result is VerificationEngine.VerificationResult.Inconclusive)
        assertFalse("دليل ناقص لا يصبح نجاحاً أبداً", result.isSuccess)
        assertEquals(listOf("الملف غير فارغ"), (result as VerificationEngine.VerificationResult.Inconclusive).missing)
    }

    @Test
    fun `لا دليل إطلاقا يعطي Inconclusive وليس نجاحا ولا فشلا`() {
        val result = engine.verify(fileContract(), emptyList())
        assertTrue(result is VerificationEngine.VerificationResult.Inconclusive)
        assertEquals(fileContract().evidenceRequired, (result as VerificationEngine.VerificationResult.Inconclusive).missing)
    }

    @Test
    fun `دليل وهمي بلا تفاصيل لا يصنع نجاحا كاذبا`() {
        val result = engine.verify(
            fileContract(),
            listOf(
                VerificationEngine.Evidence.FileEvidence("الملف موجود", "/docs/تقرير.pdf", exists = true, sizeBytes = 5),
                VerificationEngine.Evidence.ResultEvidence("الملف غير فارغ", detail = "   ", success = true),
            ),
        )
        assertTrue("نتيجة بلا وصف فعلي ليست دليلاً", result is VerificationEngine.VerificationResult.Failure)
    }

    // ------------------------------------------------------------------
    // 5) حتمية المحرك وسلامة المدخلات
    // ------------------------------------------------------------------

    @Test
    fun `المحرك حتمي - نفس المدخلات تعطي نفس النتيجة`() {
        val evidence = listOf(
            VerificationEngine.Evidence.FileEvidence("الملف موجود", "/docs/تقرير.pdf", exists = true, sizeBytes = 10),
            VerificationEngine.Evidence.ResultEvidence("الملف غير فارغ", "2048 بايت مكتوبة", success = true),
        )
        val a = engine.verify(fileContract(), evidence)
        val b = engine.verify(fileContract(), evidence)
        assertEquals(a, b)
        assertTrue(a.isSuccess)
    }

    @Test
    fun `دليل خارج بنود العقد يرفض - لا تدقيق بدليل غير معلن`() {
        try {
            engine.verify(
                fileContract(),
                listOf(
                    VerificationEngine.Evidence.ScreenEvidence("بند غير معلن في العقد", "وصف", matches = true),
                ),
            )
            fail("كان يجب رفض دليل خارج بنود العقد")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }
}
