package com.amanrai.agave.ui

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.amanrai.agave.ui.theme.AgaveBlue
import com.amanrai.agave.ui.theme.AgaveBorder
import com.amanrai.agave.ui.theme.AgaveButtonBlue
import com.amanrai.agave.ui.theme.AgaveButtonText
import com.amanrai.agave.ui.theme.AgaveCyan
import com.amanrai.agave.ui.theme.AgaveFaint
import com.amanrai.agave.ui.theme.AgaveGreen
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgaveBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Header(
            state = state,
            onConsole = viewModel::showConsole,
            onHistory = viewModel::showHistory,
        )
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

@Composable
private fun Header(
    state: AgaveUiState,
    onConsole: () -> Unit,
    onHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgaveSurface)
            .border(1.dp, AgaveBorder)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Agave",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "  ·  Needle 2  ·  45M  ·  CQ2  ·  on-device",
                color = AgaveMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusLabel(state.engine)
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderTab(
                label = "Console",
                selected = state.screen == AgaveScreen.Console,
                onClick = onConsole,
            )
            Spacer(Modifier.width(18.dp))
            HeaderTab(
                label = "History (${state.history.size})",
                selected = state.screen != AgaveScreen.Console,
                onClick = onHistory,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusLabel(engine: EngineUiState) {
    val color = when (engine.phase) {
        "ready" -> AgaveGreen
        "error" -> AgaveRed
        "priming", "loading" -> AgaveAccent
        else -> AgaveBlue
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = engine.phase.ifBlank { "starting" },
            color = color,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun HeaderTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = if (selected) AgaveTextHigh else AgaveMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .height(2.dp)
                .fillMaxWidth()
                .background(if (selected) AgaveAccent else Color.Transparent),
        )
    }
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (current == null) {
                item { EmptyConsole() }
            } else {
                item { PromptLine(current.prompt) }
                if (current.status == "reading prompt") {
                    item { InlineStatus("Reading your request", AgaveAccent) }
                }
                if (current.reasoning.isNotBlank()) {
                    item {
                        Panel(title = "reasoning") {
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
                    item { ToolCallPanel(current.toolCall) }
                }
                if (current.error != null) {
                    item {
                        Panel(title = "error", titleColor = AgaveRed) {
                            Text(current.error, color = AgaveRed, fontSize = 13.sp)
                        }
                    }
                }
                if (current.status == "generating" && current.rawOutput.isBlank()) {
                    item { InlineStatus("Waiting for the first token", AgaveCyan) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        LiveFooter(current)
        Spacer(Modifier.height(8.dp))
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
private fun EmptyConsole() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Needle 2 is ready", color = AgaveTextHigh, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Try “flash the red light for two seconds”",
                color = AgaveFaint,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PromptLine(prompt: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgaveSunken, RoundedCornerShape(2.dp))
            .border(1.dp, AgaveBorder, RoundedCornerShape(2.dp))
            .padding(12.dp),
    ) {
        Text("›", color = AgaveGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(prompt, color = AgaveTextHigh, fontWeight = FontWeight.Medium)
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
private fun ToolCallPanel(toolCall: String) {
    Panel(title = "tool call", titleColor = AgaveCyan) {
        Text(
            text = toolCall,
            color = AgaveCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun LiveFooter(interaction: LiveInteraction?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(205.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Panel(
            title = "LED",
            modifier = Modifier
                .weight(0.8f)
                .fillMaxHeight(),
        ) {
            LedPreview(interaction?.toolCall.orEmpty())
        }
        Panel(
            title = "live metrics",
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight(),
        ) {
            LiveMetrics(interaction)
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

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(displayColor, RoundedCornerShape(2.dp))
                .border(1.dp, if (mode == "off") AgaveBorder else color.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            color = if (mode == "off") AgaveMuted else color,
            fontSize = 12.sp,
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
    MetricRow("TTFT", metricMs(metrics.ttftMs))
    MetricRow(
        "prefill",
        if (metrics.prefillTokens > 0) {
            "${formatRate(metrics.prefillTps)} · ${metrics.prefillTokens} tok"
        } else "—",
    )
    MetricRow(
        "decode",
        when {
            metrics.decodeTps > 0.0 -> formatRate(metrics.decodeTps)
            liveTps > 0.0 -> "${formatRate(liveTps)} live"
            else -> "—"
        },
    )
    MetricRow("tokens", interaction.tokenTimings.size.toString())
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
            label = { Text("Request") },
            placeholder = { Text("flash the red light for two seconds") },
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AgaveSunken,
                unfocusedContainerColor = AgaveSunken,
                disabledContainerColor = AgaveSunken,
                focusedBorderColor = AgaveAccent,
                unfocusedBorderColor = AgaveBorder,
                focusedTextColor = AgaveTextHigh,
                unfocusedTextColor = AgaveText,
                focusedLabelColor = AgaveAccent,
                unfocusedLabelColor = AgaveMuted,
                cursorColor = AgaveAccent,
            ),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onSubmit,
            enabled = enabled && value.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AgaveButtonBlue,
                contentColor = AgaveButtonText,
                disabledContainerColor = AgaveFaint,
                disabledContentColor = AgaveSunken,
            ),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.height(56.dp),
        ) {
            Text("Run", fontWeight = FontWeight.Medium)
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
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(state.history, key = { it.id }) { item ->
            HistoryRow(item = item, onClick = { onOpen(item) })
        }
    }
}

@Composable
private fun HistoryRow(item: StoredInteraction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgaveSurface, RoundedCornerShape(2.dp))
            .border(1.dp, AgaveBorder, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.prompt,
                color = AgaveTextHigh,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                formatDate(item.createdAt),
                color = AgaveFaint,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(metricMs(item.metrics.ttftMs), color = AgaveAccent, fontSize = 11.sp)
            Text(formatRate(item.metrics.decodeTps), color = AgaveMuted, fontSize = 10.sp)
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
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "‹ Back to history",
                color = AgaveAccent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 6.dp),
            )
        }
        item { PromptLine(item.prompt) }
        if (item.reasoning.isNotBlank()) {
            item {
                Panel("reasoning") {
                    Text(
                        item.reasoning,
                        color = AgaveMuted,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        if (item.toolCall.isNotBlank()) item { ToolCallPanel(item.toolCall) }
        if (item.error != null) {
            item {
                Panel("error", titleColor = AgaveRed) {
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
                Panel("metrics", Modifier.weight(1.4f).fillMaxHeight()) {
                    MetricsSummary(item.metrics, item.tokenTimings)
                }
            }
        }
        item {
            Panel("token timeline") {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgaveSunken, RoundedCornerShape(2.dp))
            .border(1.dp, AgaveBorder, RoundedCornerShape(2.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AgaveSurface, RoundedCornerShape(2.dp))
            .border(BorderStroke(1.dp, AgaveBorder), RoundedCornerShape(2.dp))
            .padding(10.dp),
    ) {
        Text(title, color = titleColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        content()
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

private fun visibleToken(value: String): String {
    if (value.isEmpty()) return "∅"
    return value
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace(" ", "·")
}
