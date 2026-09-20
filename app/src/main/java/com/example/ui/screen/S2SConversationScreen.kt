package com.example.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.S2SState
import com.example.ui.component.AudioOrbVisualizer
import com.example.ui.component.DiagnosticsBottomSheet
import com.example.ui.component.MetricsHudCard
import com.example.ui.component.ModelManagerBottomSheet
import com.example.ui.component.SettingsBottomSheet
import com.example.ui.component.TranscriptList
import com.example.ui.theme.S2SCyanLight
import com.example.ui.theme.S2SCyanPrimary
import com.example.ui.theme.S2SElectricMint
import com.example.ui.theme.S2SVioletAccent
import com.example.ui.viewmodel.S2SViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2SConversationScreen(
    viewModel: S2SViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Audio recording permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    // Check initial permission
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(hasPermission)
    }

    // Handle error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Speech to Speech",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !uiState.modelStatusSummary.allLoaded -> Color(0xFFE53935)
                                            uiState.engineState == S2SState.LISTENING -> S2SCyanLight
                                            uiState.engineState == S2SState.THINKING -> S2SVioletAccent
                                            uiState.engineState == S2SState.SPEAKING -> S2SElectricMint
                                            uiState.engineState == S2SState.ERROR -> MaterialTheme.colorScheme.error
                                            else -> S2SElectricMint
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    !uiState.modelStatusSummary.allLoaded -> "Models Not Loaded"
                                    uiState.engineState == S2SState.LISTENING -> "Listening..."
                                    uiState.engineState == S2SState.THINKING -> "Thinking (Local LLM)..."
                                    uiState.engineState == S2SState.SPEAKING -> "Speaking (Local TTS)..."
                                    uiState.engineState == S2SState.ERROR -> "Error"
                                    uiState.engineState == S2SState.IDLE -> "All Models Ready (On-Device)"
                                    else -> "Ready"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.openModelManager() },
                        modifier = Modifier.testTag("model_manager_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Model Manager",
                            tint = if (uiState.modelStatusSummary.allLoaded) S2SElectricMint else Color(0xFFE53935)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openDiagnostics() },
                        modifier = Modifier.testTag("diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "System Diagnostics"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearConversation() },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear conversation"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.openSettings() },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pipeline Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!uiState.isPermissionGranted) {
                // Permission Request Banner
                PermissionCard(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }

            // Banner when models are not loaded
            if (!uiState.modelStatusSummary.allLoaded) {
                ModelNotLoadedBanner(
                    onOpenModelManager = { viewModel.openModelManager() },
                    onQuickLoad = { viewModel.loadAllModels() }
                )
            }

            // Real-Time Latency HUD
            MetricsHudCard(
                metrics = uiState.currentMetrics,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            // Audio Orb Visualizer & Interactive State Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                AudioOrbVisualizer(
                    state = uiState.engineState,
                    amplitude = uiState.audioAmplitude,
                    isSpeaking = uiState.isSpeaking,
                    onClick = {
                        if (uiState.isSpeaking) {
                            viewModel.interrupt()
                        } else {
                            viewModel.toggleConversation()
                        }
                    }
                )
            }

            // Transcript Messages & Live Streaming Bubbles
            TranscriptList(
                messages = uiState.conversationMessages,
                currentUserTranscript = uiState.currentUserTranscript,
                currentAssistantDelta = uiState.currentAssistantDelta,
                onQuickPromptClick = { prompt ->
                    if (!uiState.isPermissionGranted) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    viewModel.sendQuickPrompt(prompt)
                },
                modifier = Modifier.weight(1f)
            )

            // Bottom Control Action Bar
            BottomActionBar(
                isRecording = uiState.isRecording,
                isSpeaking = uiState.isSpeaking,
                state = uiState.engineState,
                allModelsLoaded = uiState.modelStatusSummary.allLoaded,
                onToggleRecord = {
                    if (!uiState.isPermissionGranted) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else if (!uiState.modelStatusSummary.allLoaded) {
                        viewModel.openModelManager()
                    } else {
                        viewModel.toggleConversation()
                    }
                },
                onInterrupt = { viewModel.interrupt() }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (uiState.showSettingsSheet) {
        SettingsBottomSheet(
            config = uiState.config,
            onDismiss = { viewModel.closeSettings() },
            onSaveConfig = { newConfig -> viewModel.updateConfig(newConfig) }
        )
    }

    if (uiState.showModelManagerSheet) {
        ModelManagerBottomSheet(
            models = uiState.models,
            modelStatusSummary = uiState.modelStatusSummary,
            storageUsedBytes = uiState.storageUsedBytes,
            storageAvailableBytes = uiState.storageAvailableBytes,
            hardwareProfile = uiState.hardwareProfile,
            onDismiss = { viewModel.closeModelManager() },
            onLoadModel = { viewModel.loadModel(it) },
            onUnloadModel = { viewModel.unloadModel(it) },
            onLoadAll = { viewModel.loadAllModels() },
            onUnloadAll = { viewModel.unloadAllModels() },
            onStartDownload = { viewModel.startDownload(it) },
            onPauseDownload = { viewModel.pauseDownload(it) },
            onCancelDownload = { viewModel.cancelDownload(it) },
            onDeleteModel = { viewModel.deleteModel(it) },
            onDeleteAllModels = { viewModel.deleteAllModels() },
            onDownloadAllRecommended = { viewModel.downloadAllRecommendedModels() },
            onTestModel = { viewModel.runModelInferenceTest(it) },
            testStatusMessage = uiState.testStatusMessage,
            isRunningTest = uiState.isRunningTest,
            onDismissTestStatus = { viewModel.clearTestStatusMessage() }
        )
    }

    if (uiState.showDiagnosticsSheet) {
        DiagnosticsBottomSheet(
            diagnostics = uiState.diagnosticsInfo,
            onDismiss = { viewModel.closeDiagnostics() },
            onRunInferenceTest = { viewModel.runModelInferenceTest(it) },
            onVerifyLlmModel = { viewModel.verifyLlmModelFile() },
            testStatusMessage = uiState.testStatusMessage,
            isRunningTest = uiState.isRunningTest,
            onDismissTestStatus = { viewModel.clearTestStatusMessage() }
        )
    }
}

