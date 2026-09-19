package com.jarvis.mobile.security

import com.jarvis.mobile.core.AgentEvent
import com.jarvis.mobile.core.AgentState
import com.jarvis.mobile.core.JarvisStateMachine
import com.jarvis.mobile.core.RiskLevel

/**
 * سياسة التأكيد: أي مستويات خطورة تطلب تأكيد المستخدم قبل التنفيذ (Phase 10).
 * CRITICAL خارج السياسات دائماً: مرفوض مطلقاً في هذه المرحلة — لا قدرة تنفيذية تستحق ذلك بعد
 * (أقصى بوابة = لا مرور؛ توسيعها لاحقاً قرار معمارٍ صريح لا سلوك افتراضي).
 */
enum class ConfirmationPolicy {
    /** MEDIUM وHIGH تطلبان تأكيداً — LOW يُنفَّذ مباشرة (الافتراضية: البوابة إلزامية لكل غير المنخفض). */
    CONFIRM_MEDIUM_AND_ABOVE,

    /** HIGH فقط يطلب تأكيداً — LOW وMEDIUM يُنفَّذان مباشرة. */
    CONFIRM_HIGH_ONLY,

    /** كل المستويات بما فيها LOW تطلب تأكيداً — أشد سياسة تأكيد. */
    CONFIRM_ALL,
}

/**
 * محرك المخاطرة والتأكيد (Phase 10 — SPEC.md: قبل أي فيز قدرة تنفيذية 12-19).
 *
 * العقد:
 * 1. لا خطوة تمر بلا تصنيف خطورة: أي خطوة بلا تصنيف صريح تُصنَّف CRITICAL (التحفظ قبل التفاؤل).
 * 2. سياسة تأكيد صريحة لكل مستوى — جدول القرارات مؤكد باختبار عقد (كل مستوى × كل سياسة).
 * 3. بوابة WAITING_CONFIRMATION إلزامية قبل التنفيذ لكل عملية غير منخفضة الخطورة:
 *    البوابة توقف الآلة فعلياً في WAITING_CONFIRMATION، ولا طريق إلى EXECUTING إلا عبر
 *    [confirm] (CONFIRMED) — آلة الحالة نفسها ترفض أي حدث آخر من تلك الحالة. لا مسار جانبي.
 * 4. CRITICAL مرفوض مطلقاً في هذه المرحلة.
 * 5. كل قرار يُسجَّل في سجل قرارات append-only قابل للتدقيق — القرار غير المُسجَّل لم يحدث.
 *
 * القدرات القادمة (Phases 12-19) تسجّل "نية" فقط هنا ولا تنفّذ إلا عبر [gate].
 */
