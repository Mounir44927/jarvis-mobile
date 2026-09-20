package com.jarvis.mobile

import android.app.Application
import com.jarvis.mobile.core.AgentEvent
import com.jarvis.mobile.core.JarvisStateMachine
import com.jarvis.mobile.tts.JarvisVoiceSession

/**
 * حاوية بسيطة للتبعيات في Phase 1.
 * ستُستبدل تدريجياً بحقن تبعيات كامل عند نمو المكونات (Phase 5+).
 */
class JarvisApp : Application() {

    lateinit var stateMachine: JarvisStateMachine
        private set

    /**
     * جلسة الصوت — تعيش مع العملية كاملة (أطول من أي Activity/recomposition)،
     * وهي **النقطة الوحيدة** التي تمر عبرها كل أصوات Jarvis.
     */
    lateinit var voiceSession: JarvisVoiceSession
        private set

    override fun onCreate() {
        super.onCreate()
        stateMachine = JarvisStateMachine()
        voiceSession = JarvisVoiceSession.createDefault(
            appContext = applicationContext,
            machine = object : JarvisVoiceSession.JarvisMachineAdapter {
                override fun beginSpeaking(): Boolean =
                    stateMachine.dispatch(AgentEvent.SPEAK) != null

                override fun doneSpeaking() {
                    stateMachine.dispatch(AgentEvent.RESET)
                }
            },
        )
    }
}
