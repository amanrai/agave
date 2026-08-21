package com.amanrai.agave.nativebridge

import androidx.annotation.Keep

object NativeBridge {
    init {
        System.loadLibrary("agave")
    }

    external fun initialize(
        modelBytes: ByteArray,
        toolsJson: ByteArray,
        callbacks: NativeCallbacks,
    ): String?

    external fun configureTools(
        toolsJson: ByteArray,
        callbacks: NativeCallbacks,
    ): String?

    external fun schemaPrefixTokenCount(toolsJson: ByteArray): Int

    external fun generate(
        query: ByteArray,
        showThinking: Boolean,
        requireToolCall: Boolean,
        maxNewTokens: Int,
        callbacks: NativeCallbacks,
    ): String?
}

@Keep
class NativeCallbacks(
    private val statusHandler: (phase: String, message: String, current: Int, total: Int) -> Unit = { _, _, _, _ -> },
    private val tokenHandler: (bytes: ByteArray, tokenId: Int, index: Int, elapsedMs: Double, deltaMs: Double) -> Unit = { _, _, _, _, _ -> },
    private val prefillHandler: (tokens: Int, milliseconds: Double, tokensPerSecond: Double) -> Unit = { _, _, _ -> },
    private val completeHandler: (
        tokens: Int,
        decodeMs: Double,
        decodeTokensPerSecond: Double,
        timeToFirstTokenMs: Double,
        confidence: Double,
        rawOutput: ByteArray,
    ) -> Unit = { _, _, _, _, _, _ -> },
    private val errorHandler: (message: String) -> Unit = {},
) {
    @Keep
    fun onStatus(phase: String, message: String, current: Int, total: Int) {
        statusHandler(phase, message, current, total)
    }

    @Keep
    fun onToken(bytes: ByteArray, tokenId: Int, index: Int, elapsedMs: Double, deltaMs: Double) {
        tokenHandler(bytes, tokenId, index, elapsedMs, deltaMs)
    }

    @Keep
    fun onPrefill(tokens: Int, milliseconds: Double, tokensPerSecond: Double) {
        prefillHandler(tokens, milliseconds, tokensPerSecond)
    }

    @Keep
    fun onComplete(
        tokens: Int,
        decodeMs: Double,
        decodeTokensPerSecond: Double,
        timeToFirstTokenMs: Double,
        confidence: Double,
        rawOutput: ByteArray,
    ) {
        completeHandler(
            tokens,
            decodeMs,
            decodeTokensPerSecond,
            timeToFirstTokenMs,
            confidence,
            rawOutput,
        )
    }

    @Keep
    fun onError(message: String) {
        errorHandler(message)
    }
}
