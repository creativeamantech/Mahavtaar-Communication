package com.example.ui.component

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DiagnosticsInfo
import com.example.ui.theme.S2SCyanLight
import com.example.ui.theme.S2SElectricMint
import com.example.ui.theme.S2SIndigoSecondary
import com.example.ui.theme.S2SVioletAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsBottomSheet(
    diagnostics: DiagnosticsInfo,
    onDismiss: () -> Unit,
    onRunInferenceTest: (String) -> Unit = {},
    testStatusMessage: String? = null,
    isRunningTest: Boolean = false,
    onDismissTestStatus: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("diagnostics_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = S2SElectricMint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test status feedback banner
            if (testStatusMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isRunningTest) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = S2SElectricMint
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = null,
                                    tint = S2SElectricMint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = testStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = onDismissTestStatus) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Section 1: Speech-to-Text (STT)
            DiagnosticsSection(
                title = "Speech-to-Text (STT)",
                icon = Icons.Default.GraphicEq,
                iconColor = S2SCyanLight,
                rows = listOf(
                    "Active Model" to diagnostics.sttModelName,
                    "Runtime Engine" to diagnostics.sttStatus,
                    "Model Load Duration" to if (diagnostics.sttLoadTimeMs > 0) "${diagnostics.sttLoadTimeMs}ms" else "N/A",
                    "Acoustic Sampling" to "16,000 Hz PCM Mono (320 samples/frame)"
                ),
                onTest = if (diagnostics.sttStatus.contains("READY", ignoreCase = true) || diagnostics.sttStatus.contains("LOADED", ignoreCase = true) || diagnostics.sttStatus.contains("Active", ignoreCase = true)) {
                    { onRunInferenceTest("stt_whisper_tiny_q8") }
                } else null,
                testLabel = "Test STT Inference"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Language Model (LLM)
            DiagnosticsSection(
                title = "On-Device Language Model (LLM)",
                icon = Icons.Default.Memory,
                iconColor = S2SVioletAccent,
                rows = listOf(
                    "Active Model" to diagnostics.llmModelName,
                    "Inference Runtime" to diagnostics.llmStatus,
                    "Measured TTFT" to if (diagnostics.llmTtftMs > 0) "${diagnostics.llmTtftMs}ms" else "Awaiting turn",
                    "Generation Speed" to if (diagnostics.llmTokensPerSec > 0) String.format("%.1f tokens/sec", diagnostics.llmTokensPerSec) else "Awaiting turn",
                    "Context Window" to "2,048 tokens (Conversation Cache active)"
                ),
                onTest = if (diagnostics.llmStatus.contains("READY", ignoreCase = true) || diagnostics.llmStatus.contains("LOADED", ignoreCase = true) || diagnostics.llmStatus.contains("Active", ignoreCase = true)) {
                    { onRunInferenceTest("llm_smollm_135m_q4") }
                } else null,
                testLabel = "Test LLM Inference"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Section 3: Speech Synthesis (TTS)
            DiagnosticsSection(
                title = "Speech Synthesis (TTS)",
                icon = Icons.Default.RecordVoiceOver,
                iconColor = S2SElectricMint,
                rows = listOf(
                    "Voice Model" to diagnostics.ttsModelName,
                    "Acoustic State" to diagnostics.ttsStatus,
                    "Sample Rate" to "${diagnostics.ttsSampleRate} Hz",
                    "Audio Output" to "Low-Latency Android AudioTrack STREAM",
                    "Barge-In Flush" to "Instantaneous (0ms buffer drop)"
                ),
                onTest = if (diagnostics.ttsStatus.contains("READY", ignoreCase = true) || diagnostics.ttsStatus.contains("LOADED", ignoreCase = true) || diagnostics.ttsStatus.contains("Active", ignoreCase = true)) {
                    { onRunInferenceTest("tts_kokoro_82m") }
                } else null,
                testLabel = "Test TTS Synthesis"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Section 4: Hardware & Device Environment
            DiagnosticsSection(
                title = "Hardware & Environment",
                icon = Icons.Default.Storage,
                iconColor = S2SIndigoSecondary,
                rows = listOf(
                    "CPU Architecture" to diagnostics.cpuArch,
                    "Total System RAM" to "${diagnostics.totalDeviceRamMb} MB",
                    "Available Free RAM" to "${diagnostics.availDeviceRamMb} MB",
                    "Available Storage" to "${diagnostics.availableStorageMb} MB",
                    "Audio Transport" to diagnostics.audioTransport
                )
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DiagnosticsSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    rows: List<Pair<String, String>>,
    onTest: (() -> Unit)? = null,
    testLabel: String = "Test Inference"
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = iconColor
                    )
                }

                if (onTest != null) {
                    OutlinedButton(
                        onClick = onTest,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(testLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
