package com.jarvis.mobile.core

/**
 * مستويات الخطورة (القسم 22). تُستخدم لاحقاً في Confirmation Engine.
 */
enum class RiskLevel { LOW, MEDIUM, HIGH }

/**
 * حالة المهمة الداخلية المعلنة في القسم 6.
 * تبقى داخلية ولا تُعرض للمستخدم إلا ملخصها عبر Task Panel (القسم 57).
 */
data class TaskState(
    val objective: String = "",
    val context: String = "",
    val plan: List<String> = emptyList(),
    val currentStep: Int = -1,
    val toolsUsed: List<String> = emptyList(),
    val observations: List<String> = emptyList(),
    val expectedResult: String = "",
    val actualResult: String = "",
    val verification: String = "",
    val error: String? = null,
    val recovery: String? = null,
    val finalStatus: TaskStatus = TaskStatus.PENDING,
)

enum class TaskStatus { PENDING, RUNNING, VERIFYING, SUCCEEDED, FAILED, RECOVERED }
