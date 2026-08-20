# Agave

Agave is an ARM64 Android demonstration of Needle 2 running fully on-device. It recreates the terminal UI from `needle-2-esp32` as a native Jetpack Compose app while using that repository's independent C99 inference engine through JNI.

## C99 engine source and attribution

The portable engine under [`app/src/main/cpp/engine`](app/src/main/cpp/engine) is copied from Andris Gauracs's [`andrisgauracs/needle-2-esp32`](https://github.com/andrisgauracs/needle-2-esp32) at commit [`61cafad`](https://github.com/andrisgauracs/needle-2-esp32/commit/61cafad7014a5664bb3ffd5f0c457ce5aa6598ae). That project is an independent C99 implementation of Needle 2's `.cact` inference format; it is not Cactus Compute's official Android runtime.

Agave adds the Android NDK build, JNI bridge, native row worker pool, Compose interface, streaming telemetry, and persistent interaction history around that engine. The upstream project and this repository are distributed under Apache-2.0; see [`LICENSE`](LICENSE).

## Current scope

- Bundles the official `Cactus-Compute/needle2` `.cact` weights in the APK.
- Reads the complete model asset into native RAM during startup and retains it for the process lifetime.
- Primes and snapshots the fixed tool-schema prefix once at startup.
- Streams every generated token from native code into Compose.
- Separately renders `<think>` reasoning and formatted `<tool_call>` JSON.
- Bundles `set_led`, `get_weather`, and `get_time` schemas; generated calls are displayed but not executed.
- Shows an on-screen LED that defaults to off, renders generated colors, animates `flash` calls, and turns off when `duration_seconds` expires.
- Records TTFT, prefill/decode throughput, confidence, and every token's elapsed/inter-token timing.
- Persists completed interactions and metrics in an on-device SQLite database.
- Runs offline and requests no Android permissions.

## Model

`app/src/main/assets/needle2.cact` was downloaded from:

- Repository: [`Cactus-Compute/needle2`](https://huggingface.co/Cactus-Compute/needle2)
- File: `needle2.cact`
- SHA-256: `b43aabfcaf1a6db6acf488076eab71d823c08697c7af4521fc1d174b60ede5ba`

The model and original engine are Apache-2.0 licensed. See `LICENSE`.

## Build

Requirements:

- JDK 17+
- Android SDK 35
- Android NDK `28.2.13676358`
- CMake 3.22.1
- An ARM64 Android device running Android 10 / API 29 or newer

```bash
cd agave
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK intentionally targets only `arm64-v8a`.

## Runtime path

1. Kotlin reads the uncompressed model asset into a byte array.
2. JNI copies the bytes into a retained native allocation.
3. `nd_model_open` binds tensors directly over that in-RAM `.cact` blob.
4. The fixed bundled schemas are compacted and compiled into the byte grammar.
5. The schema prompt prefix is evaluated once and snapshotted.
6. Each request rewinds to that snapshot, prefills the request, and performs grammar-constrained token stepping.
7. Native callbacks send token bytes and timestamps to the ViewModel.

## Metric definitions

- **TTFT:** wall-clock time from entry into native request processing until the first generated token has been selected and emitted. It includes tokenization and request prefill.
- **Prefill TPS:** request-suffix token count divided by model-step time for those prompt tokens. It excludes startup schema priming.
- **Decode TPS:** emitted-token count divided by the complete decode loop duration, including constrained sampling and the model step after each emitted token.
- **Token elapsed:** wall-clock time from request start to that token's callback.
- **Token gap:** wall-clock time since the preceding callback. The first token's gap equals TTFT.
- **p50/p95 gap:** percentile over inter-token gaps excluding the first token.

These are app-observed steady-clock measurements around the C engine; they are not values reported by Cactus's official Android binary.

## Documents

- [`docs/architecture.md`](docs/architecture.md)
- [`docs/known-limitations.md`](docs/known-limitations.md)
