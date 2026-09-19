package com.example.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.S2SState
import com.example.ui.theme.S2SCyanLight
import com.example.ui.theme.S2SCyanPrimary
import com.example.ui.theme.S2SElectricMint
import com.example.ui.theme.S2SIndigoSecondary
import com.example.ui.theme.S2SVioletAccent
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive pulsating audio orb visualizer.
 * Reacts to microphone amplitude and assistant speaking states.
 */
@Composable
fun AudioOrbVisualizer(
    state: S2SState,
    amplitude: Float,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbPulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbScale"
    )

    val waveRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveRotation"
    )

    // State-dependent color palette
    val coreColor by animateColorAsState(
        targetValue = when (state) {
            S2SState.LISTENING -> S2SCyanLight
            S2SState.THINKING -> S2SVioletAccent
            S2SState.SPEAKING -> S2SElectricMint
            S2SState.ERROR -> MaterialTheme.colorScheme.error
            S2SState.IDLE -> S2SCyanPrimary.copy(alpha = 0.6f)
        },
        label = "CoreColor"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (state) {
            S2SState.LISTENING -> S2SCyanPrimary
            S2SState.THINKING -> S2SIndigoSecondary
            S2SState.SPEAKING -> Color(0xFF059669)
            S2SState.ERROR -> Color(0xFFDC2626)
            S2SState.IDLE -> S2SIndigoSecondary.copy(alpha = 0.4f)
        },
        label = "SecondaryColor"
    )

    Box(
        modifier = modifier
            .size(220.dp)
            .testTag("audio_orb_visualizer")
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 4.2f

            // Dynamic amplification: voice amplitude expands rings
            val dynamicBoost = (amplitude * 55f).coerceIn(0f, 40f)
            val effectiveRadius = (baseRadius * pulseScale) + dynamicBoost

            // Outer Glow Aura 1
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor.copy(alpha = 0.25f), Color.Transparent),
                    center = center,
                    radius = effectiveRadius * 1.85f
                ),
                radius = effectiveRadius * 1.85f,
                center = center
            )

            // Outer Pulsing Ripple Ring
            drawCircle(
                color = secondaryColor.copy(alpha = 0.35f),
                radius = effectiveRadius * 1.35f,
                center = center,
                style = Stroke(width = 2.5f)
            )

            // Mid Ripple Ring with Sonic Dots
            drawCircle(
                color = coreColor.copy(alpha = 0.6f),
                radius = effectiveRadius * 1.15f,
                center = center,
                style = Stroke(width = 3.5f)
            )

            // Orbiting Frequency Nodes
            val nodeCount = 8
            for (i in 0 until nodeCount) {
                val angleRad = Math.toRadians((waveRotation + (i * 360f / nodeCount)).toDouble())
                val orbitRadius = effectiveRadius * 1.15f
                val nodeX = center.x + (orbitRadius * cos(angleRad)).toFloat()
                val nodeY = center.y + (orbitRadius * sin(angleRad)).toFloat()

                drawCircle(
                    color = coreColor,
                    radius = 3.5f + (amplitude * 5f),
                    center = Offset(nodeX, nodeY)
                )
            }

            // Core Glowing Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        coreColor,
                        secondaryColor
                    ),
                    center = center,
                    radius = effectiveRadius
                ),
                radius = effectiveRadius,
                center = center
            )
        }
    }
}
