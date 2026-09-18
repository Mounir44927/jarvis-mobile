package com.jarvis.mobile.core

/**
 * أحداث صريحة تُحدث الانتقالات بين الحالات.
 * لا توجد انتقالات ضمنية: كل تغيير حالة يمر عبر JarvisStateMachine.dispatch.
 */
enum class AgentEvent {
    START_LISTENING,
    STOP_LISTENING,
    TRANSCRIBE,
    UNDERSTAND,
    PLAN,
    EXECUTE,
    VERIFY,
    SPEAK,
    ASK_CONFIRMATION,
    CONFIRMED,
    SUCCESS,
    FAIL,
    BARGE_IN,
    RESET,
}
