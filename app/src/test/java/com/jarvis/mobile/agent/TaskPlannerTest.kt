package com.jarvis.mobile.agent

import com.jarvis.mobile.core.AgentEvent
import com.jarvis.mobile.core.AgentState
import com.jarvis.mobile.core.RiskLevel
import com.jarvis.mobile.security.RiskEngine
import com.jarvis.mobile.verification.VerificationEngine
import com.jarvis.mobile.verification.VerificationEngine.Evidence
import com.jarvis.mobile.verification.VerificationEngine.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * اختبارات مخطط المهام (Phase 9 — SPEC.md):
 * - الطلب → خطة حتمية، كل خطوة بنية صريحة + عقد تحقق جاهز قبل التنفيذ.
 * - SAFE FAILURE: طلب غير مدعوم يُرفض صراحةً لا بخطة افتراضية.
 * - التكامل: خطة Phase 9 تعبر بوابة Phase 10 وتُتحقق بPhase 11 بلا أي فجوة عقدية.
 */
class TaskPlannerTest {

    private val planner = TaskPlanner()
    private val riskEngine = RiskEngine()
    private val verifier = VerificationEngine()

    // ------------------------------------------------------------------
    // 1) التخطيط الحتمي
    // ------------------------------------------------------------------

    @Test
    fun `نفس الطلب يعطي نفس الخطة دائما - الحتمية`() {
        val a = planner.plan("أنشئ لي ملف pdf للتقرير")
        val b = planner.plan("أنشئ لي ملف pdf للتقرير")
        assertEquals(a, b)
    }

    @Test
    fun `طلب PDF يعطي خطة بخطوات مرتبة وهدف صريح`() {
        val plan = planner.plan("أنشئ لي ملف pdf للتقرير الشهري")
        assertEquals("إنشاء PDF من نص", plan.objective)
        assertEquals(listOf("pdf.resolve-content", "pdf.generate"), plan.steps.map { it.id })
        assertTrue(plan.steps[0].risk == RiskLevel.LOW)
        assertTrue(plan.steps[1].risk == RiskLevel.MEDIUM)
        assertEquals(RiskLevel.MEDIUM, plan.overallRisk)
    }

    @Test
    fun `طلب كم يخطط كحساب - الكلمات العربية تطابق على مستوى الكلمات`() {
        val plan = planner.plan("كم 15 ضرب 4")
        assertEquals("حساب رياضي", plan.objective)
        assertEquals(RiskLevel.LOW, plan.overallRisk)
    }

    @Test
    fun `كلمة تحتوي جذر الكلمة المفتاحية لا تخدع المطابقة`() {
        // "كمثرى" تحتوي "كم" لكنها كلمة مختلفة — يجب ألا تُخطط كحساب
        try {
            planner.plan("اشترِ لي كمثرى")
            org.junit.Assert.fail("كمثرى ليست طلب حساب")
        } catch (e: TaskPlanner.UnsupportedRequestException) {
            // متوقع
        }
    }

    // ------------------------------------------------------------------
    // 2) عقد تحقق لكل خطوة منذ التخطيط — لا فجوة عقدية
    // ------------------------------------------------------------------

    @Test
    fun `كل خطوة في كل خطة تحمل عقد تحقق مطابقا لمعرفها`() {
        for (request in listOf("أنشئ pdf", "احسب 2+2", "اكتب مذكرة سريعة")) {
            val plan = planner.plan(request)
            for (step in plan.steps) {
                assertEquals(
                    "عقد التحقق يجب أن يحمل معرف الخطوة نفسها",
                    step.id,
                    step.verification.stepId,
                )
                assertTrue(step.verification.evidenceRequired.isNotEmpty())
            }
        }
    }

    // ------------------------------------------------------------------
    // 3) SAFE FAILURE: طلب غير مدعوم رفض صريح
    // ------------------------------------------------------------------

    @Test
    fun `طلب غير مدعوم يرفض صراحة - لا خطة افتراضية`() {
        try {
            planner.plan("اطبخ لي البيض")
            org.junit.Assert.fail("كان يجب رفض الطلب غير المدعوم")
        } catch (e: TaskPlanner.UnsupportedRequestException) {
            // SAFE FAILURE متوقع
        }
    }

