package com.jarvis.mobile.tools

import com.jarvis.mobile.core.RiskLevel
import com.jarvis.mobile.security.RiskEngine

/**
 * فئة الأداة (Phase 12). كل الفئات تنفيذية — لذلك كل أداة تبدأ disabled وتُفعَّل عبر بوابة 10 فقط.
 */
enum class ToolCategory { FILE, SYSTEM, NETWORK, MEDIA, COMPUTE }

/**
 * تعريف أداة مسجلة: تسجيل نية فقط — لا تنفيذ هنا (ADR-10: disabled-by-default).
 * التنفيذ الفعلي يُبنى في Phases 13-19 خلف هذا التسجيل، وبلا تفعيل عبر البوابة لا وجود له.
 *
 * @param riskLevel أعلى خطورة متوقعة لاستخدامات الأداة.
 * @param enabled مفعّلة؟ دائماً false عند التسجيل؛ لا يقلبها إلا [ToolRegistry.enableThroughGate]
 *                بعد نجاح بوابة Phase 10 — لا مسار آخر (لا setter عام).
 */
data class ToolSpec(
    val name: String,
    val category: ToolCategory,
    val riskLevel: RiskLevel,
    val description: String,
    val enabled: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "أداة بلا اسم مرفوضة" }
        require(description.isNotBlank()) { "أداة بلا وصف مرفوضة — النية يجب أن تكون موثقة" }
    }
}

/**
 * سجل الأدوات + التقييم (Phase 12 — أول فيز قدرة تنفيذية، مبني فوق بوابة 10/11 المعتمدتين).
 *
 * العقد (SPEC Phase 12):
 * 1. كل أداة تُسجَّل disabled-by-default ولا تؤثر على الجهاز إلا عبر بوابة المخاطرة والتأكيد.
 * 2. التفعيل يمر حصراً عبر [enableThroughGate]: بوابة 10 على الخطوة المستهلكة للأداة —
 *    إن توقفت البوابة للتأكيد يبقى التفعيل معلقاً حتى [completePendingEnable] بعد منح التأكيد
 *    **فعلياً عبر البوابة** (سجل تدقيق البوابة هو الدليل — لا مسار جانبي بعد رفض المستخدم).
 * 3. CRITICAL يُرفض مطلقاً في هذه المرحلة — لا أداة بهذه الخطورة تُفعَّل.
 * 4. [score] حتمي: نفس المدخلات → نفس النتيجة (بلا زمن ولا عشوائية).
 * 5. أداة disabled لا تُرجع من [eligibleFor] أبداً مهما كانت نتيجتها.
 */
class ToolRegistry {

    /** قرار التفعيل لأداة. */
    sealed class EnableDecision {
        /** مفعّلة الآن. */
        data class Enabled(val tool: String) : EnableDecision()

        /** معلقة على تأكيد المستخدم عبر البوابة — أكمل بـ [completePendingEnable]. */
        data class PendingConfirmation(val tool: String, val reason: String) : EnableDecision()

        /** مرفوضة — تبقى disabled. */
        data class Refused(val tool: String, val reason: String) : EnableDecision()

        /** كانت مفعّلة أصلاً. */
        data class AlreadyEnabled(val tool: String) : EnableDecision()
    }

    private val tools = linkedMapOf<String, ToolSpec>()

    // حالة التفعيل المعلق على تأكيد المستخدم — تُضبط فقط من [enableThroughGate] وتُمسح عند الحسم.
    private var pendingEnableName: String? = null
    private var pendingEnableStep: String? = null
    private var pendingEnableEngine: RiskEngine? = null

    /** الأدوات المسجلة (نسخة للقراءة). */
    val all: List<ToolSpec> get() = tools.values.toList()

    /**
     * تسجيل أداة جديدة. تُسجَّل disabled دائماً حتى لو مُرّرت enabled=true بالخطأ —
     * الحالة الوحيدة للتفعيل عبر بوابة 10.
     *
     * @throws IllegalArgumentException إن كان الاسم مستخدماً.
     */
    fun register(spec: ToolSpec): ToolSpec {
        require(spec.name !in tools) { "أداة باسم ${spec.name} مسجلة مسبقاً" }
        val registered = spec.copy(enabled = false)
        tools[registered.name] = registered
        return registered
    }

    fun byName(name: String): ToolSpec? = tools[name]

    /** هل يمكن للأداة المطلوبة أن تُستخدم الآن؟ disabled → لا أبداً. */
    fun isEnabled(name: String): Boolean = tools[name]?.enabled == true

    /**
     * تفعيل أداة عبر بوابة Phase 10 (المسار الوحيد للتفعيل).
     *
     * @return نتيجة البوابة: Allow → مفعّلة فوراً؛ RequireConfirmation → معلقة حتى
     *         [completePendingEnable] بعد تأكيد المستخدم؛ Deny → مرفوضة وتبقى disabled.
     * @throws IllegalArgumentException إن لم تكن الأداة مسجلة.
     */
    fun enableThroughGate(toolName: String, stepName: String, risk: RiskLevel?, engine: RiskEngine): EnableDecision {
        val tool = tools[toolName]
            ?: throw IllegalArgumentException("أداة غير مسجلة: $toolName")
        if (tool.enabled) return EnableDecision.AlreadyEnabled(tool.name)

        return when (val decision = engine.gate(stepName, risk)) {
            is RiskEngine.Decision.Allow -> {
                tools[toolName] = tool.copy(enabled = true)
                EnableDecision.Enabled(toolName)
            }
            is RiskEngine.Decision.RequireConfirmation -> {
                pendingEnableName = toolName
                pendingEnableStep = stepName
                pendingEnableEngine = engine
                EnableDecision.PendingConfirmation(toolName, decision.reason)
            }
            is RiskEngine.Decision.Deny ->
                EnableDecision.Refused(toolName, decision.reason)
        }
    }

