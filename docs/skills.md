# Skills

Agave packages each model-visible capability as an independent skill directory. There is no aggregate `tools.json` file.

## Directory layout

Bundled, read-only skills live in Android assets:

```text
app/src/main/assets/skills/
├── find_tool/skill.json
├── get_time/skill.json
├── get_battery/skill.json
├── open_url/skill.json
├── post_notification/skill.json
├── set_brightness/skill.json
├── set_volume/skill.json
└── … 30 additional skill folders
```

The APK currently contains 37 manifests: one router, three fully executed Android skills, and 33 selection-only skills covering common Android tasks.

At startup, `SkillCatalog` discovers every subdirectory, validates its `skill.json`, and builds a BM25 index over retrievable skills. Needle's waiting-state prompt contains only the router skill, `find_tool`. Execution and retrieval metadata remain on the Kotlin side and are never included in the model prompt.

## Manifest format

```json
{
  "schema_version": 1,
  "id": "get_time",
  "version": 1,
  "order": 30,
  "enabled": true,
  "tool": {
    "name": "get_time",
    "description": "Get clock time; use here for the current location",
    "parameters": {
      "type": "object",
      "properties": {
        "location": {
          "type": "string"
        }
      }
    }
  },
  "execution": {
    "runtime": "android",
    "entrypoint": "get_time"
  }
}
```

- `schema_version` identifies the `skill.json` format and is currently `1`.
- `id` must match the directory name and use lowercase letters, digits, and underscores.
- `version` is the skill's own positive integer revision.
- `order` makes schema ordering explicit because ordering can affect model routing.
- `enabled` controls whether the tool is exposed to Needle and executable.
- `tool` is the exact model-visible tool schema.
- `retrieval` supplies host-only BM25 tags and example requests.
- `execution` is host-only dispatch metadata.

Fully executed skills use the `android` runtime and allowlisted Kotlin entrypoints. Skills marked `selection_only` participate in retrieval and constrained model selection but return an explicit non-execution result. `find_tool` uses the special `router` runtime.

## Two-stage routing

For every command:

1. Needle starts from a prefix containing only `find_tool`.
2. Grammar-constrained inference emits a non-empty `keywords` string array.
3. BM25 searches skill IDs, names, descriptions, parameters, tags, and examples.
4. Ranked candidates are added while the measured schema prefix remains at or below 210 tokens.
5. Agave re-primes Needle with only those candidate schemas.
6. The original user command is inferred again to select and populate the final tool.
7. Agave executes the result and restores the `find_tool` prefix before accepting another command.

The UI and history expose both reasoning passes, the `find_tool` call, keywords, BM25 scores, token-budget decisions, final tool call, and execution result.

## Local and future on-device skills

`SkillCatalog` also scans Agave's mutable private storage:

```text
<app files directory>/skills/<skill_id>/skill.json
```

A local skill with the same `id` overrides its bundled definition. This establishes the storage model needed for an eventual on-device skill editor without making APK assets writable.

A future Python skill can use the same directory as its source files:

```text
skills/example_python_skill/
├── skill.json
└── main.py
```

Its execution block would declare a Python runtime and source entrypoint:

```json
{
  "runtime": "python",
  "entrypoint": "main.py"
}
```

Python execution is not implemented yet. Such a skill can be represented by the catalog, but execution returns an unsupported-runtime error until a sandboxed Python runtime is added. Editing a skill currently requires rebuilding or restarting the catalog and BM25 index.

## Validation and precedence

Startup fails with a visible engine error when a skill manifest is malformed. The catalog currently validates:

- manifest schema version
- skill and tool identifiers
- directory/id agreement
- positive skill version
- required tool schema fields
- runtime and entrypoint presence
- duplicate enabled tool names

Bundled skills load first; local skills then override by `id`. The merged catalog is sorted by `order` and then `id`.
