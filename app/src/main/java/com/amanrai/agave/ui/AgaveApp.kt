package com.amanrai.agave.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amanrai.agave.AgaveScreen
import com.amanrai.agave.AgaveUiState
import com.amanrai.agave.AgaveViewModel
import com.amanrai.agave.EngineUiState
import com.amanrai.agave.model.InferenceMetrics
import com.amanrai.agave.model.LiveInteraction
import com.amanrai.agave.model.StoredInteraction
import com.amanrai.agave.model.TokenTiming
import com.amanrai.agave.ui.theme.AgaveAccent
import com.amanrai.agave.ui.theme.AgaveBackground
import com.amanrai.agave.ui.theme.AgaveBorder
import com.amanrai.agave.ui.theme.AgaveButtonBlue
import com.amanrai.agave.ui.theme.AgaveButtonText
import com.amanrai.agave.ui.theme.AgaveCyan
import com.amanrai.agave.ui.theme.AgaveFaint
import com.amanrai.agave.ui.theme.AgaveMuted
import com.amanrai.agave.ui.theme.AgaveRed
import com.amanrai.agave.ui.theme.AgaveSunken
import com.amanrai.agave.ui.theme.AgaveSurface
import com.amanrai.agave.ui.theme.AgaveText
import com.amanrai.agave.ui.theme.AgaveTextHigh
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgaveApp(viewModel: AgaveViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(state.windowBrightness) {
        state.windowBrightness?.let { brightness ->
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = brightness.coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AgaveBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Header(
                state = state,
                onConsole = viewModel::showConsole,
                onHistory = viewModel::showHistory,
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when (state.screen) {
                AgaveScreen.Console -> ConsoleScreen(state, viewModel::submit)
                AgaveScreen.History -> HistoryScreen(state, viewModel::openHistoryItem)
                AgaveScreen.HistoryDetail -> HistoryDetailScreen(
                    item = state.selectedInteraction,
                    onBack = viewModel::showHistory,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(
    state: AgaveUiState,
    onConsole: () -> Unit,
    onHistory: () -> Unit,
) {
    val selectedTab = if (state.screen == AgaveScreen.Console) 0 else 1

    Surface(color = AgaveSurface, tonalElevation = 4.dp) {
        Column {
            TopAppBar(
                title = {
                    Text("Agave", style = MaterialTheme.typography.titleLarge)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AgaveSurface,
                    titleContentColor = AgaveTextHigh,
                ),
            )
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AgaveSurface,
                contentColor = AgaveAccent,
                divider = { HorizontalDivider(color = AgaveBorder) },
            ) {
                AppTab(
                    label = "Console",
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    selected = selectedTab == 0,
                    onClick = onConsole,
                )
                AppTab(
                    label = "History (${state.history.size})",
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    selected = selectedTab == 1,
                    onClick = onHistory,
                )
            }
        }
    }
}

@Composable
private fun AppTab(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(50.dp),
        selectedContentColor = AgaveTextHigh,
        unselectedContentColor = AgaveMuted,
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { icon() }
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}

@Composable
private fun ConsoleScreen(state: AgaveUiState, onSubmit: (String) -> Unit) {
    if (!state.engine.ready) {
        EngineLoading(state.engine)
        return
    }

    var prompt by rememberSaveable { mutableStateOf("") }
    val current = state.current
    val listState = rememberLazyListState()

    LaunchedEffect(current?.rawOutput?.length) {
        if ((current?.rawOutput?.isNotEmpty() == true) && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current != null) {
                item { PromptLine(current.prompt) }
                if (current.status == "reading prompt") {
                    item { InlineStatus("Reading your request", AgaveAccent) }
                }
                if (current.reasoning.isNotBlank()) {
                    item {
                        Panel(title = "Reasoning") {
                            Text(
                                text = current.reasoning,
                                color = AgaveMuted,
                                fontStyle = FontStyle.Italic,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                            )
                        }
                    }
                }
                if (current.toolCall.isNotBlank()) {
                    item { ToolExchangePanel(current.toolCall, current.toolResult) }
                }
                if (current.error != null) {
                    item {
                        Panel(title = "Error", titleColor = AgaveRed) {
                            Text(current.error, color = AgaveRed, fontSize = 13.sp)
                        }
                    }
                }
                if (current.status == "generating" && current.rawOutput.isBlank()) {
                    item { InlineStatus("Waiting for the first token", AgaveCyan) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LiveFooter(current)
        Spacer(Modifier.height(12.dp))
        Composer(
            value = prompt,
            onValueChange = { prompt = it },
            enabled = !state.isGenerating,
            onSubmit = {
                if (prompt.isNotBlank()) {
                    onSubmit(prompt)
                    prompt = ""
                }
            },
        )
    }
}

@Composable
private fun EngineLoading(engine: EngineUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Panel(title = "Needle 2 startup", modifier = Modifier.fillMaxWidth()) {
            Text(engine.message, color = AgaveTextHigh, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            val progress = if (engine.progressTotal > 0) {
                (engine.progressCurrent.toFloat() / engine.progressTotal).coerceIn(0f, 1f)
            } else {
                0f
            }
            if (engine.phase == "error") {
                Text(engine.message, color = AgaveRed, fontSize = 13.sp)
            } else if (engine.progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = AgaveAccent,
                    trackColor = AgaveSunken,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100).toInt()}%  ·  ${formatProgress(engine)}",
                    color = AgaveMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                CircularProgressIndicator(color = AgaveAccent, strokeWidth = 2.dp)
            }
        }
    }
}

private fun formatProgress(engine: EngineUiState): String {
    return if (engine.phase == "loading") {
        "${formatBytes(engine.progressCurrent)} / ${formatBytes(engine.progressTotal)}"
    } else {
        "${engine.progressCurrent} / ${engine.progressTotal} tokens"
    }
}

@Composable
private fun PromptLine(prompt: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, AgaveButtonBlue.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = AgaveButtonBlue,
                contentColor = AgaveButtonText,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("You", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                prompt,
                color = AgaveTextHigh,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun InlineStatus(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(13.dp),
            color = color,
            strokeWidth = 1.5.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun ToolExchangePanel(toolCall: String, toolResult: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToolPayloadCard(
            title = "Tool call",
            payload = toolCall,
            accent = AgaveCyan,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        ToolPayloadCard(
            title = "Result",
            payload = toolResult,
            accent = AgaveAccent,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            emptyLabel = "Waiting for the tool…",
        )
    }
}

@Composable
private fun ToolPayloadCard(
    title: String,
    payload: String,
    accent: Color,
    modifier: Modifier = Modifier,
    emptyLabel: String = "",
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = AgaveSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(accent, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    title,
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                color = AgaveSunken,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = payload.ifBlank { emptyLabel },
                    color = if (payload.isBlank()) AgaveMuted else accent,
                    fontFamily = if (payload.isBlank()) FontFamily.Default else FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveFooter(interaction: LiveInteraction?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        color = AgaveSurface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxHeight(),
            ) {
                Text(
                    "LED",
                    color = AgaveMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                LedPreview(interaction?.toolCall.orEmpty())
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(AgaveBorder),
            )
            Column(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(AgaveCyan, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Live metrics",
                        color = AgaveTextHigh,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LiveMetrics(interaction)
            }
        }
    }
}

@Composable
private fun LedPreview(toolCall: String, honorDuration: Boolean = true) {
    val action = remember(toolCall) { parseLedAction(toolCall) }
    val requestedMode = action?.mode?.lowercase() ?: "off"
    val colorName = action?.color ?: "white"
    val color = ledColor(colorName)
    val durationMs = ((action?.durationSeconds ?: 2.0) * 1000.0).toLong()
        .coerceIn(1L, 30_000L)
    var remainingMs by remember(toolCall, honorDuration) {
        mutableStateOf(if (action != null && requestedMode != "off") durationMs else 0L)
    }

    LaunchedEffect(toolCall, honorDuration) {
        if (honorDuration && action != null && requestedMode != "off") {
            val deadline = SystemClock.elapsedRealtime() + durationMs
            do {
                remainingMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                if (remainingMs > 0L) delay(100L)
            } while (remainingMs > 0L)
        }
    }

    val durationExpired = honorDuration && action != null &&
        requestedMode != "off" && remainingMs <= 0L
    val mode = if (durationExpired) "off" else requestedMode
    val flashAlpha = if (mode == "flash") {
        val transition = rememberInfiniteTransition(label = "LED flash")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 220),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "LED brightness",
        )
        alpha
    } else {
        1f
    }
    val displayColor = when (mode) {
        "off" -> AgaveSunken
        "flash" -> color.copy(alpha = flashAlpha)
        else -> color
    }
    val secondsLabel = if (honorDuration) {
        String.format(Locale.US, "%.1fs", remainingMs / 1000.0)
    } else {
        String.format(Locale.US, "%.1fs", durationMs / 1000.0)
    }
    val label = when {
        durationExpired -> "off · duration complete"
        mode == "off" -> "off"
        mode == "flash" -> "$colorName · flash · $secondsLabel"
        else -> "$colorName · solid · $secondsLabel"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(AgaveSunken, CircleShape)
                .padding(7.dp)
                .background(displayColor, CircleShape)
                .border(
                    1.dp,
                    if (mode == "off") AgaveBorder else color.copy(alpha = 0.55f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (mode == "off") {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = AgaveFaint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            label,
            color = if (mode == "off") AgaveMuted else color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun LiveMetrics(interaction: LiveInteraction?) {
    if (interaction == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("metrics appear after a request", color = AgaveFaint, fontSize = 11.sp)
        }
        return
    }
    val metrics = interaction.metrics
    val liveTps = liveDecodeTps(interaction.tokenTimings)
    val prefill = if (metrics.prefillTokens > 0) {
        "${formatRate(metrics.prefillTps)} · ${metrics.prefillTokens} tok"
    } else {
        "—"
    }
    val decode = when {
        metrics.decodeTps > 0.0 -> formatRate(metrics.decodeTps)
        liveTps > 0.0 -> "${formatRate(liveTps)} live"
        else -> "—"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricTile("TTFT", metricMs(metrics.ttftMs), Modifier.weight(1f))
            MetricTile("Tokens", interaction.tokenTimings.size.toString(), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricTile("Prefill", prefill, Modifier.weight(1f))
            MetricTile("Decode", decode, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = AgaveSunken,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = AgaveMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                color = AgaveTextHigh,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                label = { Text("Ask Agave") },
                minLines = 1,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AgaveSunken,
                    unfocusedContainerColor = AgaveSunken,
                    disabledContainerColor = AgaveSunken,
                    focusedBorderColor = AgaveAccent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = AgaveTextHigh,
                    unfocusedTextColor = AgaveText,
                    focusedLabelColor = AgaveAccent,
                    unfocusedLabelColor = AgaveMuted,
                    cursorColor = AgaveAccent,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.Enter &&
                            !event.isShiftPressed &&
                            enabled &&
                            value.isNotBlank()
                        ) {
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    },
            )
            Button(
                onClick = onSubmit,
                enabled = enabled && value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AgaveButtonBlue,
                    contentColor = AgaveButtonText,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = AgaveFaint,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send request",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
}

@Composable
private fun HistoryScreen(
    state: AgaveUiState,
    onOpen: (StoredInteraction) -> Unit,
) {
    if (state.history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Completed interactions will appear here", color = AgaveFaint)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.history, key = { it.id }) { item ->
            HistoryRow(item = item, onClick = { onOpen(item) })
        }
    }
}

@Composable
private fun HistoryRow(item: StoredInteraction, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = AgaveSurface),
        border = BorderStroke(1.dp, AgaveBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = AgaveAccent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.prompt,
                    color = AgaveTextHigh,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(formatDate(item.createdAt), color = AgaveFaint, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(metricMs(item.metrics.ttftMs), color = AgaveAccent, fontSize = 11.sp)
                Text(formatRate(item.metrics.decodeTps), color = AgaveMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AgaveMuted)
        }
    }
}

@Composable
private fun HistoryDetailScreen(item: StoredInteraction?, onBack: () -> Unit) {
    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Interaction not found", color = AgaveRed)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Back to history")
            }
        }
        item { PromptLine(item.prompt) }
        if (item.reasoning.isNotBlank()) {
            item {
                Panel("Reasoning") {
                    Text(
                        item.reasoning,
                        color = AgaveMuted,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        if (item.toolCall.isNotBlank()) {
            item { ToolExchangePanel(item.toolCall, item.toolResult) }
        }
        if (item.error != null) {
            item {
                Panel("Error", titleColor = AgaveRed) {
                    Text(item.error, color = AgaveRed)
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(215.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Panel("LED", Modifier.weight(0.8f).fillMaxHeight()) {
                    LedPreview(item.toolCall, honorDuration = false)
                }
                Panel("Metrics", Modifier.weight(1.4f).fillMaxHeight()) {
                    MetricsSummary(item.metrics, item.tokenTimings)
                }
            }
        }
        item {
            Panel("Token timeline") {
                TokenTimelineHeader()
                HorizontalDivider(color = AgaveBorder)
                if (item.tokenTimings.isEmpty()) {
                    Text("No token timings recorded", color = AgaveFaint, fontSize = 11.sp)
                }
            }
        }
        items(item.tokenTimings, key = { it.index }) { timing ->
            TokenTimelineRow(timing)
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MetricsSummary(metrics: InferenceMetrics, timings: List<TokenTiming>) {
    MetricRow("TTFT", metricMs(metrics.ttftMs))
    MetricRow("prefill", "${metrics.prefillTokens} tok · ${formatRate(metrics.prefillTps)}")
    MetricRow("decode", "${metrics.decodeTokens} tok · ${formatRate(metrics.decodeTps)}")
    MetricRow("p50 gap", metricMs(metrics.interTokenPercentile(timings, 0.50)))
    MetricRow("p95 gap", metricMs(metrics.interTokenPercentile(timings, 0.95)))
    if (metrics.confidence >= 0.0) {
        MetricRow("confidence", String.format(Locale.US, "%.3f", metrics.confidence))
    }
}

@Composable
private fun TokenTimelineHeader() {
    Row(Modifier.fillMaxWidth()) {
        Text("# / token", color = AgaveFaint, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text("elapsed", color = AgaveFaint, fontSize = 10.sp, modifier = Modifier.width(72.dp))
        Text("gap", color = AgaveFaint, fontSize = 10.sp, modifier = Modifier.width(62.dp))
    }
}

@Composable
private fun TokenTimelineRow(timing: TokenTiming) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AgaveSunken,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, AgaveBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${timing.index + 1} · ${timing.tokenId}  ${visibleToken(timing.text)}",
                color = AgaveText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(metricMs(timing.elapsedMs), color = AgaveMuted, fontSize = 10.sp, modifier = Modifier.width(72.dp))
            Text(metricMs(timing.deltaMs), color = AgaveAccent, fontSize = 10.sp, modifier = Modifier.width(62.dp))
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AgaveMuted, fontSize = 10.sp)
        Text(
            value,
            color = AgaveTextHigh,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = AgaveMuted,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = AgaveSurface),
        border = BorderStroke(1.dp, AgaveBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Text(
                title,
                color = titleColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private data class LedAction(
    val color: String,
    val mode: String,
    val durationSeconds: Double,
)

private fun parseLedAction(toolCall: String): LedAction? = runCatching {
    val call = JSONArray(toolCall).optJSONObject(0) ?: return@runCatching null
    if (call.optString("name") != "set_led") return@runCatching null
    val arguments = call.optJSONObject("arguments") ?: return@runCatching null
    val mode = arguments.optString("mode")
    val color = arguments.optString("color").ifBlank { "white" }
    val duration = arguments.optDouble("duration_seconds", 2.0)
        .takeIf { it.isFinite() && it > 0.0 } ?: 2.0
    if (mode.isBlank()) null else LedAction(color, mode, duration)
}.getOrNull()

private fun ledColor(name: String): Color = when (name.lowercase()) {
    "red" -> Color(0xFFFF3B30)
    "green" -> Color(0xFF34C759)
    "blue" -> Color(0xFF0A84FF)
    "yellow" -> Color(0xFFFFD60A)
    "purple" -> Color(0xFFAF52DE)
    "white" -> Color(0xFFF2F2F7)
    else -> AgaveFaint
}

private fun liveDecodeTps(timings: List<TokenTiming>): Double {
    if (timings.size < 2) return 0.0
    val duration = timings.last().elapsedMs - timings.first().elapsedMs
    return if (duration > 0.0) (timings.size - 1) / (duration / 1000.0) else 0.0
}

private fun metricMs(value: Double): String = if (value >= 0.0) {
    String.format(Locale.US, "%.1f ms", value)
} else {
    "—"
}

private fun formatRate(value: Double): String = if (value > 0.0) {
    String.format(Locale.US, "%.2f tok/s", value)
} else {
    "—"
}

private fun formatDate(timestamp: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(Date(timestamp))

private fun formatBytes(value: Long): String = when {
    value >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
    value >= 1024 -> String.format(Locale.US, "%.1f KB", value / 1024.0)
    else -> "$value B"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun visibleToken(value: String): String {
    if (value.isEmpty()) return "∅"
    return value
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace(" ", "·")
}
