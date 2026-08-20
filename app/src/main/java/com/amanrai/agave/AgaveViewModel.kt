package com.amanrai.agave

import android.app.Application
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
import com.amanrai.agave.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgaveViewModel(application: Application) : AndroidViewModel(application) {
    private val store = InteractionStore(application)
    private val toolExecutor = ToolExecutor(application)
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

        val interaction = LiveInteraction(prompt = prompt)
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
            val nativeError = withContext(Dispatchers.Default) {
                runCatching {
                    NativeBridge.generate(
                        query = prompt.toByteArray(Charsets.UTF_8),
                        showThinking = true,
                        maxNewTokens = 96,
                        callbacks = NativeCallbacks(
                            statusHandler = { phase, _, _, _ ->
                                if (phase == "reading" || phase == "generating") {
                                    _state.update { ui ->
                                        ui.copy(
                                            current = ui.current?.copy(
                                                status = if (phase == "reading") {
                                                    "reading prompt"
                                                } else {
                                                    "generating"
                                                },
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
                            errorHandler = { message ->
                                _state.update { ui ->
                                    ui.copy(
                                        current = ui.current?.copy(status = "error", error = message),
                                        transientError = message,
                                    )
                                }
                            },
                        ),
                    )
                }.fold(
                    onSuccess = { it },
                    onFailure = { it.message ?: "Native inference failed." },
                )
            }

            if (nativeError != null) {
                _state.update { ui ->
                    val failed = ui.current?.copy(status = "error", error = nativeError)
                    if (failed != null) completed = failed
                    ui.copy(current = failed, transientError = nativeError)
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
                val toolsJson = withContext(Dispatchers.IO) {
                    getApplication<Application>().assets.open("tools.json").bufferedReader().use {
                        it.readText()
                    }
                }
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