    // ------------------------------------------------------------------
    // 4) عقد المهمة المشتق
    // ------------------------------------------------------------------

    @Test
    fun `العقد المشتق يحمل أدوات الخطة ومعايير نجاح من عقود التحقق`() {
        val plan = planner.plan("احفظ مذكرة عن الاجتماع")
        val contract = plan.toContract()
        assertEquals("حفظ مذكرة نصية", contract.objective)
        assertEquals(RiskLevel.MEDIUM, contract.risk)
        assertTrue(contract.availableTools.contains("file_writer"))
        assertEquals(
            plan.steps.map { it.verification.expectation },
            contract.successCriteria,
        )
    }

    @Test
    fun `خطوة غير مصنفة الخطورة ترفع خطوة الخطة إلى CRITICAL`() {
        val plan = planner.plan("احسب 2+2")
        val steps = plan.steps + plan.steps.first().copy(risk = null)
        val unclassifiedPlan = plan.copy(steps = steps)
        assertEquals(
            "غير المصنف = CRITICAL — نفس منطق بوابة 10",
            RiskLevel.CRITICAL,
            unclassifiedPlan.overallRisk,
        )
    }

    @Test
    fun `خطوات بلا وصف ترفض`() {
        try {
            planWithBlankDescription()
            org.junit.Assert.fail("كان يجب رفض خطوة بلا وصف")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun `خطة LOW تمر البوابة مباشرة بلا توقف`() {
        val plan = planner.plan("احسب 2+2")
        val engine = RiskEngine()
        engine.machine.dispatch(AgentEvent.UNDERSTAND)
        for (step in plan.steps) {
            val d = engine.gate(step.id, step.risk)
            assertTrue("خطوة LOW تمر البوابة مباشرة", d is RiskEngine.Decision.Allow)
        }
        assertEquals(0, engine.auditLog.count { !it.passedGate })
    }

    // ------------------------------------------------------------------
    // 5) التكامل: خطة 9 → بوابة 10 → تحقق 11 (لا فجوة عقدية)
    // ------------------------------------------------------------------

    @Test
    fun `خطة كاملة تعبر البوابة والتحقق من البداية للنهاية`() {
        val plan = planner.plan("أنشئ لي ملف pdf للتقرير")
        val contract = plan.toContract()

        // محاكاة تنفيذ الخطة خطوة خطوة عبر محركي 10 و11
        var allPassed = true
        for (step in plan.steps) {
            // جولة نظيفة لكل خطوة: الآلة في UNDERSTANDING قبل البوابة
            resetToUnderstanding(riskEngine)
            val decision = riskEngine.gate(step.id, step.risk)
            when (decision) {
                is RiskEngine.Decision.Allow -> { /* منخفضة الخطورة: تمر مباشرة */ }
                is RiskEngine.Decision.RequireConfirmation -> {
                    assertTrue("البوابة يجب أن توقف الآلة فعلياً", riskEngine.awaitingConfirmation)
                    assertTrue(riskEngine.confirm())
                }
                is RiskEngine.Decision.Deny -> allPassed = false
            }

            if (!allPassed) break

            // تحقق 11: الدليل الحقيقي الوحيد هو ما يصنع النجاح
            val evidence = when (step.id) {
                "pdf.resolve-content" -> listOf(
                    Evidence.ResultEvidence("نتيجة تحديد المحتوى", "نص مُستخرج من الطلب", success = true),
                )
                "pdf.generate" -> listOf(
                    Evidence.FileEvidence("حالة الملف", "/docs/تقرير.pdf", exists = true),
                    Evidence.FileEvidence("حجم الملف", "/docs/تقرير.pdf", exists = true, sizeBytes = 4096),
                )
                else -> fail("خطوة غير متوقعة في الخطة: ${step.id}")
            }
            val verdict = verifier.verify(step.verification, evidence)
            if (!verdict.isSuccess) allPassed = false
        }

        assertTrue("المهمة اكتملت عبر البوابة والتحقق", allPassed)
        assertTrue("سجل التدقيق يحوي كل الخطوات", riskEngine.auditLog.size >= plan.steps.size)
        assertTrue("لا قرار رفض في سجل التدقيق", riskEngine.auditLog.none { it.decision is RiskEngine.Decision.Deny })
        assertEquals(contract.objective, plan.objective)
    }

    @Test
    fun `خطة ثنائية الخطوتين تعبر البوابتين بالتأكيد المتعاقب`() {
        val plan = planner.plan("أنشئ لي ملف pdf للتقرير")
        val engine = RiskEngine()
        val verifier2 = VerificationEngine()

        for (step in plan.steps) {
            // لكل خطوة: الآلة في UNDERSTANDING قبل البوابة
            resetToUnderstanding(engine)
            val decision = engine.gate(step.id, step.risk)
            if (decision is RiskEngine.Decision.RequireConfirmation) {
                assertTrue(engine.awaitingConfirmation)
                assertTrue(engine.confirm())
            }
            // الدليل الحقيقي الوحيد هو ما يصنع النجاح
            val evidence = when (step.id) {
                "pdf.resolve-content" -> listOf(
                    Evidence.ResultEvidence("نتيجة تحديد المحتوى", "نص مُستخرج", success = true),
                )
                "pdf.generate" -> listOf(
                    Evidence.FileEvidence("حالة الملف", "/docs/تقرير.pdf", exists = true),
                    Evidence.FileEvidence("حجم الملف", "/docs/تقرير.pdf", exists = true, sizeBytes = 4096),
                )
                else -> org.junit.Assert.fail("خطوة غير متوقعة: ${step.id}")
            }
            assertTrue("الخطوة ${step.id} يجب أن تتحقق", verifier2.verify(step.verification, evidence).isSuccess)
        }
    }

    @Test
    fun `فشل دليل خطوة يمنع نجاح المهمة رغم نجاح سابقتها`() {
        val plan = planner.plan("أنشئ لي ملف pdf للتقرير")
        riskEngine.gate(plan.steps[0].id, plan.steps[0].risk) // Allow
        val good = verifier.verify(
            plan.steps[0].verification,
            listOf(Evidence.ResultEvidence("نتيجة تحديد المحتوى", "نص جاهز", success = true)),
        )
        assertTrue(good.isSuccess)

        riskEngine.machine.dispatch(AgentEvent.UNDERSTAND)
        riskEngine.machine.dispatch(AgentEvent.PLAN)
        riskEngine.gate(plan.steps[1].id, plan.steps[1].risk)
        riskEngine.confirm()

        val bad = verifier.verify(
            plan.steps[1].verification,
            listOf(
                Evidence.FileEvidence("حالة الملف", "/docs/تقرير.pdf", exists = false),
                Evidence.FileEvidence("حجم الملف", "/docs/تقرير.pdf", exists = false, sizeBytes = null),
            ),
        )
        assertTrue("ملف غير موجود = فشل حقيقي", bad is VerificationResult.Failure)
    }

    // ------------------------------------------------------------------
    // مساعدات
    // ------------------------------------------------------------------

    private fun planWithBlankDescription(): TaskPlan {
        val base = planner.plan("احسب 2+2")
        val badStep = base.steps.first().copy(description = "  ")
        return base.copy(steps = listOf(badStep))
    }

    /** يعيد الآلة إلى UNDERSTANDING عبر مسارات الجدول الشرعية فقط — بلا تجاوز للانتقالات. */
    private fun resetToUnderstanding(engine: RiskEngine) {
        val m = engine.machine
        when (m.stateValue) {
            AgentState.UNDERSTANDING -> Unit
            AgentState.EXECUTING -> {
                m.dispatch(AgentEvent.VERIFY)
                m.dispatch(AgentEvent.RESET)
                m.dispatch(AgentEvent.UNDERSTAND)
            }
            else -> {
                m.dispatch(AgentEvent.RESET)
                m.dispatch(AgentEvent.UNDERSTAND)
            }
        }
        assertEquals("الآلة جاهزة في UNDERSTANDING قبل البوابة", AgentState.UNDERSTANDING, m.stateValue)
    }
}
