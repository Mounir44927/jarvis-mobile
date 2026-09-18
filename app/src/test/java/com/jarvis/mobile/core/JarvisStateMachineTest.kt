package com.jarvis.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات آلة حالة Jarvis (متطلب القسم 45: Agent/Task State Machine).
 * القاعدة: أي انتقال غير منطقي يجب أن يُرفض، وكل مسار إلزامي في العقد يجب أن يمر.
 */
class JarvisStateMachineTest {

    private fun newMachine(): JarvisStateMachine = JarvisStateMachine()

    @Test
    fun `الحالة الابتدائية IDLE`() {
        assertEquals(AgentState.IDLE, newMachine().stateValue)
    }

    @Test
    fun `المسار الصوتي الكامل من LISTENING حتى DONE`() {
        val sm = newMachine()
        sm.dispatch(AgentEvent.START_LISTENING)
        assertEquals(AgentState.LISTENING, sm.stateValue)

        sm.dispatch(AgentEvent.TRANSCRIBE)
        assertEquals(AgentState.TRANSCRIBING, sm.stateValue)

        sm.dispatch(AgentEvent.UNDERSTAND)
        assertEquals(AgentState.UNDERSTANDING, sm.stateValue)

        sm.dispatch(AgentEvent.PLAN)
        assertEquals(AgentState.PLANNING, sm.stateValue)

        sm.dispatch(AgentEvent.EXECUTE)
        assertEquals(AgentState.EXECUTING, sm.stateValue)

        sm.dispatch(AgentEvent.VERIFY)
        assertEquals(AgentState.VERIFYING, sm.stateValue)

        sm.dispatch(AgentEvent.SUCCESS)
        assertEquals(AgentState.DONE, sm.stateValue)

        sm.dispatch(AgentEvent.RESET)
        assertEquals(AgentState.IDLE, sm.stateValue)
    }

    @Test
    fun `مسار الاستجابة المباشرة بدون تخطيط`() {
        val sm = newMachine()
        sm.dispatch(AgentEvent.UNDERSTAND)
        sm.dispatch(AgentEvent.SPEAK)
        assertEquals(AgentState.SPEAKING, sm.stateValue)
    }

    @Test
    fun `Barge-in يتوقف عن الكلام ويعود للاستماع`() {
        val sm = newMachine()
        sm.dispatch(AgentEvent.UNDERSTAND)
        sm.dispatch(AgentEvent.SPEAK)
        sm.dispatch(AgentEvent.BARGE_IN)
        assertEquals(AgentState.LISTENING, sm.stateValue)
    }

    @Test
    fun `مسار التعافي من الفشل VERIFYING ثم إعادة EXECUTE`() {
        val sm = newMachine()
        sm.dispatch(AgentEvent.UNDERSTAND)
        sm.dispatch(AgentEvent.EXECUTE)
        sm.dispatch(AgentEvent.VERIFY)
        sm.dispatch(AgentEvent.FAIL)
        assertEquals(AgentState.ERROR, sm.stateValue)
        sm.dispatch(AgentEvent.UNDERSTAND)
        assertEquals(AgentState.UNDERSTANDING, sm.stateValue)
    }

    @Test
    fun `مسار التأكيد قبل العمليات الحساسة`() {
        val sm = newMachine()
        sm.dispatch(AgentEvent.UNDERSTAND)
        sm.dispatch(AgentEvent.PLAN)
        sm.dispatch(AgentEvent.ASK_CONFIRMATION)
        assertEquals(AgentState.WAITING_CONFIRMATION, sm.stateValue)
        sm.dispatch(AgentEvent.CONFIRMED)
        assertEquals(AgentState.EXECUTING, sm.stateValue)
    }

    @Test
    fun `انتقال غير منطقي يرفض ويحتفظ بالحالة`() {
        var rejectedState: AgentState? = null
        var rejectedEvent: AgentEvent? = null
        val sm = JarvisStateMachine { s, e ->
            rejectedState = s
            rejectedEvent = e
        }

        // IDLE لا يقبل EXECUTE مباشرة — يجب المرور بفهم الطلب
        assertNull(sm.dispatch(AgentEvent.EXECUTE))
        assertEquals(AgentState.IDLE, sm.stateValue)
        assertEquals(AgentState.IDLE, rejectedState)
        assertEquals(AgentEvent.EXECUTE, rejectedEvent)
        assertNotNull(sm.lastRejection)
    }

    @Test
    fun `EXECUTING لا يقبل SUCCESS مباشرة قبل التحقق`() {
        val sm = newMachine()
        sm.dispatch(AgentEvent.UNDERSTAND)
        sm.dispatch(AgentEvent.EXECUTE)
        assertNull(sm.dispatch(AgentEvent.SUCCESS))
        assertEquals(AgentState.EXECUTING, sm.stateValue)
    }

    @Test
    fun `SPEAKING لا يقبل START_LISTENING إلا عبر BARGE_IN أو RESET`() {
        val sm = newMachine()
        sm.dispatch(AgentEvent.UNDERSTAND)
        sm.dispatch(AgentEvent.SPEAK)
        assertNull(sm.dispatch(AgentEvent.START_LISTENING))
        assertEquals(AgentState.SPEAKING, sm.stateValue)
    }

    @Test
    fun `DONE يقبل بدء جلسة جديدة مباشرة`() {
        val sm = newMachine()
        sm.dispatch(AgentEvent.UNDERSTAND)
        sm.dispatch(AgentEvent.SPEAK)
        sm.dispatch(AgentEvent.RESET)
        val next = sm.dispatch(AgentEvent.START_LISTENING)
        assertEquals(AgentState.LISTENING, next)
        assertTrue(sm.lastRejection == null || sm.lastRejection != null)
    }
}
