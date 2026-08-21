package com.amanrai.agave.tools

import android.content.Context
import android.media.AudioManager
import com.amanrai.agave.skills.SkillCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

data class ToolExecution(
    val resultJson: String,
    val windowBrightness: Float? = null,
)

class ToolExecutor(
    context: Context,
    private val skills: SkillCatalog,
) {
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun execute(toolCallJson: String): ToolExecution {
        if (toolCallJson.isBlank()) return ToolExecution("")

        return runCatching {
            val calls = JSONArray(toolCallJson)
            var windowBrightness: Float? = null
            val results = JSONArray().apply {
                repeat(calls.length()) { index ->
                    val call = calls.optJSONObject(index)
                    if (call == null) {
                        put(errorResult("unknown", "Tool call $index is not an object."))
                        return@repeat
                    }
                    val name = call.optString("name")
                    val arguments = call.optJSONObject("arguments") ?: JSONObject()
                    val handled = executeOne(name, arguments)
                    put(handled.result)
                    handled.windowBrightness?.let { windowBrightness = it }
                }
            }
            ToolExecution(results.toString(2), windowBrightness)
        }.getOrElse { error ->
            ToolExecution(
                JSONArray()
                    .put(errorResult("unknown", error.message ?: "Tool call JSON is invalid."))
                    .toString(2),
            )
        }
    }

    private fun executeOne(name: String, arguments: JSONObject): HandledTool {
        val skill = skills.findEnabledTool(name)
            ?: return HandledTool(errorResult(name.ifBlank { "unknown" }, "Skill is not enabled."))
        if (skill.execution.runtime == "selection_only") {
            return HandledTool(
                errorResult(name, "This skill is available for selection experiments only."),
            )
        }
        if (skill.execution.runtime != "android") {
            return HandledTool(
                errorResult(name, "Runtime '${skill.execution.runtime}' is not available yet."),
            )
        }
        return when (skill.execution.entrypoint) {
            "get_time" -> HandledTool(executeGetTime(arguments))
            "set_brightness" -> executeSetBrightness(arguments)
            "set_volume" -> HandledTool(executeSetVolume(arguments))
            else -> HandledTool(
                errorResult(name, "Android entrypoint '${skill.execution.entrypoint}' is not registered."),
            )
        }
    }

    private fun executeGetTime(arguments: JSONObject): JSONObject {
        val location = arguments.optString("location").trim().ifBlank { "here" }
        val zone = resolveZone(location)
            ?: return errorResult(
                "get_time",
                "Unknown location '$location'. Use here or an IANA time-zone ID.",
            )
        val now = ZonedDateTime.now(zone)
        val locale = Locale.getDefault()
        val result = JSONObject()
            .put("location", location)
            .put("time", now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)))
            .put("date", now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)))
            .put("timezone", zone.id)
            .put("utc_offset", now.offset.id)
            .put("epoch_ms", now.toInstant().toEpochMilli())

        return JSONObject()
            .put("name", "get_time")
            .put("status", "ok")
            .put("result", result)
    }

    private fun executeSetBrightness(arguments: JSONObject): HandledTool {
        val level = readPercent(arguments, "brightness_percent")
            ?: return HandledTool(
                errorResult("set_brightness", "brightness_percent must be a number from 0 to 100."),
            )
        val percent = level.coerceIn(0.0, 100.0)
        return HandledTool(
            result = JSONObject()
                .put("name", "set_brightness")
                .put("status", "ok")
                .put(
                    "result",
                    JSONObject()
                        .put("level_percent", percent)
                        .put("scope", "Agave window"),
                ),
            windowBrightness = (percent / 100.0).toFloat(),
        )
    }

    private fun executeSetVolume(arguments: JSONObject): JSONObject {
        val level = readPercent(arguments, "volume_percent")
            ?: return errorResult("set_volume", "volume_percent must be a number from 0 to 100.")
        val requestedPercent = level.coerceIn(0.0, 100.0)

        return runCatching {
            val minimum = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val index = (minimum + (maximum - minimum) * (requestedPercent / 100.0))
                .roundToInt()
                .coerceIn(minimum, maximum)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            val actualIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val actualPercent = if (maximum > minimum) {
                ((actualIndex - minimum) * 100.0 / (maximum - minimum)).roundToInt()
            } else {
                0
            }
            JSONObject()
                .put("name", "set_volume")
                .put("status", "ok")
                .put(
                    "result",
                    JSONObject()
                        .put("level_percent", actualPercent)
                        .put("stream", "media"),
                )
        }.getOrElse { error ->
            errorResult("set_volume", error.message ?: "Android rejected the volume change.")
        }
    }

    private fun readPercent(arguments: JSONObject, name: String): Double? {
        val key = when {
            arguments.has(name) -> name
            arguments.has("level") -> "level"
            else -> return null
        }
        return arguments.optDouble(key, Double.NaN).takeIf { it.isFinite() }
    }

    private fun resolveZone(location: String): ZoneId? {
        val normalized = location.lowercase(Locale.US).trim()
        if (normalized in setOf("here", "local", "current location")) {
            return ZoneId.systemDefault()
        }
        CITY_ZONES[normalized]?.let { return ZoneId.of(it) }
        return runCatching { ZoneId.of(location) }.getOrNull()
    }

    private fun errorResult(name: String, message: String): JSONObject = JSONObject()
        .put("name", name)
        .put("status", "error")
        .put("error", message)

    private data class HandledTool(
        val result: JSONObject,
        val windowBrightness: Float? = null,
    )

    private companion object {
        val CITY_ZONES = mapOf(
            "amsterdam" to "Europe/Amsterdam",
            "bangalore" to "Asia/Kolkata",
            "bengaluru" to "Asia/Kolkata",
            "beijing" to "Asia/Shanghai",
            "berlin" to "Europe/Berlin",
            "boston" to "America/New_York",
            "cairo" to "Africa/Cairo",
            "chicago" to "America/Chicago",
            "delhi" to "Asia/Kolkata",
            "denver" to "America/Denver",
            "dubai" to "Asia/Dubai",
            "hong kong" to "Asia/Hong_Kong",
            "johannesburg" to "Africa/Johannesburg",
            "london" to "Europe/London",
            "los angeles" to "America/Los_Angeles",
            "melbourne" to "Australia/Melbourne",
            "mexico city" to "America/Mexico_City",
            "mumbai" to "Asia/Kolkata",
            "new york" to "America/New_York",
            "paris" to "Europe/Paris",
            "san francisco" to "America/Los_Angeles",
            "seattle" to "America/Los_Angeles",
            "seoul" to "Asia/Seoul",
            "shanghai" to "Asia/Shanghai",
            "singapore" to "Asia/Singapore",
            "sydney" to "Australia/Sydney",
            "tokyo" to "Asia/Tokyo",
            "toronto" to "America/Toronto",
            "utc" to "UTC",
            "vancouver" to "America/Vancouver",
        )
    }
}
