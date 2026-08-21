package com.amanrai.agave.skills

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

data class ScoredSkill(
    val skill: SkillDefinition,
    val score: Double,
)

class Bm25SkillRetriever(
    skills: List<SkillDefinition>,
    private val k1: Double = 1.2,
    private val b: Double = 0.75,
) {
    private data class Document(
        val skill: SkillDefinition,
        val terms: List<String>,
        val frequencies: Map<String, Int>,
    )

    private val documents = skills.map { skill ->
        val terms = tokenize(searchableText(skill))
        Document(skill, terms, terms.groupingBy { it }.eachCount())
    }
    private val averageLength = documents.map { it.terms.size }.average().takeIf { !it.isNaN() } ?: 0.0
    private val documentFrequency = buildMap<String, Int> {
        documents.forEach { document ->
            document.frequencies.keys.forEach { term -> put(term, getOrDefault(term, 0) + 1) }
        }
    }

    fun search(keywords: List<String>): List<ScoredSkill> {
        val queryTerms = keywords.flatMap(::tokenize).distinct()
        if (queryTerms.isEmpty() || documents.isEmpty()) return emptyList()
        val count = documents.size.toDouble()

        return documents.mapNotNull { document ->
            val lengthRatio = if (averageLength > 0.0) document.terms.size / averageLength else 1.0
            var score = 0.0
            queryTerms.forEach { term ->
                val frequency = document.frequencies[term]?.toDouble() ?: return@forEach
                val containing = documentFrequency[term]?.toDouble() ?: 0.0
                val idf = ln(1.0 + (count - containing + 0.5) / (containing + 0.5))
                val denominator = frequency + k1 * (1.0 - b + b * lengthRatio)
                score += idf * (frequency * (k1 + 1.0) / denominator)
            }
            if (score > 0.0) ScoredSkill(document.skill, score) else null
        }.sortedWith(
            compareByDescending<ScoredSkill> { it.score }
                .thenBy { it.skill.order }
                .thenBy { it.skill.id },
        )
    }

    private fun searchableText(skill: SkillDefinition): String = buildString {
        append(skill.id).append(' ')
        append(skill.toolName).append(' ')
        append(skill.tool.optString("description")).append(' ')
        appendJsonStrings(skill.tool.optJSONObject("parameters"), this)
        skill.retrieval?.let {
            appendJsonStrings(it, this)
            append(' ')
            appendJsonStrings(it, this) // retrieval metadata receives a small field boost
        }
    }

    private fun appendJsonStrings(value: Any?, destination: StringBuilder) {
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                destination.append(key).append(' ')
                appendJsonStrings(value.opt(key), destination)
            }
            is JSONArray -> repeat(value.length()) { appendJsonStrings(value.opt(it), destination) }
            is String -> destination.append(value).append(' ')
        }
    }

    companion object {
        private val TERM = Regex("[a-z0-9]+")

        fun tokenize(value: String): List<String> = TERM
            .findAll(value.lowercase())
            .map { it.value }
            .toList()
    }
}
