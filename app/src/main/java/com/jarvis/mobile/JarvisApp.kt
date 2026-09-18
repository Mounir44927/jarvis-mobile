package com.jarvis.mobile

import android.app.Application
import com.jarvis.mobile.core.JarvisStateMachine

/**
 * حاوية بسيطة للتبعيات في Phase 1.
 * ستُستبدل تدريجياً بحقن تبعيات كامل عند نمو المكونات (Phase 5+).
 */
class JarvisApp : Application() {

    lateinit var stateMachine: JarvisStateMachine
        private set

    override fun onCreate() {
        super.onCreate()
        stateMachine = JarvisStateMachine()
    }
}
