package com.jarvis.mobile.core

/**
 * حالات Jarvis المعلنة في العقد الهندسي (القسم 53).
 * أي انتقال غير منطقي مرفوض — انظر JarvisStateMachine.
 */
enum class AgentState(val arabicLabel: String) {
    IDLE("أنا جاهز."),
    LISTENING("أستمع إليك…"),
    TRANSCRIBING("أحوّل كلامك إلى نص…"),
    UNDERSTANDING("أفهم طلبك."),
    PLANNING("أخطط للمهمة…"),
    EXECUTING("أنفذ المهمة…"),
    VERIFYING("أتحقق من النتيجة…"),
    SPEAKING("أتحدث…"),
    WAITING_CONFIRMATION("بانتظار تأكيدك…"),
    ERROR("حدث خطأ."),
    DONE("تم."),
}
