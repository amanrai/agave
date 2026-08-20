package com.amanrai.agave.model

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import kotlin.math.ceil

const val UNKNOWN_METRIC = -1.0

data class TokenTiming(
    val index: Int,
    val tokenId: Int,
    val text: String,
    val elapsedMs: Double,
    val deltaMs: Double,
)

data class InferenceMetrics(
    val prefillTokens: Int = 0,
    val prefillMs: Double = 0.0,
    val prefillTps: Double = 0.0,
    val decodeTokens: Int = 0,
    val decodeMs: Double = 0.0,
    val decodeTps: Double = 0.0,
    val ttftMs: Double = UNKNOWN_METRIC,
    val confidence: Double = UNKNOWN_METRIC,
) {
    fun interTokenPercentile(timings: List<TokenTiming>, percentile: Double): Double {
        val values = timings.drop(1).map { it.deltaMs }.sorted()
        if (values.isEmpty()) return UNKNOWN_METRIC
        val index = (ceil(percentile * values.size).toInt() - 1).coerceIn(values.indices)
        return values[index]
    }
}

data class LiveInteraction(
    val prompt: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "reading prompt",
    val rawOutput: String = "",
    val reasoning: String = "",
    val toolCall: String = "",
    val metrics: InferenceMetrics = InferenceMetrics(),
    val tokenTimings: List<TokenTiming> = emptyList(),
    val error: String? = null,
)

data class StoredInteraction(
    val id: Long,
    val createdAt: Long,
    val prompt: String,
    val rawOutput: String,
    val reasoning: String,
    val toolCall: String,
    val metrics: InferenceMetrics,
    val tokenTimings: List<TokenTiming>,
    val error: String?,
)

fun parseStream(raw: String): Pair<String, String> {
    val reasoning = taggedContent(raw, "<think>", "</think>")
    val call = taggedContent(raw, "<tool_call>", "</tool_call>")
    return reasoning.trim() to formatJsonOrRaw(call.trim())
}

private fun taggedContent(raw: String, start: String, end: String): String {
    val startAt = raw.indexOf(start)
    if (startAt < 0) return ""
    val contentAt = startAt + start.length
    val endAt = raw.indexOf(end, contentAt)
    return if (endAt >= 0) raw.substring(contentAt, endAt) else raw.substring(contentAt)
}

private fun formatJsonOrRaw(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        when {
            value.startsWith("[") -> normalizeToolLocations(JSONArray(value)).toString(2)
            value.startsWith("{") -> JSONObject(value).toString(2)
            else -> value
        }
    }.getOrDefault(value)
}

private fun normalizeToolLocations(calls: JSONArray): JSONArray {
    repeat(calls.length()) { index ->
        val call = calls.optJSONObject(index) ?: return@repeat
        if (call.optString("name") !in setOf("get_weather", "get_time")) return@repeat
        val arguments = call.optJSONObject("arguments") ?: JSONObject().also {
            call.put("arguments", it)
        }
        val location = arguments.optString("location").trim()
        if (location.isBlank() || location.equals("here", ignoreCase = true)) {
            arguments.put("location", "here")
        }
    }
    return calls
}

fun TokenTiming.toJson(): JSONObject = JSONObject()
    .put("index", index)
    .put("token_id", tokenId)
    .put("text", text)
    .put("elapsed_ms", elapsedMs)
    .put("delta_ms", deltaMs)

fun tokenTimingsToJson(timings: List<TokenTiming>): String = JSONArray().apply {
    timings.forEach { put(it.toJson()) }
}.toString()

fun tokenTimingsFromJson(value: String): List<TokenTiming> = runCatching {
    val array = JSONArray(value)
    buildList(array.length()) {
        repeat(array.length()) { index ->
            val item = array.getJSONObject(index)
            add(
                TokenTiming(
                    index = item.getInt("index"),
                    tokenId = item.getInt("token_id"),
                    text = item.getString("text"),
                    elapsedMs = item.getDouble("elapsed_ms"),
                    deltaMs = item.getDouble("delta_ms"),
                ),
            )
        }
    }
}.getOrDefault(emptyList())

fun decodeToken(bytes: ByteArray): String = String(bytes, StandardCharsets.UTF_8)