    /**
     * إتمام تفعيل معلق بعد قرار المستخدم عبر البوابة.
     *
     * العقد: لا شيء يُفعَّل إلا بوجود تفعيل معلق من [enableThroughGate] **لنفس البوابة**
     * (نفس محرك المخاطرة)، وبوجود دليل منح تأكيد فعلي في سجل تدقيق البوابة — آخر سجل
     * يجب أن يكون سجل تأكيد ناجحاً (passedGate=true) لنفس الخطوة. رفض المستخدم أو غياب
     * الدليل → رفض نهائي ويُمسح التفعيل المعلق (لا إعادة محاولة بلا بوابة جديدة).
     *
     * @throws IllegalArgumentException إن لم تكن الأداة مسجلة.
     */
    fun completePendingEnable(toolName: String, engine: RiskEngine, confirmed: Boolean): EnableDecision {
        val tool = tools[toolName]
            ?: throw IllegalArgumentException("أداة غير مسجلة: $toolName")
        if (tool.enabled) return EnableDecision.AlreadyEnabled(toolName)

        if (pendingEnableName != toolName || pendingEnableEngine !== engine) {
            return EnableDecision.Refused(toolName, "لا تفعيل معلق عبر هذه البوابة لهذه الأداة")
        }
        val step = pendingEnableStep ?: ""
        val confirmationEvidence = engine.auditLog.lastOrNull()
            ?.let { it.step == step && it.passedGate } == true

        pendingEnableName = null
        pendingEnableStep = null
        pendingEnableEngine = null

        if (!confirmed || !confirmationEvidence) {
            return EnableDecision.Refused(toolName, "لم يُمنح التأكيد عبر البوابة — يبقى التفعيل مرفوضاً")
        }
        tools[toolName] = tool.copy(enabled = true)
        return EnableDecision.Enabled(toolName)
    }

    /** إلغاء تفعيل (للتشخيص/التقاعد الآمن) — ويمسح أي تفعيل معلق لنفس الأداة. */
    fun disable(name: String) {
        val tool = tools[name] ?: return
        tools[name] = tool.copy(enabled = false)
        if (pendingEnableName == name) {
            pendingEnableName = null
            pendingEnableStep = null
            pendingEnableEngine = null
        }
    }

    // ------------------------------------------------------------------
    // التقييم الحتمي (Scoring)
    // ------------------------------------------------------------------

    /**
     * نتيجة تقييم أداة لمهمة ما — كل المكوّنات في [0,1].
     */
    data class Score(
        val tool: ToolSpec,
        val relevance: Float,
        val availability: Float,
        val permission: Float,
        val cost: Float,
        val latency: Float,
        val reliability: Float,
        val risk: Float,
    ) {
        /** الدرجة الكلية الموزونة — حتمية. */
        fun total(): Float =
            0.35f * relevance +
                0.10f * availability +
                0.15f * permission +
                0.10f * cost +
                0.10f * latency +
                0.10f * reliability +
                0.10f * risk
    }

    /** مدخلات التقييم: الأدوات المطلوبة للاستعمال، والصلاحيات المتاحة حالياً، وبيانات الأداء. */
    data class ScoringContext(
        val neededToolNames: Set<String>,
        val grantedPermissions: Set<String> = emptySet(),
        val toolPermissions: Map<String, Set<String>> = emptyMap(),
        val toolCosts: Map<String, Float> = emptyMap(),
        val toolLatencies: Map<String, Float> = emptyMap(),
        val toolReliabilities: Map<String, Float> = emptyMap(),
    )

    /**
     * تقييم كل الأدوات المسجلة لسياق معطى — حتمي بالكامل، مرتبة تنازلياً بالدرجة الكلية
     * (كسر التعادل بالاسم الأبجدي لتبقى الترتيبات مستقرة ومتوقعة).
     * أداة disabled تعيد availability=0 (ولا تُرجع من [eligibleFor] أصلاً).
     */
    fun score(context: ScoringContext): List<Score> =
        all.map { tool -> scoreTool(tool, context) }
            .sortedWith(compareByDescending<Score> { it.total() }.thenBy { it.tool.name })

    /**
     * الأدوات المؤهلة فعلاً للاستخدام الآن: مفعّلة + ضمن المطلوب.
     * أداة disabled لا تُستدعى مهما كانت نتيجتها — هذا هو الملزِم العقدي.
     */
    fun eligibleFor(context: ScoringContext): List<Score> =
        score(context).filter { it.tool.enabled && it.tool.name in context.neededToolNames }

    private fun scoreTool(tool: ToolSpec, ctx: ScoringContext): Score {
        val relevance = if (tool.name in ctx.neededToolNames) 1f else 0f
        val availability = if (tool.enabled) 1f else 0f
        val required = ctx.toolPermissions[tool.name] ?: emptySet()
        val permission = if (required.isEmpty()) 1f
        else required.count { it in ctx.grantedPermissions }.toFloat() / required.size
        val risk = when (tool.riskLevel) {
            RiskLevel.LOW -> 1f
            RiskLevel.MEDIUM -> 0.6f
            RiskLevel.HIGH -> 0.3f
            RiskLevel.CRITICAL -> 0f
        }
        return Score(
            tool = tool,
            relevance = relevance,
            availability = availability,
            permission = permission,
            cost = 1f - (ctx.toolCosts[tool.name] ?: 0f).coerceIn(0f, 1f),
            latency = 1f - (ctx.toolLatencies[tool.name] ?: 0f).coerceIn(0f, 1f),
            reliability = (ctx.toolReliabilities[tool.name] ?: 1f).coerceIn(0f, 1f),
            risk = risk,
        )
    }
}