@Composable
private fun ModelNotLoadedBanner(
    onOpenModelManager: () -> Unit,
    onQuickLoad: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("models_not_loaded_banner"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE53935).copy(alpha = 0.12f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE53935).copy(alpha = 0.4f))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Models Not Loaded",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF5350)
                    )
                    Text(
                        text = "Initialize local STT, LLM & Voice models to chat.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onQuickLoad,
                    colors = ButtonDefaults.buttonColors(containerColor = S2SCyanPrimary),
                    modifier = Modifier.testTag("quick_load_models_button")
                ) {
                    Text("Load Models", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    isRecording: Boolean,
    isSpeaking: Boolean,
    state: S2SState,
    allModelsLoaded: Boolean,
    onToggleRecord: () -> Unit,
    onInterrupt: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Barge-in Interrupt Button (visible when assistant is speaking)
        AnimatedVisibility(
            visible = isSpeaking,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FilledIconButton(
                onClick = onInterrupt,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                    contentColor = Color(0xFFEF4444)
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("interrupt_button")
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = "Interrupt speech")
            }
        }

        if (isSpeaking) {
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Primary Microphone FAB (64dp target)
        FilledIconButton(
            onClick = onToggleRecord,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = when {
                    isRecording -> S2SCyanPrimary
                    !allModelsLoaded -> MaterialTheme.colorScheme.surfaceVariant
                    else -> S2SCyanPrimary.copy(alpha = 0.8f)
                },
                contentColor = if (isRecording || allModelsLoaded) Color.White else MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .size(64.dp)
                .testTag("microphone_fab")
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (isRecording) "Stop voice session" else "Start voice session",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("permission_card"),
        colors = CardDefaults.cardColors(
            containerColor = S2SCyanPrimary.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Microphone Access Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = S2SCyanLight
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Audio stays 100% on-device for real-time speech conversion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.testTag("grant_permission_button")
            ) {
                Text("Enable")
            }
        }
    }
}
