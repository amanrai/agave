# Agave

Agave is an ARM64 Android demonstration of Needle 2 running fully on-device. It presents a native Material 3 Jetpack Compose interface while using the independent C99 inference engine from `needle-2-esp32` through JNI.

## C99 engine source and attribution

The portable engine under [`app/src/main/cpp/engine`](app/src/main/cpp/engine) is copied from Andris Gauracs's [`andrisgauracs/needle-2-esp32`](https://github.com/andrisgauracs/needle-2-esp32) at commit [`61cafad`](https://github.com/andrisgauracs/needle-2-esp32/commit/61cafad7014a5664bb3ffd5f0c457ce5aa6598ae). That project is an independent C99 implementation of Needle 2's `.cact` inference format; it is not Cactus Compute's official Android runtime.

Agave adds the Android NDK build, JNI bridge, native row worker pool, Compose interface, streaming telemetry, and persistent interaction history around that engine. The upstream project and this repository are distributed under Apache-2.0; see [`LICENSE`](LICENSE).

## Current scope

- Bundles the official `Cactus-Compute/needle2` `.cact` weights in the APK.
- Reads the complete model asset into native RAM during startup and retains it for the process lifetime.
- Keeps only `find_tool` in the waiting-state prompt, then BM25-retrieves a token-budgeted skill subset and re-primes Needle for a second selection pass.
- Streams every generated token from native code into Compose.
- Renders the complete two-stage process: `find_tool` reasoning/call, BM25 keywords/scores/candidates, selected-tool reasoning/call, and execution result.
- Packages 37 independent skill manifests: one router, three fully executed Android skills, and 33 selection-only skills spanning common Android capabilities.
- Executes `get_time` locally, applies Agave-window brightness, and controls Android's media-volume stream; selection-only skills return an explicit non-execution result.
- Records TTFT, prefill/decode throughput, confidence, and every token's elapsed/inter-token timing.
- Persists completed interactions, tool results, and metrics in an on-device SQLite database.
- Runs offline. Media-volume control uses Android's normal `MODIFY_AUDIO_SETTINGS` permission and requires no runtime prompt.

## Model

`app/src/main/assets/needle2.cact` was downloaded from:

- Repository: [`Cactus-Compute/needle2`](https://huggingface.co/Cactus-Compute/needle2)
- File: `needle2.cact`
- SHA-256: `b43aabfcaf1a6db6acf488076eab71d823c08697c7af4521fc1d174b60ede5ba`

The model and original engine are Apache-2.0 licensed. See `LICENSE`.

The checked-in model can be verified or restored directly from its public source with:

```bash
./scripts/download-model.sh
```

The script verifies the expected SHA-256 digest before installing the asset.

## Build

Requirements include JDK 17, Android SDK 35, Android NDK `28.2.13676358`, CMake 3.22.1, and an ARM64 device running API 29 or newer. See the complete [build and model-download notes](docs/build.md).

```bash
git clone https://github.com/amanrai/agave.git
cd agave
./scripts/download-model.sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK intentionally targets only `arm64-v8a`.

## Runtime path

1. Kotlin reads the uncompressed model asset into a byte array.
2. JNI copies the bytes into a retained native allocation.
3. `nd_model_open` binds tensors directly over that in-RAM `.cact` blob.
4. `SkillCatalog` merges bundled and local skill folders and indexes retrievable skills with BM25.
5. The waiting-state prefix is compiled and primed with only `find_tool`.
6. The first inference emits keyword strings for the user's request.
7. BM25 ranks skills; Agave packs candidates within a measured prefix-token budget.
8. The candidate schemas are compiled and primed, then the original request is inferred again.
9. The selected tool executes, its result is persisted, and Agave restores the `find_tool` waiting state.

## Metric definitions

- **TTFT:** wall-clock time from entry into native request processing until the first generated token has been selected and emitted. It includes tokenization and request prefill.
- **Prefill TPS:** request-suffix token count divided by model-step time for those prompt tokens. It excludes startup schema priming.
- **Decode TPS:** emitted-token count divided by the complete decode loop duration, including constrained sampling and the model step after each emitted token.
- **Token elapsed:** wall-clock time from request start to that token's callback.
- **Token gap:** wall-clock time since the preceding callback. The first token's gap equals TTFT.
- **p50/p95 gap:** percentile over inter-token gaps excluding the first token.

These are app-observed steady-clock measurements around the C engine; they are not values reported by Cactus's official Android binary.

## Documents

- [`docs/skills.md`](docs/skills.md)
- [`docs/build.md`](docs/build.md)
- [`docs/architecture.md`](docs/architecture.md)
- [`docs/known-limitations.md`](docs/known-limitations.md)
