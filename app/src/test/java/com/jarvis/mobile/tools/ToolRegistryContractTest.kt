package com.jarvis.mobile.tools

import com.jarvis.mobile.core.RiskLevel
import com.jarvis.mobile.security.RiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات عقد ToolRegistry (Phase 12 — أول فيز قدرة تنفيذية، SPEC.md):
 * 1. كل أداة تُسجَّل disabled-by-default — لا استثناء حتى لو مُرّرت enabled=true.
 * 2. التفعيل يمر حصراً عبر بوابة المخاطرة (Phase 10) — لا مسار جانبي.
 * 3. CRITICAL مرفوض مطلقاً.
 * 4. score حتمي: نفس المدخلات → نفس النتيجة (بلا زمن ولا عشوائية).
 * 5. أداة disabled لا تُستدعى مهما كانت نتيجتها.
 */
class ToolRegistryContractTest {

    private fun engineReadyForGate(): RiskEngine =
        RiskEngine().apply {
            machine.dispatch(com.jarvis.mobile.core.AgentEvent.UNDERSTAND)
            machine.dispatch(com.jarvis.mobile.core.AgentEvent.PLAN)
        }

    private fun tool(
        name: String = "أداة_تجريبية",
        risk: RiskLevel = RiskLevel.LOW,
        category: ToolCategory = ToolCategory.FILE,
        enabled: Boolean = false,
    ) = ToolSpec(
        name = name,
        category = category,
        riskLevel = risk,
        description = "وصف موثق للنية — $name",
        enabled = enabled,
    )

    // ------------------------------------------------------------------
    // 1) disabled-by-default
    // ------------------------------------------------------------------

    @Test
    fun `كل أداة تُسجل معطلة حتى لو مُررت enabled=true بالخطأ`() {
        val registry = ToolRegistry()
        val spec = tool(enabled = true) // محاولة تمرير مفعّلة

        val registered = registry.register(spec)

        assertFalse("التسجيل يجبر disabled — لا يثق بالمدخل", registered.enabled)
        assertFalse(registry.isEnabled(spec.name))
    }

