package com.jarvis.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد الحالات (Contract Test) — "تم التحقق منه بدليل" لا بالوصف.
 *
 * هذه الاختبارات تربط كود آلة الحالة بالوثائق (README/ARCHITECTURE/SPEC):
 * أي تعديل مستقبلي على قائمة AgentState يجب أن يمر من هنا أولاً،
 * ثم يُحدَّث عدد الحالات في الوثائق في نفس الـ commit.
 * أُنشئ بعد التحقيق في تقرير APK (2026-09-19): الكود الفعلي 11 حالة منذ أول commit — انظر FEATURE_STATUS.md.
 */
class AgentStateContractTest {

    @Test
    fun `آلة الحالة تحوي 11 حالة فقط بالترتيب المتعاقد عليه`() {
        val expected = listOf(
            AgentState.IDLE,
            AgentState.LISTENING,
            AgentState.TRANSCRIBING,
            AgentState.UNDERSTANDING,
            AgentState.PLANNING,
            AgentState.EXECUTING,
            AgentState.VERIFYING,
            AgentState.SPEAKING,
            AgentState.WAITING_CONFIRMATION,
            AgentState.ERROR,
            AgentState.DONE,
        )
        assertEquals(expected, AgentState.entries.toList())
    }

    @Test
    fun `كل حالة لها تسمية عربية غير فارغة`() {
        for (state in AgentState.entries) {
            assertTrue(
                "الحالة ${state.name} بلا تسمية عربية",
                state.arabicLabel.isNotBlank(),
            )
        }
    }

    @Test
    fun `التسميات العربية للحالات فريدة — لا حالتين بنفس الاسم المعروض`() {
        // لوحة حالة المهمة تعرض arabicLabel فقط — تكرار التسمية يجعل حالتين غير قابلتين للتمييز للمستخدم
        val labels = AgentState.entries.map { it.arabicLabel }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `كل حالة تقبل على الأقل RESET للعودة إلى IDLE`() {
        val sm = JarvisStateMachine()
        for (state in AgentState.entries) {
            // نوصل الآلة إلى الحالة عبر مسارها الوحيد الممكن ثم نطلب RESET
            val reached = driveTo(sm, state)
            assertEquals("لا يمكن الوصول إلى $state عبر الجدول", state, reached)
            assertEquals("RESET مرفوض من $state", AgentState.IDLE, sm.dispatch(AgentEvent.RESET))
        }
    }

    /** يقود آلة الحالة من IDLE إلى الحالة المطلوبة عبر مسارها الوحيد في جدول الانتقالات. */
    private fun driveTo(sm: JarvisStateMachine, target: AgentState): AgentState? {
        if (sm.stateValue == target) return target
        val path: List<AgentEvent> = when (target) {
            AgentState.LISTENING -> listOf(AgentEvent.START_LISTENING)
            AgentState.TRANSCRIBING -> listOf(AgentEvent.START_LISTENING, AgentEvent.TRANSCRIBE)
            AgentState.UNDERSTANDING -> listOf(AgentEvent.UNDERSTAND)
            AgentState.PLANNING -> listOf(AgentEvent.UNDERSTAND, AgentEvent.PLAN)
            AgentState.EXECUTING -> listOf(AgentEvent.UNDERSTAND, AgentEvent.EXECUTE)
            AgentState.VERIFYING -> listOf(AgentEvent.UNDERSTAND, AgentEvent.EXECUTE, AgentEvent.VERIFY)
            AgentState.SPEAKING -> listOf(AgentEvent.UNDERSTAND, AgentEvent.SPEAK)
            AgentState.WAITING_CONFIRMATION -> listOf(AgentEvent.UNDERSTAND, AgentEvent.PLAN, AgentEvent.ASK_CONFIRMATION)
            AgentState.ERROR -> listOf(AgentEvent.UNDERSTAND, AgentEvent.FAIL)
            AgentState.DONE -> listOf(AgentEvent.UNDERSTAND, AgentEvent.EXECUTE, AgentEvent.VERIFY, AgentEvent.SUCCESS)
            AgentState.IDLE -> emptyList()
        }
        var result: AgentState? = sm.stateValue
        for (event in path) {
            result = sm.dispatch(event)
            if (result == null) return null // انتقال مرفوض في منتصف المسار
        }
        return result
    }
}
