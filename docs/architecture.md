# Architecture

## Android layer

Agave is a single-activity Kotlin application using Jetpack Compose. `AgaveViewModel` owns startup and inference state and runs all native work off the main thread. Compose observes immutable `StateFlow` snapshots.

Completed interactions are stored with `SQLiteOpenHelper`. Each record includes the prompt, raw output, extracted reasoning, formatted call, aggregate inference metrics, and a JSON token timeline.

## Native layer

`libagave.so` contains:

- The portable C99 Needle 2 `.cact` reader, tokenizer, quantized kernels, model, grammar, and sampler copied from `andrisgauracs/needle-2-esp32`.
- A C++17 JNI bridge.
- A persistent four-way CPU row pool: three worker threads plus the inference thread.
- Startup model loading and prompt-prefix snapshotting.
- Synchronous token stepping with callbacks into Kotlin.

The C99 engine is used instead of Cactus's prebuilt Needle Android library because the public `needle.h` API exposes only blocking completion. It cannot provide genuine streaming or externally observed TTFT.

## UI state

The console follows the source TUI's hierarchy:

- model/status header
- request panel
- streamed reasoning panel
- formatted tool-call panel
- LED preview
- live performance panel
- request composer

History is a persistent peer view. Selecting a record opens its output, aggregate metrics, and complete per-token timing timeline.

## Threading

- Model asset I/O: `Dispatchers.IO`
- Native initialization and inference: `Dispatchers.Default`
- Quantized matrix rows: native persistent worker pool
- UI: Compose main thread through `StateFlow`
- SQLite reads/writes: `Dispatchers.IO`

Only one inference call can run at a time. Native model access is protected by one process-wide mutex.
