package com.jarvis.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.mobile.core.AgentEvent
import com.jarvis.mobile.core.AgentState

private val Background = Color(0xFF050A14)
private val Panel = Color(0xFF0B1220)
private val TextPrimary = Color(0xFFE2E8F0)
private val TextMuted = Color(0xFF64748B)
private val Accent = Color(0xFF67E8F9)

@Composable
fun JarvisScreen(
    state: AgentState,
    lastRejected: Pair<AgentState, AgentEvent>?,
) {
    var panelOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        IconButton(
            onClick = { panelOpen = !panelOpen },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = "حالة المهمة",
                tint = Accent,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Orb(state = state)
            Spacer(Modifier.height(28.dp))
            Text(
                text = state.arabicLabel,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            if (state == AgentState.IDLE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "اضغط أيقونة الحالة أعلى الشاشة لعرض حالة المهمة.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            }
        }

        if (panelOpen) {
            TaskPanel(
                state = state,
                lastRejected = lastRejected,
                onClose = { panelOpen = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * لوحة حالة المهمة (القسم 57): تعرض الحالة الفعلية لآلة الحالة فقط.
 * لا تعرض أي تفكير داخلي، ولا تختلق بيانات مهام غير موجودة.
 */
@Composable
private fun TaskPanel(
    state: AgentState,
    lastRejected: Pair<AgentState, AgentEvent>?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "حالة المهمة",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "إغلاق",
                color = Accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        TaskRow("الحالة الحالية", state.arabicLabel)
        TaskRow(
            "آخر انتقال مرفوض",
            lastRejected?.let { "${it.first.name} ← ${it.second.name}" } ?: "لا شيء",
        )
        TaskRow("المهمة الجارية", "— (تنفيذ المهام الفعلي يبدأ في Phase 9)")
        TaskRow("الأدوات المستخدمة", "—")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "هذه اللوحة تعرض بيانات آلة الحالة الحقيقية فقط، ولا تعرض أي محاكاة أو بيانات وهمية.",
            color = TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TaskRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = TextMuted, fontSize = 14.sp)
        Text(text = value, color = TextPrimary, fontSize = 14.sp)
    }
}
