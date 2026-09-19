package com.jarvis.mobile.agent

import com.jarvis.mobile.core.RiskLevel
import com.jarvis.mobile.core.TaskContract
import com.jarvis.mobile.verification.VerificationEngine.VerificationContract

/**
 * خطوة مخططة: وحدة تنفيذ واحدة داخل خطة (Phase 9).
 *
 * القاعدة (ADR-10): الخطوة تسجّل "نية" فقط — وصفاً عربياً وأدوات مطلوبة (أسماء حتى Phase 12)
 * ومستوى خطورة مقترحاً وعقد تحقق جاهزاً قبل التنفيذ. لا خطوة بلا عقد تحقق منذ التخطيط.
 *
 * @param risk مستوى الخطورة المقترح؛ null يعني "غير مصنف" — وبوابة Phase 10 تصنّفه CRITICAL
 *            (التحفظ قبل التفاؤل) فيرفضه. المخطط المسؤول يصنّف خطواته صراحةً.
 */
data class PlannedStep(
    val id: String,
    val description: String,
    val tools: List<String>,
    val risk: RiskLevel?,
    val verification: VerificationContract,
) {
    init {
        require(id.isNotBlank()) { "خطوة بلا معرّف مرفوضة" }
        require(description.isNotBlank()) { "خطوة بلا وصف مرفوضة — النية يجب أن تكون صريحة" }
        require(tools.isNotEmpty()) { "خطوة بلا أدوات مرفوضة — كل خطوة تُصرّح بما ستحتاجه لتنفيذ نيتها (أسماء حتى Phase 12)" }
    }
}

/**
 * خطة مهمة كاملة: عقد المهمة (القسم 63) + خطواته المرتبة، كل خطوة بعقد تحقق.
 *
 * @param contract عقد المهمة: هدف/قيود/أدوات/خطورة/معايير نجاح — يُبنى من الخطوات نفسها
 *                 (المخطط لا يخترع معايير نجاح خارج عقود التحقق الخاصة بالخطوات).
 */
data class TaskPlan(
    val objective: String,
    val constraints: List<String>,
    val steps: List<PlannedStep>,
) {
    init {
        require(objective.isNotBlank()) { "خطة بلا هدف مرفوضة" }
        require(steps.isNotEmpty()) { "خطة بلا خطوات مرفوضة" }
        require(steps.map { it.id }.toSet().size == steps.size) { "معرّفات الخطوات يجب أن تكون فريدة" }
    }

    /** أعلى خطورة بين الخطوات — غير المصنف يُحتسب CRITICAL (نفس منطق بوابة 10). */
    val overallRisk: RiskLevel
        get() = steps.maxOf { it.risk ?: RiskLevel.CRITICAL }

    /** عقد المهمة المشتق من الخطوات — معايير النجاح هي توقعات عقود التحقق حرفياً. */
    fun toContract(): TaskContract = TaskContract(
        objective = objective,
        constraints = constraints,
        availableTools = steps.flatMap { it.tools }.distinct(),
        risk = overallRisk,
        successCriteria = steps.map { it.verification.expectation },
    )
}
