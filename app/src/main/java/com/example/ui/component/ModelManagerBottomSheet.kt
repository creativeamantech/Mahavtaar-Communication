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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.hardware.DeviceHardwareProfile
import com.example.data.model.ModelDownloadStatus
import com.example.data.model.ModelItem
import com.example.data.model.ModelStatusSummary
import com.example.data.model.ModelType
import com.example.ui.theme.S2SCyanLight
import com.example.ui.theme.S2SCyanPrimary
import com.example.ui.theme.S2SElectricMint
import com.example.ui.theme.S2SVioletAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerBottomSheet(
    models: Map<String, ModelItem>,
    modelStatusSummary: ModelStatusSummary,
    storageUsedBytes: Long,
    storageAvailableBytes: Long,
    hardwareProfile: DeviceHardwareProfile?,
    onDismiss: () -> Unit,
    onLoadModel: (String) -> Unit,
    onUnloadModel: (String) -> Unit,
    onLoadAll: () -> Unit,
    onUnloadAll: () -> Unit,
    onStartDownload: (String) -> Unit,
    onPauseDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onDeleteAllModels: () -> Unit,
    onInstallBundledPack: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTypes = listOf(ModelType.STT, ModelType.LLM, ModelType.TTS)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("model_manager_bottom_sheet")
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
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = S2SCyanLight,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Local Model Manager",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dedicated Model Control Panel
            ModelControlPanelCard(
                summary = modelStatusSummary,
                onLoadAll = onLoadAll,
                onUnloadAll = onUnloadAll,
                onInstallBundledPack = onInstallBundledPack
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Storage & Hardware summary
            StorageSummaryCard(
                usedBytes = storageUsedBytes,
                availBytes = storageAvailableBytes,
                hardwareProfile = hardwareProfile,
                onDeleteAll = onDeleteAllModels
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Tabs
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTypes.forEachIndexed { index, type ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(type.categoryLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Models in selected category
            val currentCategoryModels = models.values.filter { it.type == tabTypes[selectedTabIndex] }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                currentCategoryModels.forEach { model ->
                    ModelItemCard(
                        model = model,
                        onLoad = { onLoadModel(model.id) },
                        onUnload = { onUnloadModel(model.id) },
                        onDownload = { onStartDownload(model.id) },
                        onPause = { onPauseDownload(model.id) },
                        onCancel = { onCancelDownload(model.id) },
                        onDelete = { onDeleteModel(model.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModelControlPanelCard(
    summary: ModelStatusSummary,
    onLoadAll: () -> Unit,
    onUnloadAll: () -> Unit,
    onInstallBundledPack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "MODEL CONTROL PANEL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = S2SCyanLight
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ModelPillStatus(label = "STT", isLoaded = summary.sttLoaded, modelName = summary.sttModelName)
                ModelPillStatus(label = "LLM", isLoaded = summary.llmLoaded, modelName = summary.llmModelName)
                ModelPillStatus(label = "TTS", isLoaded = summary.ttsLoaded, modelName = summary.ttsModelName)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLoadAll,
                    modifier = Modifier.weight(1f).testTag("load_all_models_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = S2SCyanPrimary)
                ) {
                    Text("LOAD ALL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onUnloadAll,
                    modifier = Modifier.weight(1f).testTag("unload_all_models_button")
                ) {
                    Text("UNLOAD ALL", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onInstallBundledPack,
                modifier = Modifier.fillMaxWidth().testTag("install_bundled_pack_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Quick Setup Offline Model Pack", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ModelPillStatus(
    label: String,
    isLoaded: Boolean,
    modelName: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isLoaded) S2SElectricMint.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (isLoaded) S2SElectricMint.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isLoaded) S2SElectricMint else Color(0xFFE53935))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLoaded) S2SElectricMint else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (isLoaded) "● READY" else "NOT LOADED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (isLoaded) S2SElectricMint else Color(0xFFEF5350)
            )
        }
    }
}

@Composable
private fun StorageSummaryCard(
    usedBytes: Long,
    availBytes: Long,
    hardwareProfile: DeviceHardwareProfile?,
    onDeleteAll: () -> Unit
) {
    val usedMb = (usedBytes / (1024 * 1024)).toDouble()
    val availMb = (availBytes / (1024 * 1024)).toDouble()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = S2SVioletAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Storage: ${String.format("%.1f MB", usedMb)} used | ${String.format("%.1f GB", availMb / 1024)} free",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (hardwareProfile != null) {
                        Text(
                            text = "RAM: ${hardwareProfile.availableRamMb}MB avail / ${hardwareProfile.totalRamMb}MB total (${hardwareProfile.cpuArch})",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (usedBytes > 0) {
                IconButton(onClick = onDeleteAll) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete all models",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelItemCard(
    model: ModelItem,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val isInstalled = model.downloadStatus == ModelDownloadStatus.INSTALLED || model.downloadStatus == ModelDownloadStatus.READY
    val isLoaded = model.isLoaded

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_card_${model.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isLoaded) {
            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(S2SElectricMint.copy(alpha = 0.6f)))
        } else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (model.isRecommended) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(S2SCyanLight.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("RECOMMENDED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = S2SCyanLight)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${model.format} • ${model.quantization} • ${model.sizeFormatted} • RAM: ${model.minimumRamMb}MB",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                StatusBadge(status = model.downloadStatus, isLoaded = isLoaded)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Supported languages
            Text(
                text = "Languages: ${model.supportedLanguages.joinToString(", ")}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Progress bar if downloading
            if (model.downloadStatus == ModelDownloadStatus.DOWNLOADING || model.downloadStatus == ModelDownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { model.downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${(model.downloadProgress * 100).toInt()}% (${model.downloadSpeed})", fontSize = 11.sp)
                    Text(
                        "${model.downloadedBytes / (1024 * 1024)}MB / ${model.fileSizeBytes / (1024 * 1024)}MB",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Error message
            if (model.errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Error: ${model.errorMessage}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isInstalled) {
                    if (isLoaded) {
                        OutlinedButton(
                            onClick = onUnload,
                            modifier = Modifier.testTag("unload_button_${model.id}")
                        ) {
                            Text("Unload", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onLoad,
                            modifier = Modifier.testTag("load_button_${model.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = S2SElectricMint)
                        ) {
                            Text("Load Model", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete model", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (model.downloadStatus == ModelDownloadStatus.DOWNLOADING) {
                    OutlinedButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                    }
                } else if (model.downloadStatus == ModelDownloadStatus.PAUSED) {
                    Button(onClick = onDownload) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume", fontSize = 12.sp)
                    }
                } else {
                    Button(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ModelDownloadStatus, isLoaded: Boolean) {
    val (label, color) = when {
        isLoaded -> "READY" to S2SElectricMint
        status == ModelDownloadStatus.INSTALLED -> "INSTALLED" to S2SCyanLight
        status == ModelDownloadStatus.DOWNLOADING -> "DOWNLOADING" to S2SVioletAccent
        status == ModelDownloadStatus.PAUSED -> "PAUSED" to Color(0xFFF59E0B)
        status == ModelDownloadStatus.VERIFYING -> "VERIFYING" to S2SCyanLight
        status == ModelDownloadStatus.LOADING -> "LOADING" to S2SCyanLight
        status == ModelDownloadStatus.ERROR -> "ERROR" to Color(0xFFE53935)
        else -> "NOT INSTALLED" to Color(0xFF9E9E9E)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