class RiskEngine(
    private val policy: ConfirmationPolicy = ConfirmationPolicy.CONFIRM_MEDIUM_AND_ABOVE,
    /** آلة الحالة التي تمر عبرها بوابة WAITING_CONFIRMATION — مكشوفة للواجهة والتدقيق والاختبار. */
    val machine: JarvisStateMachine = JarvisStateMachine(),
) {

    /** قرار البوابة لخطوة واحدة. */
    sealed class Decision {
        /** يجوز التنفيذ الآن (منخفضة الخطورة حسب السياسة). */
        data class Allow(val risk: RiskLevel) : Decision()

        /** مطلوب تأكيد المستخدم — الآلة متوقفة في WAITING_CONFIRMATION حتى [confirm] أو [reject]. */
        data class RequireConfirmation(val risk: RiskLevel, val reason: String) : Decision()

        /** مرفوض — لا يُنفَّذ إطلاقاً. */
        data class Deny(val reason: String) : Decision()
    }

    /** عنصر واحد في سجل القرارات. */
    data class DecisionRecord(
        val step: String,
        val risk: RiskLevel,
        val decision: Decision,
        /** هل سمحت البوابة بالتنفيذ فعلياً في هذه الخطوة؟ */
        val passedGate: Boolean,
    )

    private val _auditLog = mutableListOf<DecisionRecord>()

    /** سجل القرارات — للقراءة فقط (append-only: تدقيق بلا تحريف). */
    val auditLog: List<DecisionRecord> get() = _auditLog.toList()

    /** هل البوابة متوقفة الآن بانتظار قرار المستخدم؟ */
    val awaitingConfirmation: Boolean
        get() = machine.stateValue == AgentState.WAITING_CONFIRMATION

    /** الخطوة المعلقة حالياً في البوابة (إن وُجدت). */
    var pendingStep: String? = null
        private set

    /** مستوى خطورة الخطوة المعلقة في البوابة. */
    var pendingRisk: RiskLevel? = null
        private set

    /**
     * تصنيف الخطورة — لا خطوة بلا تصنيف: null تعني CRITICAL.
     */
    fun classify(risk: RiskLevel?): RiskLevel = risk ?: RiskLevel.CRITICAL

    /**
     * قرار السياسة لمستوى خطورة معطى — دالة نقية (لا تلمس الآلة ولا السجل).
     */
    fun decide(risk: RiskLevel?): Decision {
        val level = classify(risk)
        return when {
            level == RiskLevel.CRITICAL ->
                Decision.Deny("CRITICAL مرفوض مطلقاً في هذه المرحلة — لا استثناء (Phase 10)")

            needsConfirmation(level) -> Decision.RequireConfirmation(
                level,
                "سياسة $policy تطلب تأكيد المستخدم لمستوى $level قبل التنفيذ",
            )

            else -> Decision.Allow(level)
        }
    }

    private fun needsConfirmation(level: RiskLevel): Boolean = when (policy) {
        ConfirmationPolicy.CONFIRM_MEDIUM_AND_ABOVE -> level == RiskLevel.MEDIUM || level == RiskLevel.HIGH
        ConfirmationPolicy.CONFIRM_HIGH_ONLY -> level == RiskLevel.HIGH
        ConfirmationPolicy.CONFIRM_ALL -> true
    }

    /**
     * بوابة التنفيذ الإلزامية لخطوة ما.
     *
     * - Allow: تمر فوراً (passedGate=true في السجل).
     * - RequireConfirmation: تقود آلة الحالة فعلياً إلى WAITING_CONFIRMATION وتتوقف هناك —
     *   التنفيذ لا يجوز إلا بعد [confirm]. إن تعذّر بلوغ البوابة من الحالة الحالية
     *   (انتقال مرفوض من آلة الحالة) يُخفَّض القرار إلى Deny — البوابة لا تكذب.
     * - Deny: لا شيء يُنفَّذ.
     *
     * @throws IllegalArgumentException إن كان اسم الخطوة فارغاً — قرار بلا هوية لا يُدقَّق.
     */
    fun gate(step: String, risk: RiskLevel?): Decision {
        require(step.isNotBlank()) { "كل خطوة في البوابة تحتاج اسماً قابلاً للتدقيق" }
        val level = classify(risk)
        return when (val decision = decide(level)) {
            is Decision.Allow -> {
                _auditLog.add(DecisionRecord(step, level, decision, passedGate = true))
                decision
            }

            is Decision.Deny -> {
                _auditLog.add(DecisionRecord(step, level, decision, passedGate = false))
                decision
            }

            is Decision.RequireConfirmation -> {
                val reachedGate = machine.dispatch(AgentEvent.ASK_CONFIRMATION) == AgentState.WAITING_CONFIRMATION
                if (!reachedGate) {
                    val denied = Decision.Deny("تعذّر بلوغ بوابة WAITING_CONFIRMATION من ${machine.stateValue}")
                    _auditLog.add(DecisionRecord(step, level, denied, passedGate = false))
                    return denied
                }
                pendingStep = step
                pendingRisk = level
                _auditLog.add(DecisionRecord(step, level, decision, passedGate = false))
                decision
            }
        }
    }

    /**
     * منح التأكيد عبر البوابة (بعد عرض القرار على المستخدم).
     * لا يمر إلا من WAITING_CONFIRMATION → CONFIRMED → EXECUTING؛ غير ذلك يرفض.
     */
    fun confirm(): Boolean {
        if (!awaitingConfirmation) return false
        val passed = machine.dispatch(AgentEvent.CONFIRMED) == AgentState.EXECUTING
        if (passed) {
            val step = pendingStep ?: "<بلا اسم>"
            val risk = pendingRisk ?: RiskLevel.LOW
            _auditLog.add(
                DecisionRecord(step, risk, Decision.Allow(risk), passedGate = true),
            )
        }
        pendingStep = null
        pendingRisk = null
        return passed
    }

    /**
     * رفض المستخدم عبر البوابة: FAIL → ERROR (مسار آمن معروف في آلة الحالة).
     */
    fun reject(): Boolean {
        if (!awaitingConfirmation) return false
        val rejected = machine.dispatch(AgentEvent.FAIL) == AgentState.ERROR
        pendingStep = null
        pendingRisk = null
        return rejected
    }
}
