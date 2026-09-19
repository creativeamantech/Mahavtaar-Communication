package com.example.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConversationMessage
import com.example.data.model.SenderType
import com.example.ui.theme.S2SCyanLight
import com.example.ui.theme.S2SCyanPrimary
import com.example.ui.theme.S2SElectricMint
import com.example.ui.theme.S2SIndigoSecondary
import com.example.ui.theme.S2SVioletAccent

@Composable
fun TranscriptList(
    messages: List<ConversationMessage>,
    currentUserTranscript: String,
    currentAssistantDelta: String,
    onQuickPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll on new messages or streaming tokens
    LaunchedEffect(messages.size, currentUserTranscript, currentAssistantDelta) {
        if (messages.isNotEmpty() || currentUserTranscript.isNotEmpty() || currentAssistantDelta.isNotEmpty()) {
            val targetIndex = (messages.size + (if (currentUserTranscript.isNotEmpty() || currentAssistantDelta.isNotEmpty()) 1 else 0)).coerceAtLeast(0)
            if (targetIndex > 0) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Quick prompts row for one-tap voice queries
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickPromptChip("What models are running?", onQuickPromptClick)
            QuickPromptChip("Test pipeline latency", onQuickPromptClick)
            QuickPromptChip("What is the weather?", onQuickPromptClick)
            QuickPromptChip("Explain sub-800ms speed", onQuickPromptClick)
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("transcript_list"),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && currentUserTranscript.isEmpty() && currentAssistantDelta.isEmpty()) {
                item {
                    EmptyTranscriptPlaceholder()
                }
            }

            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            // Live user speech partial
            if (currentUserTranscript.isNotBlank()) {
                item {
                    LiveUserBubble(text = currentUserTranscript)
                }
            }

            // Live assistant streaming partial
            if (currentAssistantDelta.isNotBlank()) {
                item {
                    LiveAssistantBubble(text = currentAssistantDelta)
                }
            }
        }
    }
}

@Composable
private fun QuickPromptChip(
    text: String,
    onClick: (String) -> Unit
) {
    AssistChip(
        onClick = { onClick(text) },
        label = { Text(text, fontSize = 12.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = S2SCyanLight
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    )
}

@Composable
private fun EmptyTranscriptPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(S2SCyanLight.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    tint = S2SCyanLight,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Voice Ready",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap microphone or choose a quick prompt to start",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage) {
    val isUser = message.sender == SenderType.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(S2SVioletAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = S2SVioletAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) Brush.horizontalGradient(listOf(S2SCyanPrimary, S2SIndigoSecondary))
                    else Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                )
                if (message.metrics != null && !isUser) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚡ ${message.metrics.totalLatencyMs}ms roundtrip",
                        fontSize = 10.sp,
                        color = S2SElectricMint,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(S2SCyanPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = S2SCyanPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveUserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(S2SCyanPrimary.copy(alpha = 0.25f))
                .border(1.dp, S2SCyanLight.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = S2SCyanLight
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "●",
                    fontSize = 12.sp,
                    color = S2SCyanLight
                )
            }
        }
    }
}

@Composable
private fun LiveAssistantBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(S2SElectricMint.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = S2SElectricMint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .border(1.dp, S2SElectricMint.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
