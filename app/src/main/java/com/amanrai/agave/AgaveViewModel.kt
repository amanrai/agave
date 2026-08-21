package com.amanrai.agave

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amanrai.agave.data.InteractionStore
import com.amanrai.agave.model.InferenceMetrics
import com.amanrai.agave.model.LiveInteraction
import com.amanrai.agave.model.StoredInteraction
import com.amanrai.agave.model.TokenTiming
import com.amanrai.agave.model.UNKNOWN_METRIC
import com.amanrai.agave.model.decodeToken
import com.amanrai.agave.model.parseStream
import com.amanrai.agave.nativebridge.NativeBridge
import com.amanrai.agave.nativebridge.NativeCallbacks
import com.amanrai.agave.skills.Bm25SkillRetriever
import com.amanrai.agave.skills.SkillCatalog
import com.amanrai.agave.skills.SkillDefinition
import com.amanrai.agave.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AgaveViewModel(application: Application) : AndroidViewModel(application) {
    private val store = InteractionStore(application)
    private lateinit var skillCatalog: SkillCatalog
    private lateinit var skillRetriever: Bm25SkillRetriever
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var routerToolsJson: String
    private val _state = MutableStateFlow(AgaveUiState())
    val state: StateFlow<AgaveUiState> = _state.asStateFlow()

    init {
        loadHistory()
        initializeEngine()
    }

    fun showConsole() {
        _state.update { it.copy(screen = AgaveScreen.Console, selectedInteraction = null) }
    }

    fun showHistory() {
        _state.update { it.copy(screen = AgaveScreen.History, selectedInteraction = null) }
    }

    fun openHistoryItem(item: StoredInteraction) {
        _state.update { it.copy(screen = AgaveScreen.HistoryDetail, selectedInteraction = item) }
    }

    fun submit(promptValue: String) {
        val prompt = promptValue.trim()
        val snapshot = _state.value
        if (prompt.isBlank() || !snapshot.engine.ready || snapshot.isGenerating) return

        val interaction = LiveInteraction(prompt = prompt, status = "finding skills")
        _state.update {
            it.copy(
                current = interaction,
                isGenerating = true,
                screen = AgaveScreen.Console,
                selectedInteraction = null,
                transientError = null,
            )
        }

        viewModelScope.launch {
            var completed: LiveInteraction? = null
            var restoreError: String? = null
            val pipelineError = withContext(Dispatchers.Default) {
                runCatching {
                    val routingOutput = runRoutingPass(prompt)
                    val keywords = extractRoutingKeywords(routingOutput)
                    val candidates = selectCandidates(keywords)
                    configureCandidateSkills(candidates)
                    completed = runToolSelectionPass(prompt)
                    check(completed != null) { "Tool selection did not complete." }
                }.exceptionOrNull()?.message
            }
            restoreError = withContext(Dispatchers.Default) {
                runCatching {
                    NativeBridge.configureTools(
                        routerToolsJson.toByteArray(Charsets.UTF_8),
                        NativeCallbacks(),
                    )
                }.getOrElse { error -> error.message ?: "Could not restore find_tool." }
            }

            val failure = pipelineError ?: restoreError
            if (failure != null) {
                _state.update { ui ->
                    val failed = ui.current?.copy(status = "error", error = failure)
                    if (failed != null) completed = failed
                    ui.copy(
                        current = failed,
                        transientError = failure,
                        engine = if (restoreError != null) {
                            ui.engine.copy(phase = "error", message = restoreError!!, ready = false)
                        } else {
                            ui.engine
                        },
                    )
                }
            }

            val result = completed
            if (result != null) {
                val stored = withContext(Dispatchers.IO) { store.insert(result) }
                _state.update { ui ->
                    ui.copy(
                        history = listOf(stored) + ui.history,
                        isGenerating = false,
                    )
                }
            } else {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun runRoutingPass(prompt: String): String {
        var rawOutput = ""
        var callbackError: String? = null
        val nativeError = NativeBridge.generate(
            query = prompt.toByteArray(Charsets.UTF_8),
            showThinking = true,
            requireToolCall = true,
            maxNewTokens = 96,
            callbacks = NativeCallbacks(
                statusHandler = { phase, _, _, _ ->
                    if (phase == "reading" || phase == "generating") {
                        _state.update { ui ->
                            ui.copy(current = ui.current?.copy(status = "finding skills"))
                        }
                    }
                },
                tokenHandler = { bytes, _, _, _, _ ->
                    rawOutput += decodeToken(bytes)
                    val (reasoning, call) = parseStream(rawOutput)
                    _state.update { ui ->
                        ui.copy(
                            current = ui.current?.copy(
                                routingReasoning = reasoning,
                                routingToolCall = call,
                            ),
                        )
                    }
                },
                completeHandler = { _, _, _, _, _, bytes ->
                    rawOutput = decodeToken(bytes)
                    val (reasoning, call) = parseStream(rawOutput)
                    _state.update { ui ->
                        ui.copy(
                            current = ui.current?.copy(
                                routingReasoning = reasoning,
                                routingToolCall = call,
                                status = "searching skill index",
                            ),
                        )
                    }
                },
                errorHandler = { callbackError = it },
            ),
        )
        check(nativeError == null) { nativeError ?: "Skill routing failed." }
        check(callbackError == null) { callbackError ?: "Skill routing failed." }
        Log.i(ROUTER_LOG_TAG, "find_tool output: $rawOutput")
        return rawOutput
    }

    private fun extractRoutingKeywords(rawOutput: String): List<String> {
        val call = parseStream(rawOutput).second
        val keywords = buildList {
            val calls = JSONArray(call)
            repeat(calls.length()) { index ->
                val item = calls.optJSONObject(index) ?: return@repeat
                if (item.optString("name") != "find_tool") return@repeat
                val values = item.optJSONObject("arguments")?.optJSONArray("keywords")
                    ?: return@repeat
                repeat(values.length()) { keywordIndex ->
                    values.optString(keywordIndex).trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }.distinct()
        check(keywords.isNotEmpty()) { "find_tool returned no usable keywords." }
        Log.i(ROUTER_LOG_TAG, "keywords: ${keywords.joinToString()}")
        return keywords
    }

    private fun selectCandidates(keywords: List<String>): List<SkillDefinition> {
        val ranked = skillRetriever.search(keywords)
        check(ranked.isNotEmpty()) {
            "No skill matched keywords: ${keywords.joinToString()}"
        }

        val selected = mutableListOf<SkillDefinition>()
        val matches = JSONArray()
        ranked.take(MAX_RETRIEVED_SKILLS).forEach { match ->
            val proposed = selected + match.skill
            val json = skillCatalog.toolsJson(proposed)
            val tokenCount = NativeBridge.schemaPrefixTokenCount(json.toByteArray(Charsets.UTF_8))
            check(tokenCount >= 0) { "Could not measure the candidate schema prefix." }
            val accepted = tokenCount <= MAX_CANDIDATE_PREFIX_TOKENS
            if (accepted) selected += match.skill
            matches.put(
                JSONObject()
                    .put("skill", match.skill.id)
                    .put("bm25_score", match.score)
                    .put("prefix_tokens_if_added", tokenCount)
                    .put("selected", accepted),
            )
        }
        check(selected.isNotEmpty()) { "The highest-ranked skill exceeds the schema token budget." }
        val retrievalResult = JSONObject()
            .put("keywords", JSONArray(keywords))
            .put("prefix_token_budget", MAX_CANDIDATE_PREFIX_TOKENS)
            .put("matches", matches)
            .put("selected_skills", JSONArray(selected.map { it.id }))
            .toString(2)
        _state.update { ui ->
            ui.copy(current = ui.current?.copy(routingResult = retrievalResult))
        }
        Log.i(ROUTER_LOG_TAG, "candidates: ${selected.joinToString { it.id }}")
        return selected
    }

    private fun configureCandidateSkills(candidates: List<SkillDefinition>) {
        _state.update { ui ->
            ui.copy(
                current = ui.current?.copy(
                    status = "loading ${candidates.joinToString { it.id }}",
                ),
            )
        }
        val json = skillCatalog.toolsJson(candidates)
        var callbackError: String? = null
        val nativeError = NativeBridge.configureTools(
            json.toByteArray(Charsets.UTF_8),
            NativeCallbacks(errorHandler = { callbackError = it }),
        )
        check(nativeError == null) { nativeError ?: "Could not load candidate skills." }
        check(callbackError == null) { callbackError ?: "Could not load candidate skills." }
    }

    private fun runToolSelectionPass(prompt: String): LiveInteraction? {
        var completed: LiveInteraction? = null
        var callbackError: String? = null
        val nativeError = NativeBridge.generate(
            query = prompt.toByteArray(Charsets.UTF_8),
            showThinking = true,
            requireToolCall = false,
            maxNewTokens = 96,
            callbacks = NativeCallbacks(
                statusHandler = { phase, _, _, _ ->
                    if (phase == "reading" || phase == "generating") {
                        _state.update { ui ->
                            ui.copy(
                                current = ui.current?.copy(
                                    status = if (phase == "reading") "reading prompt" else "generating",
                                ),
                            )
                        }
                    }
                },
                tokenHandler = { bytes, tokenId, index, elapsedMs, deltaMs ->
                    val text = decodeToken(bytes)
                    _state.update { ui ->
                        val current = ui.current ?: return@update ui
                        val raw = current.rawOutput + text
                        val (reasoning, call) = parseStream(raw)
                        val timing = TokenTiming(index, tokenId, text, elapsedMs, deltaMs)
                        val metrics = if (current.metrics.ttftMs < 0.0) {
                            current.metrics.copy(ttftMs = elapsedMs)
                        } else {
                            current.metrics
                        }
                        ui.copy(
                            current = current.copy(
                                rawOutput = raw,
                                reasoning = reasoning,
                                toolCall = call,
                                metrics = metrics,
                                tokenTimings = current.tokenTimings + timing,
                            ),
                        )
                    }
                },
                prefillHandler = { tokens, milliseconds, tokensPerSecond ->
                    _state.update { ui ->
                        ui.copy(
                            current = ui.current?.copy(
                                status = "generating",
                                metrics = ui.current.metrics.copy(
                                    prefillTokens = tokens,
                                    prefillMs = milliseconds,
                                    prefillTps = tokensPerSecond,
                                ),
                            ),
                        )
                    }
                },
                completeHandler = {
                        tokens,
                        decodeMs,
                        decodeTokensPerSecond,
                        timeToFirstTokenMs,
                        confidence,
                        rawOutputBytes,
                    ->
                    val rawOutput = decodeToken(rawOutputBytes)
                    _state.update { ui ->
                        val current = ui.current ?: return@update ui
                        val (reasoning, call) = parseStream(rawOutput)
                        val execution = toolExecutor.execute(call)
                        val final = current.copy(
                            status = if (current.error == null) "done" else "error",
                            rawOutput = rawOutput,
                            reasoning = reasoning,
                            toolCall = call,
                            toolResult = execution.resultJson,
                            metrics = current.metrics.copy(
                                decodeTokens = tokens,
                                decodeMs = decodeMs,
                                decodeTps = decodeTokensPerSecond,
                                ttftMs = timeToFirstTokenMs,
                                confidence = confidence,
                            ),
                        )
                        completed = final
                        ui.copy(
                            current = final,
                            windowBrightness = execution.windowBrightness ?: ui.windowBrightness,
                        )
                    }
                },
                errorHandler = { callbackError = it },
            ),
        )
        check(nativeError == null) { nativeError ?: "Tool selection failed." }
        check(callbackError == null) { callbackError ?: "Tool selection failed." }
        return completed
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { store.loadAll() }
            _state.update { it.copy(history = history) }
        }
    }

    private fun initializeEngine() {
        viewModelScope.launch {
            val start = System.nanoTime()
            val result = runCatching {
                val modelBytes = withContext(Dispatchers.IO) { readModelAsset() }
                val application = getApplication<Application>()
                skillCatalog = withContext(Dispatchers.IO) { SkillCatalog.load(application) }
                skillRetriever = Bm25SkillRetriever(skillCatalog.retrievableSkills)
                toolExecutor = ToolExecutor(application, skillCatalog)
                routerToolsJson = skillCatalog.routerToolsJson()
                val toolsJson = routerToolsJson
                withContext(Dispatchers.Default) {
                    NativeBridge.initialize(
                        modelBytes = modelBytes,
                        toolsJson = toolsJson.toByteArray(Charsets.UTF_8),
                        callbacks = NativeCallbacks(
                            statusHandler = { phase, message, current, total ->
                                _state.update {
                                    it.copy(
                                        engine = it.engine.copy(
                                            phase = phase,
                                            message = message,
                                            progressCurrent = current.toLong(),
                                            progressTotal = total.toLong(),
                                            ready = phase == "ready",
                                        ),
                                    )
                                }
                            },
                            errorHandler = { message ->
                                _state.update {
                                    it.copy(
                                        engine = it.engine.copy(
                                            phase = "error",
                                            message = message,
                                            ready = false,
                                        ),
                                    )
                                }
                            },
                        ),
                    )
                }
            }

            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            result.fold(
                onSuccess = { error ->
                    if (error == null) {
                        _state.update {
                            it.copy(
                                engine = it.engine.copy(
                                    phase = "ready",
                                    message = "Ready",
                                    ready = true,
                                    startupMs = elapsed,
                                ),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                engine = it.engine.copy(
                                    phase = "error",
                                    message = error,
                                    ready = false,
                                    startupMs = elapsed,
                                ),
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            engine = it.engine.copy(
                                phase = "error",
                                message = throwable.message ?: "Agave could not initialize Needle 2.",
                                ready = false,
                                startupMs = elapsed,
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun readModelAsset(): ByteArray {
        val assets = getApplication<Application>().assets
        val assetSize = assets.openFd("needle2.cact").use { it.length.toInt() }
        assets.open("needle2.cact").use { input ->
            val bytes = ByteArray(assetSize)
            var readTotal = 0
            while (readTotal < bytes.size) {
                val count = input.read(bytes, readTotal, bytes.size - readTotal)
                if (count < 0) break
                readTotal += count
                _state.update {
                    it.copy(
                        engine = it.engine.copy(
                            phase = "loading",
                            message = "Loading bundled weights into RAM",
                            progressCurrent = readTotal.toLong(),
                            progressTotal = assetSize.toLong(),
                            modelBytes = readTotal.toLong(),
                        ),
                    )
                }
            }
            check(readTotal == assetSize) {
                "The bundled model ended at $readTotal of $assetSize bytes."
            }
            return bytes
        }
    }

    private companion object {
        const val ROUTER_LOG_TAG = "AgaveRouter"
        const val MAX_RETRIEVED_SKILLS = 5
        const val MAX_CANDIDATE_PREFIX_TOKENS = 210
    }
}

enum class AgaveScreen { Console, History, HistoryDetail }

data class EngineUiState(
    val phase: String = "loading",
    val message: String = "Starting Agave",
    val progressCurrent: Long = 0,
    val progressTotal: Long = 0,
    val modelBytes: Long = 0,
    val startupMs: Double = 0.0,
    val ready: Boolean = false,
)

data class AgaveUiState(
    val engine: EngineUiState = EngineUiState(),
    val current: LiveInteraction? = null,
    val history: List<StoredInteraction> = emptyList(),
    val selectedInteraction: StoredInteraction? = null,
    val screen: AgaveScreen = AgaveScreen.Console,
    val isGenerating: Boolean = false,
    val transientError: String? = null,
    val windowBrightness: Float? = null,
)
