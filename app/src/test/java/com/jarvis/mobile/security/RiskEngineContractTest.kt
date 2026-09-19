package com.jarvis.mobile.security

import com.jarvis.mobile.core.AgentEvent
import com.jarvis.mobile.core.AgentState
import com.jarvis.mobile.core.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات عقد محرك المخاطرة والتأكيد (Phase 10 — SPEC.md):
 * - جدول القرارات: كل مستوى خطورة × كل سياسة → قرار صريح.
 * - بوابة WAITING_CONFIRMATION إلزامية: لا خطوة غير منخفضة الخطورة تتجاوزها.
 * - CRITICAL مرفوض مطلقاً.
 * - سجل قرارات قابل للتدقيق: كل قرار يُسجَّل.
 */
class RiskEngineContractTest {

    // ------------------------------------------------------------------
    // 1) جدول القرارات: كل مستوى × كل سياسة
    // ------------------------------------------------------------------

    @Test
    fun `جدول القرارات - كل مستوى خطورة مضروبا في كل سياسة يعطي قرارا صريحا`() {
        val levels = RiskLevel.entries
        val policies = ConfirmationPolicy.entries

        for (policy in policies) {
            val engine = RiskEngine(policy = policy)
            for (level in levels) {
                val decision = engine.decide(level)
                when {
                    // CRITICAL مرفوض مطلقاً في كل السياسات
                    level == RiskLevel.CRITICAL ->
                        assertTrue(
                            "CRITICAL يجب أن يُرفض مطلقاً (سياسة $policy)",
                            decision is RiskEngine.Decision.Deny,
                        )
                    // جدول السياسات الصريح
                    policy == ConfirmationPolicy.CONFIRM_MEDIUM_AND_ABOVE ->
                        if (level == RiskLevel.LOW) {
                            assertTrue(decision is RiskEngine.Decision.Allow)
                        } else {
                            assertTrue("MEDIUM/HIGH تطلب تأكيداً في $policy", decision is RiskEngine.Decision.RequireConfirmation)
                        }

                    policy == ConfirmationPolicy.CONFIRM_HIGH_ONLY ->
                        if (level == RiskLevel.HIGH) {
                            assertTrue(decision is RiskEngine.Decision.RequireConfirmation)
                        } else {
                            assertTrue(decision is RiskEngine.Decision.Allow)
                        }

                    policy == ConfirmationPolicy.CONFIRM_ALL ->
                        assertTrue("كل مستويات تطلب تأكيداً في $policy", decision is RiskEngine.Decision.RequireConfirmation)
                }
            }
        }
    }

    @Test
    fun `الخطوة بلا تصنيف خطورة تُصنف CRITICAL تلقائياً`() {
        val engine = RiskEngine()
        assertEquals(RiskLevel.CRITICAL, engine.classify(null))
        // والقرار المشتق منها: رفض — لا خطوة مجهولة الخطورة تمر
        assertTrue(engine.decide(null) is RiskEngine.Decision.Deny)
    }

    // ------------------------------------------------------------------
    // 2) البوابة الإلزامية: لا خطوة غير LOW تتجاوزها
    // ------------------------------------------------------------------

    @Test
    fun `خطوة LOW تتجاوز البوابة مباشرة تحت السياسة الافتراضية`() {
        val engine = RiskEngine()
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        val decision = engine.gate("قراءة نص من الشاشة للعرض", RiskLevel.LOW)
        assertTrue(decision is RiskEngine.Decision.Allow)
        assertEquals(AgentState.UNDERSTANDING, engine.machine.stateValue) // لم تغادر المسار
    }

    @Test
    fun `خطوة MEDIUM تتوقف فعليا في WAITING_CONFIRMATION ولا تنفذ قبل التأكيد`() {
        val engine = RiskEngine()
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        engine.machine.dispatch(AgentEvent.PLAN)

        val decision = engine.gate("حذف ملف مؤقت للمستخدم", RiskLevel.MEDIUM)
        assertTrue(decision is RiskEngine.Decision.RequireConfirmation)
        assertTrue("البوابة يجب أن توقف الآلة في WAITING_CONFIRMATION", engine.awaitingConfirmation)
        assertEquals("لم يُمنح التنفيذ بعد — الآلة لم تصل إلى EXECUTING", AgentState.WAITING_CONFIRMATION, engine.machine.stateValue)
        assertEquals("حذف ملف مؤقت للمستخدم", engine.pendingStep)

        // محاولة دفع التنفيذ من الحالة المنتظرة بأي حدث آخر غير CONFIRMED تُرفض من آلة الحالة نفسها
        assertNull("EXECUTE لا يجوز من WAITING_CONFIRMATION — لا مسار جانبي", engine.machine.dispatch(AgentEvent.EXECUTE))
        assertEquals(AgentState.WAITING_CONFIRMATION, engine.machine.stateValue)

        // التأكيد هو الطريق الوحيد إلى التنفيذ
        assertTrue(engine.confirm())
        assertEquals(AgentState.EXECUTING, engine.machine.stateValue)
    }

    @Test
    fun `خطوة HIGH تسلك المسار نفسه - توقف ثم تأكيد`() {
        val engine = RiskEngine()
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        engine.machine.dispatch(AgentEvent.PLAN)

        assertTrue(engine.gate("إرسال رسالة باسم المستخدم", RiskLevel.HIGH) is RiskEngine.Decision.RequireConfirmation)
        assertTrue(engine.awaitingConfirmation)
        assertTrue(engine.confirm())
        assertEquals(AgentState.EXECUTING, engine.machine.stateValue)
    }

