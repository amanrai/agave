package com.amanrai.agave.skills

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SkillExecution(
    val runtime: String,
    val entrypoint: String,
)

data class SkillDefinition(
    val id: String,
    val version: Int,
    val order: Int,
    val enabled: Boolean,
    val tool: JSONObject,
    val retrieval: JSONObject?,
    val execution: SkillExecution,
    val source: SkillSource,
) {
    val toolName: String = tool.getString("name")
}

enum class SkillSource { BUNDLED, LOCAL }

class SkillCatalog private constructor(
    val skills: List<SkillDefinition>,
) {
    private val enabledByToolName = skills
        .filter { it.enabled }
        .associateBy { it.toolName }

    val enabledSkills: List<SkillDefinition>
        get() = skills.filter { it.enabled }

    val routerSkill: SkillDefinition
        get() = enabledSkills.single { it.execution.runtime == "router" }

    val retrievableSkills: List<SkillDefinition>
        get() = enabledSkills.filter { it.execution.runtime != "router" }

    fun findEnabledTool(name: String): SkillDefinition? = enabledByToolName[name]

    fun routerToolsJson(): String = toolsJson(listOf(routerSkill))

    fun toolsJson(selected: List<SkillDefinition>): String = JSONArray().apply {
        selected.forEach { put(JSONObject(it.tool.toString())) }
    }.toString()

    companion object {
        private val ID_PATTERN = Regex("[a-z][a-z0-9_]*")

        fun load(context: Context): SkillCatalog {
            val bundled = loadBundled(context)
            val local = loadLocal(context)
            val merged = linkedMapOf<String, SkillDefinition>()
            bundled.forEach { merged[it.id] = it }
            local.forEach { merged[it.id] = it }

            val ordered = merged.values.sortedWith(compareBy<SkillDefinition> { it.order }.thenBy { it.id })
            val duplicateTools = ordered
                .filter { it.enabled }
                .groupBy { it.toolName }
                .filterValues { it.size > 1 }
                .keys
            check(duplicateTools.isEmpty()) {
                "Duplicate enabled tool names: ${duplicateTools.sorted().joinToString()}"
            }
            val routers = ordered.filter { it.enabled && it.execution.runtime == "router" }
            check(routers.size == 1) { "Exactly one enabled router skill is required" }
            check(routers.single().toolName == "find_tool") {
                "The router skill tool must be named find_tool"
            }
            return SkillCatalog(ordered)
        }

        private fun loadBundled(context: Context): List<SkillDefinition> {
            val assets = context.assets
            return assets.list("skills").orEmpty().sorted().mapNotNull { folder ->
                val path = "skills/$folder/skill.json"
                runCatching {
                    assets.open(path).bufferedReader().use { reader ->
                        parse(folder, reader.readText(), SkillSource.BUNDLED)
                    }
                }.getOrElse { error ->
                    throw IllegalStateException("Invalid bundled skill at $path: ${error.message}", error)
                }
            }
        }

        private fun loadLocal(context: Context): List<SkillDefinition> {
            val root = File(context.filesDir, "skills")
            if (!root.exists()) root.mkdirs()
            return root.listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .sortedBy { it.name }
                .mapNotNull { folder ->
                    val manifest = File(folder, "skill.json")
                    if (!manifest.isFile) return@mapNotNull null
                    runCatching {
                        parse(folder.name, manifest.readText(), SkillSource.LOCAL)
                    }.getOrElse { error ->
                        throw IllegalStateException(
                            "Invalid local skill at ${manifest.absolutePath}: ${error.message}",
                            error,
                        )
                    }
                }
        }

        private fun parse(folder: String, raw: String, source: SkillSource): SkillDefinition {
            val root = JSONObject(raw)
            check(root.optInt("schema_version", -1) == 1) { "schema_version must be 1" }
            val id = root.getString("id")
            check(ID_PATTERN.matches(id)) { "invalid skill id '$id'" }
            check(folder == id) { "folder '$folder' must match skill id '$id'" }

            val version = root.optInt("version", 1)
            check(version > 0) { "version must be positive" }
            val tool = root.getJSONObject("tool")
            val toolName = tool.getString("name")
            check(ID_PATTERN.matches(toolName)) { "invalid tool name '$toolName'" }
            check(tool.has("description")) { "tool.description is required" }
            check(tool.has("parameters")) { "tool.parameters is required" }

            val executionJson = root.getJSONObject("execution")
            val runtime = executionJson.getString("runtime").trim()
            val entrypoint = executionJson.getString("entrypoint").trim()
            check(runtime.isNotEmpty()) { "execution.runtime is required" }
            check(entrypoint.isNotEmpty()) { "execution.entrypoint is required" }

            return SkillDefinition(
                id = id,
                version = version,
                order = root.optInt("order", 1000),
                enabled = root.optBoolean("enabled", true),
                tool = tool,
                retrieval = root.optJSONObject("retrieval"),
                execution = SkillExecution(runtime, entrypoint),
                source = source,
            )
        }
    }
}
