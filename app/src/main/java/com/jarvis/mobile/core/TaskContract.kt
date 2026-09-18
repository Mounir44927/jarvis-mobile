package com.jarvis.mobile.core

/**
 * عقد المهمة (القسم 63): كل مهمة وكيلة تبدأ بهذا العقد.
 * لا تُنشأ مهمة بدون معيار نجاح صريح — أساس Verification Engine في Phase 19.
 */
data class TaskContract(
    val objective: String,
    val constraints: List<String>,
    val availableTools: List<String>,
    val risk: RiskLevel,
    val successCriteria: List<String>,
) {
    init {
        require(objective.isNotBlank()) { "OBJECTIVE مطلوب" }
        require(successCriteria.isNotEmpty()) { "SUCCESS CRITERIA مطلوبة: لا توجد مهمة بدون معيار نجاح صريح" }
    }
}
