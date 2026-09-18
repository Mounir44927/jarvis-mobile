package com.jarvis.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jarvis.mobile.core.AgentState
import kotlin.math.PI
import kotlin.math.sin

/**
 * الإعدادات البصرية لكل حالة (القسم 23): لا صور ثابتة — كل الحالات متحركة
 * وتستجيب لحالة النظام الفعلية القادمة من آلة الحالة.
 */
private data class OrbVisual(
    val primary: Color,
    val secondary: Color,
    val rotationMillis: Int,
    val pulseMillis: Int,
    val pulseStrength: Float,
)

private fun visualsFor(state: AgentState): OrbVisual = when (state) {
    AgentState.IDLE -> OrbVisual(Color(0xFF22D3EE), Color(0xFF0EA5E9), 9000, 4200, 0.03f)
    AgentState.LISTENING -> OrbVisual(Color(0xFF2DD4BF), Color(0xFF22D3EE), 3000, 900, 0.16f)
    AgentState.TRANSCRIBING -> OrbVisual(Color(0xFF38BDF8), Color(0xFF6366F1), 2600, 1500, 0.10f)
    AgentState.UNDERSTANDING,
    AgentState.PLANNING,
    -> OrbVisual(Color(0xFF818CF8), Color(0xFF38BDF8), 1800, 2400, 0.06f)
    AgentState.EXECUTING -> OrbVisual(Color(0xFFFB923C), Color(0xFF22D3EE), 1100, 800, 0.12f)
    AgentState.VERIFYING -> OrbVisual(Color(0xFFA78BFA), Color(0xFF38BDF8), 2200, 1300, 0.09f)
    AgentState.SPEAKING -> OrbVisual(Color(0xFF22D3EE), Color(0xFF67E8F9), 2400, 650, 0.20f)
    AgentState.WAITING_CONFIRMATION -> OrbVisual(Color(0xFFFACC15), Color(0xFFFB923C), 5000, 1600, 0.08f)
    AgentState.ERROR -> OrbVisual(Color(0xFFF87171), Color(0xFF7F1D1D), 5200, 500, 0.14f)
    AgentState.DONE -> OrbVisual(Color(0xFF4ADE80), Color(0xFF22D3EE), 7000, 3600, 0.05f)
}

@Composable
fun Orb(state: AgentState, modifier: Modifier = Modifier) {
    val visual = visualsFor(state)

    val transition = rememberInfiniteTransition(label = "orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = visual.rotationMillis, easing = LinearEasing),
        ),
        label = "orb-rotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = visual.pulseMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    )
    val pulseOffset = sin(pulse * PI).toFloat() * visual.pulseStrength

    Canvas(modifier = modifier.size(260.dp)) {
        val c = size.center
        val r = size.minDimension / 2f * (0.92f + pulseOffset)

        // هالة متوهجة
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(visual.primary.copy(alpha = 0.35f), Color.Transparent),
                center = c,
                radius = r * 0.95f,
            ),
            radius = r * 0.95f,
            center = c,
        )

        // النواة
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    visual.secondary.copy(alpha = 0.55f),
                    Color(0xFF050A14),
                ),
                center = c - Offset(r * 0.2f, r * 0.2f),
                radius = r * 0.8f,
            ),
            radius = r * 0.55f,
            center = c,
        )

        // الحلقة الخارجية
        drawCircle(
            color = visual.secondary.copy(alpha = 0.35f),
            radius = r,
            center = c,
            style = Stroke(width = 2.dp.toPx()),
        )

        // قوسان دوّاران بسرعة/اتجاه مختلفين — إيقاع الحركة يعكس الحالة
        drawArc(
            color = visual.primary,
            startAngle = rotation,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(c.x - r * 0.8f, c.y - r * 0.8f),
            size = Size(r * 1.6f, r * 1.6f),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = visual.secondary.copy(alpha = 0.8f),
            startAngle = -rotation * 0.6f + 140f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(c.x - r * 0.8f, c.y - r * 0.8f),
            size = Size(r * 1.6f, r * 1.6f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