    @Test
    fun `رفض المستخدم في البوابة يأخذ الآلة إلى مسار الفشل الآمن`() {
        val engine = RiskEngine()
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        engine.machine.dispatch(AgentEvent.PLAN)
        engine.gate("مسح بيانات تطبيق", RiskLevel.MEDIUM)
        assertTrue(engine.awaitingConfirmation)

        assertTrue(engine.reject())
        assertEquals(AgentState.ERROR, engine.machine.stateValue)
        assertFalse(engine.awaitingConfirmation)
        // ومن ERROR لا تنفيذ مباشر بلا إعادة فهم/تخطيط
        assertNull(engine.machine.dispatch(AgentEvent.CONFIRMED))
    }

    @Test
    fun `confirm وreject خارج البوابة يرفضان - لا تأكيد بلا بوابة فعلية`() {
        val engine = RiskEngine()
        assertFalse(engine.confirm())
        assertFalse(engine.reject())
    }

    @Test
    fun `CRITICAL يُرفض قبل أي شيء ولا يلمس آلة الحالة`() {
        val engine = RiskEngine()
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        val before = engine.machine.stateValue

        val decision = engine.gate("تنفيذ كود في الساندبوكس", RiskLevel.CRITICAL)
        assertTrue(decision is RiskEngine.Decision.Deny)
        assertEquals("آلة الحالة لم تتأثر بقرار الرفض", before, engine.machine.stateValue)
        assertFalse(engine.awaitingConfirmation)
    }

    @Test
    fun `بوابة بلا اسم خطوة ترفض - قرار بلا هوية لا يُدقَّق`() {
        val engine = RiskEngine()
        try {
            engine.gate("   ", RiskLevel.LOW)
            org.junit.Assert.fail("كان يجب رفض خطوة بلا اسم قابل للتدقيق")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }

    @Test
    fun `طلب تأكيد من حالة لا تسمح به يُخفَّض إلى رفض - البوابة لا تكذب`() {
        val engine = RiskEngine()
        // من IDLE لا يُقبل ASK_CONFIRMATION في جدول آلة الحالة — يجب أن تفشل البوابة لا أن تكذب
        val decision = engine.gate("خطوة من حالة خاطئة", RiskLevel.MEDIUM)
        assertTrue(decision is RiskEngine.Decision.Deny)
        assertEquals(AgentState.IDLE, engine.machine.stateValue)
        assertFalse(engine.awaitingConfirmation)
    }

    // ------------------------------------------------------------------
    // 3) سجل القرارات القابل للتدقيق
    // ------------------------------------------------------------------

    @Test
    fun `سجل القرارات يوثق كل قرار بالترتيب مع نتيجة البوابة`() {
        val engine = RiskEngine()
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        engine.machine.dispatch(AgentEvent.PLAN)

        engine.gate("خطوة منخفضة", RiskLevel.LOW) // Allow (passed)
        engine.gate("خطوة متوسطة", RiskLevel.MEDIUM) // RequireConfirmation (stopped at gate)
        engine.confirm() // passed

        engine.machine.dispatch(AgentEvent.VERIFY)
        engine.machine.dispatch(AgentEvent.SUCCESS)
        engine.machine.dispatch(AgentEvent.RESET)
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        engine.gate("خطوة حرجة", RiskLevel.CRITICAL) // Deny

        val log = engine.auditLog
        assertEquals(4, log.size)
        assertEquals("خطوة منخفضة", log[0].step)
        assertTrue(log[0].passedGate)
        assertEquals("خطوة متوسطة", log[1].step)
        assertFalse(log[1].passedGate) // توقفت في البوابة (التنفيذ جرى لاحقاً بسجل التأكيد)
        assertEquals("خطوة حرجة", log[3].step)
        assertFalse(log[3].passedGate)
        // كل سجل يحمل مستوى خطورته المصنف
        assertEquals(RiskLevel.LOW, log[0].risk)
        assertEquals(RiskLevel.MEDIUM, log[1].risk)
        assertEquals(RiskLevel.CRITICAL, log[3].risk)
    }

    @Test
    fun `سجل القرارات append-only - لا تعديل على التاريخ`() {
        val engine = RiskEngine()
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        engine.gate("أ", RiskLevel.LOW)
        val first = engine.auditLog
        assertEquals(1, first.size)

        engine.gate("ب", RiskLevel.CRITICAL)
        // النسخة الأولى لم تتغير — سجل للتدقيق لا مخزن حية
        assertEquals(1, first.size)
        assertEquals(2, engine.auditLog.size)
    }

    @Test
    fun `سجل التأكيد يوثق الخطوة والمستوى الصحيحين بعد منح التأكيد`() {
        val engine = RiskEngine()
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        engine.machine.dispatch(AgentEvent.PLAN)
        engine.gate("خطوة عالية الخطورة الموثقة", RiskLevel.HIGH)
        engine.confirm()

        val log = engine.auditLog
        assertEquals(2, log.size)
        assertEquals("خطوة عالية الخطورة الموثقة", log[1].step)
        assertEquals(RiskLevel.HIGH, log[1].risk)
        assertTrue(log[1].passedGate)
        assertTrue(log[1].decision is RiskEngine.Decision.Allow)
    }
}