    @Test
    fun `تسجيل اسم مكرر يُرفض`() {
        val registry = ToolRegistry()
        registry.register(tool())

        try {
            registry.register(tool())
            org.junit.Assert.fail("كان يجب رفض الاسم المكرر")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }

    @Test
    fun `أداة بلا اسم أو بلا وصف نية مرفوضة`() {
        try {
            ToolSpec(name = "  ", category = ToolCategory.FILE, riskLevel = RiskLevel.LOW, description = "وصف")
            org.junit.Assert.fail("اسم فارغ مرفوض")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
        try {
            ToolSpec(name = "أداة", category = ToolCategory.FILE, riskLevel = RiskLevel.LOW, description = "  ")
            org.junit.Assert.fail("وصف فارغ مرفوض — النية يجب أن تكون موثقة")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }

    // ------------------------------------------------------------------
    // 2) التفعيل حصرياً عبر بوابة Phase 10
    // ------------------------------------------------------------------

    @Test
    fun `تفعيل أداة LOW عبر البوابة يمر مباشرة ويفعلها فعليا`() {
        val registry = ToolRegistry()
        registry.register(tool(risk = RiskLevel.LOW))
        val engine = engineReadyForGate()

        val decision = registry.enableThroughGate("أداة_تجريبية", "خطوة قراءة ملف", RiskLevel.LOW, engine)

        assertTrue(decision is ToolRegistry.EnableDecision.Enabled)
        assertTrue("بعد Allow يجب أن تكون الأداة مفعّلة فعلاً", registry.isEnabled("أداة_تجريبية"))
        assertNotNull(engine.auditLog.singleOrNull { it.step == "خطوة قراءة ملف" })
    }

    @Test
    fun `تفعيل أداة MEDIUM يتوقف في WAITING_CONFIRMATION ثم يُكتمل بعد التأكيد فقط`() {
        val registry = ToolRegistry()
        registry.register(tool(risk = RiskLevel.MEDIUM))
        val engine = engineReadyForGate()

        val decision = registry.enableThroughGate("أداة_تجريبية", "خطوة حساسة", RiskLevel.MEDIUM, engine)

        assertTrue(decision is ToolRegistry.EnableDecision.PendingConfirmation)
        assertFalse("لا تفعيل قبل تأكيد المستخدم", registry.isEnabled("أداة_تجريبية"))

        // محاولة إتمام بلا تأكيد → رفض
        val refused = registry.completePendingEnable("أداة_تجريبية", engine, confirmed = false)
        assertTrue(refused is ToolRegistry.EnableDecision.Refused)
        assertFalse(registry.isEnabled("أداة_تجريبية"))

        // بعد رفض الإتمام يُمسح التعلق — لا إعادة محاولة بلا بوابة جديدة (لا مسار جانبي)
        assertTrue(engine.confirm())
        val afterSweep = registry.completePendingEnable("أداة_تجريبية", engine, confirmed = true)
        assertTrue(afterSweep is ToolRegistry.EnableDecision.Refused)
        assertFalse(registry.isEnabled("أداة_تجريبية"))

        // التأكيد عبر البوابة ثم الإتمام هو الطريق الوحيد
        // (بعد التأكيد الأول الآلة في EXECUTING — نُكمل الدورة ونعود إلى مسار التخطيط قبل بوابة جديدة)
        engine.machine.dispatch(com.jarvis.mobile.core.AgentEvent.VERIFY)
        engine.machine.dispatch(com.jarvis.mobile.core.AgentEvent.SUCCESS)
        engine.machine.dispatch(com.jarvis.mobile.core.AgentEvent.RESET)
        engine.machine.dispatch(com.jarvis.mobile.core.AgentEvent.UNDERSTAND)
        engine.machine.dispatch(com.jarvis.mobile.core.AgentEvent.PLAN)
        registry.enableThroughGate("أداة_تجريبية", "خطوة حساسة", RiskLevel.MEDIUM, engine)
        assertTrue(engine.confirm())
        val completed = registry.completePendingEnable("أداة_تجريبية", engine, confirmed = true)
        assertTrue(completed is ToolRegistry.EnableDecision.Enabled)
        assertTrue(registry.isEnabled("أداة_تجريبية"))
    }

    @Test
    fun `رفض المستخدم في البوابة يبقي الأداة معطلة`() {
        val registry = ToolRegistry()
        registry.register(tool(risk = RiskLevel.HIGH))
        val engine = engineReadyForGate()

        assertTrue(registry.enableThroughGate("أداة_تجريبية", "خطوة خطرة", RiskLevel.HIGH, engine)
            is ToolRegistry.EnableDecision.PendingConfirmation)
        assertTrue(engine.reject())

        // حتى لو أُرسل confirmed=true بعد رفض المستخدم: آخر سجل في سجل البوابة ليس تأكيداً ناجحاً → رفض نهائي
        val completed = registry.completePendingEnable("أداة_تجريبية", engine, confirmed = true)
        assertTrue("رفض المستخدم في البوابة لا يُفعّل الأداة مهما جاء لاحقاً — لا مسار جانبي",
            completed is ToolRegistry.EnableDecision.Refused)
        assertFalse(registry.isEnabled("أداة_تجريبية"))

        // إتمام من بوابة أخرى (محرك مختلف) يُرفض أيضاً
        val otherEngine = engineReadyForGate()
        val fromOtherGate = registry.completePendingEnable("أداة_تجريبية", otherEngine, confirmed = true)
        assertTrue(fromOtherGate is ToolRegistry.EnableDecision.Refused)
        assertFalse(registry.isEnabled("أداة_تجريبية"))
    }

    @Test
    fun `أداة CRITICAL تُرفض من البوابة وتبقى معطلة أبدا`() {
        val registry = ToolRegistry()
        registry.register(tool(risk = RiskLevel.CRITICAL))
        val engine = engineReadyForGate()

        val decision = registry.enableThroughGate("أداة_تجريبية", "خطوة حرجة", RiskLevel.CRITICAL, engine)

        assertTrue(decision is ToolRegistry.EnableDecision.Refused)
        assertFalse(registry.isEnabled("أداة_تجريبية"))
    }

    @Test
    fun `تفعيل أداة غير مسجلة يُرفض باستثناء`() {
        val registry = ToolRegistry()
        val engine = engineReadyForGate()
        try {
            registry.enableThroughGate("غير_موجودة", "خطوة", RiskLevel.LOW, engine)
            org.junit.Assert.fail("كان يجب رفض أداة غير مسجلة")
        } catch (e: IllegalArgumentException) {
            // متوقع
        }
    }

    @Test
    fun `إعادة تفعيل أداة مفعّلة تعيد AlreadyEnabled بلا تكرار في سجل البوابة`() {
        val registry = ToolRegistry()
        registry.register(tool(risk = RiskLevel.LOW))
        val engine = engineReadyForGate()

        assertTrue(registry.enableThroughGate("أداة_تجريبية", "خطوة أولى", RiskLevel.LOW, engine)
            is ToolRegistry.EnableDecision.Enabled)
        val logSize = engine.auditLog.size

        val again = registry.enableThroughGate("أداة_تجريبية", "خطوة ثانية", RiskLevel.LOW, engine)
        assertTrue(again is ToolRegistry.EnableDecision.AlreadyEnabled)
        assertEquals("لا قرار بوابة جديد لأداة مفعّلة أصلاً", logSize, engine.auditLog.size)
    }

    // ------------------------------------------------------------------
    // 3) Scoring حتمي
    // ------------------------------------------------------------------

    @Test
    fun `scoring حتمي - نفس المدخلات تعطي نفس الترتيب والنتائج`() {
        fun build(): Pair<ToolRegistry, ToolRegistry.ScoringContext> {
            val registry = ToolRegistry()
            registry.register(tool("أداة_أ", risk = RiskLevel.LOW))
            registry.register(tool("أداة_ب", risk = RiskLevel.MEDIUM))
            registry.register(tool("أداة_ج", risk = RiskLevel.HIGH))
            val ctx = ToolRegistry.ScoringContext(
                neededToolNames = setOf("أداة_أ", "أداة_ب"),
                toolLatencies = mapOf("أداة_أ" to 0.2f, "أداة_ب" to 0.5f),
            )
            return registry to ctx
        }

        val (r1, c1) = build()
        val (r2, c2) = build()

        val s1 = r1.score(c1)
        val s2 = r2.score(c2)

        assertEquals(s1.map { it.tool.name }, s2.map { it.tool.name })
        assertEquals(s1.map { it.total() }, s2.map { it.total() })
    }

    @Test
    fun `الأداة الأكثر صلة والمنخفضة الخطورة تتقدم في الترتيب`() {
        val registry = ToolRegistry()
        registry.register(tool("منخفضة", risk = RiskLevel.LOW))
        registry.register(tool("حرجة", risk = RiskLevel.CRITICAL))
        val ctx = ToolRegistry.ScoringContext(neededToolNames = setOf("منخفضة", "حرجة"))

        val scores = registry.score(ctx)
        assertEquals("منخفضة", scores.first().tool.name)
        assertEquals("حرجة", scores.last().tool.name)
    }

    @Test
    fun `كسر التعادل بالاسم يضمن ترتيبا مستقرا`() {
        val registry = ToolRegistry()
        // أتان متطابقة تماماً في كل المكوّنات
        registry.register(tool("ب_أداة", risk = RiskLevel.LOW))
        registry.register(tool("أ_أداة", risk = RiskLevel.LOW))
        val ctx = ToolRegistry.ScoringContext(neededToolNames = setOf("ب_أداة", "أ_أداة"))

        val names = registry.score(ctx).map { it.tool.name }
        assertEquals(listOf("أ_أداة", "ب_أداة"), names)
    }

    @Test
    fun `كل مكوّنات النتيجة في المدى الصحيح صفر إلى واحد`() {
        val registry = ToolRegistry()
        registry.register(tool("أداة", risk = RiskLevel.HIGH))
        val ctx = ToolRegistry.ScoringContext(
            neededToolNames = setOf("أداة"),
            toolCosts = mapOf("أداة" to 5f), // قيمة خارج المدى عمداً
            toolLatencies = mapOf("أداة" to -1f),
            toolReliabilities = mapOf("أداة" to 0.7f),
        )

        val score = registry.score(ctx).single()
        for (value in listOf(score.relevance, score.availability, score.permission, score.cost, score.latency, score.reliability, score.risk)) {
            assertTrue("المكوّن خارج [0,1]: $value", value in 0f..1f)
        }
    }

    // ------------------------------------------------------------------
    // 4) العقد الأهم: أداة disabled لا تُستدعى أبداً
    // ------------------------------------------------------------------

    @Test
    fun `أداة disabled لا تُرجع من eligibleFor مهما كانت نتيجتها`() {
        val registry = ToolRegistry()
        registry.register(tool("مفعّلة", risk = RiskLevel.LOW))
        registry.register(tool("معطلة", risk = RiskLevel.LOW))
        // تفعيل الأولى عبر بوابة 10 (LOW يمر مباشرة بلا إيقاف)
        registry.enableThroughGate("مفعّلة", "خطوة تفعيل", RiskLevel.LOW, RiskEngine())
        val ctx = ToolRegistry.ScoringContext(neededToolNames = setOf("مفعّلة", "معطلة"))

        val eligible = registry.eligibleFor(ctx)
        assertEquals(listOf("مفعّلة"), eligible.map { it.tool.name })
        assertFalse("المعطلة لا تُرجع أبداً", eligible.any { it.tool.name == "معطلة" })
    }

    @Test
    fun `أداة مفعّلة لكن خارج المطلوب لا تُرجع من eligibleFor أيضا`() {
        val registry = ToolRegistry()
        registry.register(tool("مطلوبة", risk = RiskLevel.LOW))
        registry.register(tool("غير_مطلوبة", risk = RiskLevel.LOW))
        // تفعيل المطلوبة فقط عبر بوابة 10
        registry.enableThroughGate("مطلوبة", "خطوة تفعيل", RiskLevel.LOW, RiskEngine())
        val ctx = ToolRegistry.ScoringContext(neededToolNames = setOf("مطلوبة"))

        val eligible = registry.eligibleFor(ctx)
        assertEquals(listOf("مطلوبة"), eligible.map { it.tool.name })
    }

    // ------------------------------------------------------------------
    // 5) التكامل مع بوابة 10 والتحقق من خلو الواجهة من الثغرات
    // ------------------------------------------------------------------

    @Test
    fun `disable تعيد الأداة إلى معطلة ولا تظهر في eligibleFor`() {
        val registry = ToolRegistry()
        registry.register(tool(risk = RiskLevel.LOW))
        val engine = engineReadyForGate()
        registry.enableThroughGate("أداة_تجريبية", "خطوة", RiskLevel.LOW, engine)
        assertTrue(registry.isEnabled("أداة_تجريبية"))

        registry.disable("أداة_تجريبية")
        assertFalse(registry.isEnabled("أداة_تجريبية"))
        assertNull(registry.eligibleFor(ToolRegistry.ScoringContext(neededToolNames = setOf("أداة_تجريبية")))
            .firstOrNull { it.tool.name == "أداة_تجريبية" })
    }

    @Test
    fun `بوابة 10 تحسب قرار التفعيل في سجل التدقيق - قابلية تدقيق كاملة`() {
        val registry = ToolRegistry()
        registry.register(tool(risk = RiskLevel.MEDIUM))
        val engine = engineReadyForGate()

        registry.enableThroughGate("أداة_تجريبية", "خطوة وثيقة", RiskLevel.MEDIUM, engine)
        engine.confirm()

        // قرار البوابة + قرار التأكيد كلاهما في السجل، والإتمام تم على أساسهما
        assertTrue(registry.completePendingEnable("أداة_تجريبية", engine, confirmed = true)
            is ToolRegistry.EnableDecision.Enabled)
        assertEquals(2, engine.auditLog.size)
        assertEquals("خطوة وثيقة", engine.auditLog[0].step)
        assertEquals("خطوة وثيقة", engine.auditLog[1].step)
        assertTrue(engine.auditLog[1].passedGate)
    }
}
