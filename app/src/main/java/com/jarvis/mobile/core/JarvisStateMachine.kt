package com.jarvis.mobile.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * آلة حالة مركزية بجدول انتقالات صريح.
 *
 * القاعدة: أي حدث غير معرّف في جدول الانتقالات للحالة الحالية يُرفض
 * (تبقى الحالة كما هي ويسجل سبب الرفض) — لا انتقالات منطقية غير مسموحة.
 */
class JarvisStateMachine(
    private val onRejected: (state: AgentState, event: AgentEvent) -> Unit = { _, _ -> },
) {

    private val _state = MutableStateFlow(AgentState.IDLE)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    /** آخر رفض مسجل كحالة تفاعلية — تراقبه الواجهة والتشخيص مباشرة. */
    private val _lastRejected = MutableStateFlow<Pair<AgentState, AgentEvent>?>(null)
    val lastRejected: StateFlow<Pair<AgentState, AgentEvent>?> = _lastRejected.asStateFlow()

    /** القيمة الفورية لآخر رفض — للتشخيص والاختبارات. */
    val lastRejection: Pair<AgentState, AgentEvent>? get() = _lastRejected.value

    /**
     * جدول الانتقالات المسموحة: الحالة الحالية -> مجموعة الأحداث المقبولة.
     */
    private val transitions: Map<AgentState, Set<AgentEvent>> = mapOf(
        AgentState.IDLE to setOf(
            AgentEvent.START_LISTENING, AgentEvent.UNDERSTAND, AgentEvent.SPEAK,
        ),
        AgentState.LISTENING to setOf(
            AgentEvent.STOP_LISTENING, AgentEvent.TRANSCRIBE, AgentEvent.RESET,
        ),
        AgentState.TRANSCRIBING to setOf(
            AgentEvent.UNDERSTAND, AgentEvent.FAIL, AgentEvent.RESET,
        ),
        AgentState.UNDERSTANDING to setOf(
            AgentEvent.PLAN, AgentEvent.EXECUTE, AgentEvent.VERIFY, AgentEvent.SPEAK,
            AgentEvent.ASK_CONFIRMATION, AgentEvent.FAIL, AgentEvent.RESET,
        ),
        AgentState.PLANNING to setOf(
            AgentEvent.EXECUTE, AgentEvent.ASK_CONFIRMATION, AgentEvent.FAIL, AgentEvent.RESET,
        ),
        AgentState.EXECUTING to setOf(
            AgentEvent.VERIFY, AgentEvent.FAIL, AgentEvent.BARGE_IN, AgentEvent.RESET,
        ),
        AgentState.VERIFYING to setOf(
            AgentEvent.SUCCESS, AgentEvent.FAIL, AgentEvent.EXECUTE, AgentEvent.RESET,
        ),
        AgentState.SPEAKING to setOf(
            AgentEvent.RESET, AgentEvent.BARGE_IN, AgentEvent.FAIL,
        ),
        AgentState.WAITING_CONFIRMATION to setOf(
            AgentEvent.CONFIRMED, AgentEvent.FAIL, AgentEvent.RESET,
        ),
        AgentState.ERROR to setOf(
            AgentEvent.RESET, AgentEvent.UNDERSTAND, AgentEvent.EXECUTE,
        ),
        AgentState.DONE to setOf(
            AgentEvent.RESET, AgentEvent.START_LISTENING, AgentEvent.UNDERSTAND,
        ),
    )

    val stateValue: AgentState get() = _state.value

    /**
     * يطبق الحدث على الحالة الحالية.
     * @return الحالة الجديدة إن قُبل الانتقال، أو null إن رُفض.
     */
    fun dispatch(event: AgentEvent): AgentState? {
        val current = _state.value
        val target = targetFor(current, event)
        if (target == null) {
            _lastRejected.value = current to event
            onRejected(current, event)
            return null
        }
        _state.value = target
        return target
    }

    /** الحالة الهدف حسب الجدول، أو null إن كان الانتقال مرفوضاً. */
    private fun targetFor(current: AgentState, event: AgentEvent): AgentState? {
        val allowed = transitions[current] ?: return null
        if (event !in allowed) return null
        return when (event) {
            AgentEvent.START_LISTENING -> AgentState.LISTENING
            AgentEvent.STOP_LISTENING -> AgentState.TRANSCRIBING
            AgentEvent.TRANSCRIBE -> AgentState.TRANSCRIBING
            AgentEvent.UNDERSTAND -> AgentState.UNDERSTANDING
            AgentEvent.PLAN -> AgentState.PLANNING
            AgentEvent.EXECUTE -> AgentState.EXECUTING
            AgentEvent.VERIFY -> AgentState.VERIFYING
            AgentEvent.SPEAK -> AgentState.SPEAKING
            AgentEvent.ASK_CONFIRMATION -> AgentState.WAITING_CONFIRMATION
            AgentEvent.CONFIRMED -> AgentState.EXECUTING
            AgentEvent.SUCCESS -> AgentState.DONE
            AgentEvent.FAIL -> AgentState.ERROR
            AgentEvent.BARGE_IN -> AgentState.LISTENING
            AgentEvent.RESET -> AgentState.IDLE
        }
    }
}
