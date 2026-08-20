package com.amanrai.agave.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.amanrai.agave.model.InferenceMetrics
import com.amanrai.agave.model.LiveInteraction
import com.amanrai.agave.model.StoredInteraction
import com.amanrai.agave.model.tokenTimingsFromJson
import com.amanrai.agave.model.tokenTimingsToJson

class InteractionStore(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE interactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                prompt TEXT NOT NULL,
                raw_output TEXT NOT NULL,
                reasoning TEXT NOT NULL,
                tool_call TEXT NOT NULL,
                tool_result TEXT NOT NULL DEFAULT '',
                prefill_tokens INTEGER NOT NULL,
                prefill_ms REAL NOT NULL,
                prefill_tps REAL NOT NULL,
                decode_tokens INTEGER NOT NULL,
                decode_ms REAL NOT NULL,
                decode_tps REAL NOT NULL,
                ttft_ms REAL NOT NULL,
                confidence REAL NOT NULL,
                token_timings TEXT NOT NULL,
                error TEXT
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE interactions ADD COLUMN tool_result TEXT NOT NULL DEFAULT ''")
        }
    }

    @Synchronized
    fun insert(interaction: LiveInteraction): StoredInteraction {
        val values = ContentValues().apply {
            put("created_at", interaction.createdAt)
            put("prompt", interaction.prompt)
            put("raw_output", interaction.rawOutput)
            put("reasoning", interaction.reasoning)
            put("tool_call", interaction.toolCall)
            put("tool_result", interaction.toolResult)
            put("prefill_tokens", interaction.metrics.prefillTokens)
            put("prefill_ms", interaction.metrics.prefillMs)
            put("prefill_tps", interaction.metrics.prefillTps)
            put("decode_tokens", interaction.metrics.decodeTokens)
            put("decode_ms", interaction.metrics.decodeMs)
            put("decode_tps", interaction.metrics.decodeTps)
            put("ttft_ms", interaction.metrics.ttftMs)
            put("confidence", interaction.metrics.confidence)
            put("token_timings", tokenTimingsToJson(interaction.tokenTimings))
            put("error", interaction.error)
        }
        val id = writableDatabase.insertOrThrow("interactions", null, values)
        return interaction.toStored(id)
    }

    @Synchronized
    fun loadAll(): List<StoredInteraction> {
        return readableDatabase.query(
            "interactions",
            null,
            null,
            null,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val metrics = InferenceMetrics(
                        prefillTokens = cursor.getInt(cursor.getColumnIndexOrThrow("prefill_tokens")),
                        prefillMs = cursor.getDouble(cursor.getColumnIndexOrThrow("prefill_ms")),
                        prefillTps = cursor.getDouble(cursor.getColumnIndexOrThrow("prefill_tps")),
                        decodeTokens = cursor.getInt(cursor.getColumnIndexOrThrow("decode_tokens")),
                        decodeMs = cursor.getDouble(cursor.getColumnIndexOrThrow("decode_ms")),
                        decodeTps = cursor.getDouble(cursor.getColumnIndexOrThrow("decode_tps")),
                        ttftMs = cursor.getDouble(cursor.getColumnIndexOrThrow("ttft_ms")),
                        confidence = cursor.getDouble(cursor.getColumnIndexOrThrow("confidence")),
                    )
                    add(
                        StoredInteraction(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                            prompt = cursor.getString(cursor.getColumnIndexOrThrow("prompt")),
                            rawOutput = cursor.getString(cursor.getColumnIndexOrThrow("raw_output")),
                            reasoning = cursor.getString(cursor.getColumnIndexOrThrow("reasoning")),
                            toolCall = cursor.getString(cursor.getColumnIndexOrThrow("tool_call")),
                            toolResult = cursor.getString(cursor.getColumnIndexOrThrow("tool_result")),
                            metrics = metrics,
                            tokenTimings = tokenTimingsFromJson(
                                cursor.getString(cursor.getColumnIndexOrThrow("token_timings")),
                            ),
                            error = cursor.getString(cursor.getColumnIndexOrThrow("error")),
                        ),
                    )
                }
            }
        }
    }

    private fun LiveInteraction.toStored(id: Long) = StoredInteraction(
        id = id,
        createdAt = createdAt,
        prompt = prompt,
        rawOutput = rawOutput,
        reasoning = reasoning,
        toolCall = toolCall,
        toolResult = toolResult,
        metrics = metrics,
        tokenTimings = tokenTimings,
        error = error,
    )

    private companion object {
        const val DATABASE_NAME = "agave.db"
        const val DATABASE_VERSION = 2
    }
}
