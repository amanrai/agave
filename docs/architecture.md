# Architecture

## Android layer

Agave is a single-activity Kotlin application using Jetpack Compose. `AgaveViewModel` owns startup and inference state and runs all native work off the main thread. Compose observes immutable `StateFlow` snapshots.

`SkillCatalog` discovers bundled `assets/skills/<id>/skill.json` manifests and mutable private-storage overrides. It validates and orders the merged catalog, retains host-only retrieval/execution metadata, and exposes model-visible `tool` objects for candidate-specific native priming. `Bm25SkillRetriever` indexes normal skills; the waiting-state prefix contains only `find_tool`.

Completed interactions are stored with `SQLiteOpenHelper`. Each record includes the prompt, raw output, extracted reasoning, formatted call, execution result, aggregate inference metrics, and a JSON token timeline.

Each command uses two native generations. The first must call `find_tool` with a string array. BM25 ranks matching skills, and Agave packs candidates under a measured 210-token prefix budget. JNI recompiles the grammar and re-primes the model with those schemas; the second generation receives the original command and selects the executable tool. Agave restores the `find_tool` snapshot before returning to the waiting state.

After the second generation completes, an allowlisted Kotlin `ToolExecutor` validates and dispatches the complete call array. `get_time` uses `java.time`, `set_volume` uses Android's media `AudioManager`, and `set_brightness` emits a window-scoped value that Compose applies to the active `Activity`. The broader catalog uses the `selection_only` runtime and returns an explicit non-execution result. Tool execution never occurs against partial streaming JSON.

## Native layer

`libagave.so` contains:

- The portable C99 Needle 2 `.cact` reader, tokenizer, quantized kernels, model, grammar, and sampler copied from `andrisgauracs/needle-2-esp32`.
- A C++17 JNI bridge.
- A persistent four-way CPU row pool: three worker threads plus the inference thread.
- Startup model loading and prompt-prefix snapshotting.
- Synchronous token stepping with callbacks into Kotlin.

The C99 engine is used instead of Cactus's prebuilt Needle Android library because the public `needle.h` API exposes only blocking completion. It cannot provide genuine streaming or externally observed TTFT.

## UI state

The console uses a Material 3 layout with a black canvas, request and reasoning cards, tool-call/result cards, live performance metrics, and a persistent request composer. History is a peer tab; selecting a record opens its generated output, tool result, aggregate metrics, and complete per-token timing timeline.

## Threading

- Model asset I/O: `Dispatchers.IO`
- Native initialization and inference: `Dispatchers.Default`
- Quantized matrix rows: native persistent worker pool
- Tool execution: inference callback thread after complete JSON is available
- UI and window brightness application: Compose main thread through `StateFlow`
- SQLite reads/writes: `Dispatchers.IO`

Only one inference call can run at a time. Native model access is protected by one process-wide mutex.
