package com.example.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LatencyMetrics
import com.example.ui.theme.S2SCyanLight
import com.example.ui.theme.S2SElectricMint
import com.example.ui.theme.S2SIndigoSecondary
import com.example.ui.theme.S2SVioletAccent

@Composable
fun MetricsHudCard(
    metrics: LatencyMetrics,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .border(
                width = 1.dp,
                color = if (metrics.targetMet) S2SElectricMint.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(14.dp)
            .testTag("metrics_hud_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: S2S Latency Target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Latency target",
                        tint = S2SCyanLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LATENCY PIPELINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sub-800ms Target Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (metrics.targetMet) S2SElectricMint.copy(alpha = 0.15f)
                            else Color(0xFFF59E0B).copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (metrics.targetMet) S2SElectricMint else Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (metrics.targetMet) "< 800ms TARGET MET" else "800ms TARGET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (metrics.targetMet) S2SElectricMint else Color(0xFFF59E0B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Latency Breakdown Columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "STT Latency",
                    value = "${metrics.sttLatencyMs}ms",
                    color = S2SCyanLight
                )
                MetricItem(
                    label = "TTFT (LLM)",
                    value = "${metrics.ttftMs}ms",
                    color = S2SVioletAccent
                )
                MetricItem(
                    label = "TTS Synthesize",
                    value = "${metrics.ttsLatencyMs}ms",
                    color = S2SIndigoSecondary
                )
                MetricItem(
                    label = "Total Turn",
                    value = "${metrics.totalLatencyMs}ms",
                    color = if (metrics.targetMet) S2SElectricMint else Color(0xFFF59E0B),
                    isBold = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Segmented Pipeline Progress Visualizer
            val total = maxOf(metrics.totalLatencyMs.toFloat(), 1f)
            val sttFraction = (metrics.sttLatencyMs.toFloat() / total).coerceIn(0.1f, 0.8f)
            val ttftFraction = (metrics.ttftMs.toFloat() / total).coerceIn(0.1f, 0.8f)
            val ttsFraction = (metrics.ttsLatencyMs.toFloat() / total).coerceIn(0.1f, 0.8f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .weight(sttFraction)
                        .height(6.dp)
                        .background(S2SCyanLight)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .weight(ttftFraction)
                        .height(6.dp)
                        .background(S2SVioletAccent)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .weight(ttsFraction)
                        .height(6.dp)
                        .background(S2SElectricMint)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    color: Color,
    isBold: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
