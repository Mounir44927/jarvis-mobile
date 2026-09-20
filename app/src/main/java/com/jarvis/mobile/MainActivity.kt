package com.jarvis.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jarvis.mobile.ui.JarvisScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as JarvisApp
        val sm = app.stateMachine

        // أول تحية عربية — مرة واحدة على مستوى العملية (حاجز JarvisVoiceSession
        // يمنع التكرار من recomposition/lifecycle/إعادة تشغيل Activity).
        app.voiceSession.speakGreetingOnce()

        setContent {
            val state by sm.state.collectAsState()
            val rejection by sm.lastRejected.collectAsState()
            val tier by app.voiceSession.latestTier.collectAsState()
            JarvisScreen(state = state, lastRejected = rejection, latestTier = tier)
        }
    }
}
