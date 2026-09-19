package com.example.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LlmBackendType
import com.example.data.model.S2SConfig
import com.example.data.model.SttBackendType
import com.example.data.model.TtsBackendType
import com.example.ui.theme.S2SCyanLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    config: S2SConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (S2SConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedStt by remember { mutableStateOf(config.sttBackend) }
    var selectedLlm by remember { mutableStateOf(config.llmBackend) }
    var selectedTts by remember { mutableStateOf(config.ttsBackend) }
    var vadSensitivity by remember { mutableFloatStateOf(config.vadSensitivity) }
    var silenceHangoverMs by remember { mutableFloatStateOf(config.silenceHangoverMs.toFloat()) }
    var continuousMode by remember { mutableStateOf(config.continuousConversation) }
    var bargeInEnabled by remember { mutableStateOf(config.allowBargeIn) }
    var speechRate by remember { mutableFloatStateOf(config.speechRate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = S2SCyanLight,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pipeline Architecture Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close settings")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: STT Model Selection
            Text(
                text = "Speech-to-Text (STT)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = S2SCyanLight
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SttBackendType.values().forEach { stt ->
                    FilterChip(
                        selected = selectedStt == stt,
                        onClick = { selectedStt = stt },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stt.displayName)
                                Text(stt.latencyCategory, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: LLM Model Selection
            Text(
                text = "Language Model (LLM)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = S2SCyanLight
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LlmBackendType.values().forEach { llm ->
                    FilterChip(
                        selected = selectedLlm == llm,
                        onClick = { selectedLlm = llm },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(llm.displayName)
                                Text(llm.latencyCategory, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: TTS Model Selection
            Text(
                text = "Text-to-Speech (TTS)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = S2SCyanLight
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TtsBackendType.values().forEach { tts ->
                    FilterChip(
                        selected = selectedTts == tts,
                        onClick = { selectedTts = tts },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tts.displayName)
                                Text("${tts.sampleRate}Hz", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: Pipeline Behavior & VAD
            Text(
                text = "Voice Activity & Latency Tuning",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = S2SCyanLight
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Continuous Conversation", style = MaterialTheme.typography.bodyMedium)
                    Text("Automatically listen after assistant stops", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = continuousMode, onCheckedChange = { continuousMode = it })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Allow Barge-in Interruption", style = MaterialTheme.typography.bodyMedium)
                    Text("User voice immediately halts assistant", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = bargeInEnabled, onCheckedChange = { bargeInEnabled = it })
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("VAD Sensitivity: ${(vadSensitivity * 100).toInt()}%", fontSize = 13.sp)
            Slider(
                value = vadSensitivity,
                onValueChange = { vadSensitivity = it },
                valueRange = 0.1f..0.9f
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Silence Hangover: ${silenceHangoverMs.toInt()}ms", fontSize = 13.sp)
            Slider(
                value = silenceHangoverMs,
                onValueChange = { silenceHangoverMs = it },
                valueRange = 400f..1500f
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("TTS Speech Speed: ${String.format("%.2f", speechRate)}x", fontSize = 13.sp)
            Slider(
                value = speechRate,
                onValueChange = { speechRate = it },
                valueRange = 0.8f..1.4f
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val updated = config.copy(
                        sttBackend = selectedStt,
                        llmBackend = selectedLlm,
                        ttsBackend = selectedTts,
                        vadSensitivity = vadSensitivity,
                        silenceHangoverMs = silenceHangoverMs.toLong(),
                        continuousConversation = continuousMode,
                        allowBargeIn = bargeInEnabled,
                        speechRate = speechRate
                    )
                    onSaveConfig(updated)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_settings_button")
            ) {
                Text("Apply Architecture Settings", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
