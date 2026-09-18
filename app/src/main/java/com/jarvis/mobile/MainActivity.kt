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
        val sm = (application as JarvisApp).stateMachine
        setContent {
            val state by sm.state.collectAsState()
            val rejection by sm.lastRejected.collectAsState()
            JarvisScreen(state = state, lastRejected = rejection)
        }
    }
}
