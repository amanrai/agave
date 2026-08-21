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

## Built skills

`implemented fully` means both model selection and a functional Android/JVM executor are present. `implemented in skill selection only` means the skill participates in BM25 retrieval and grammar-constrained selection, then returns an explicit non-execution result.

| Skill | Capability | Status |
|---|---|---|
| `automate_ui` | Perform a described Android interface action | `implemented in skill selection only` |
| `capture_screen` | Capture the Android screen | `implemented in skill selection only` |
| `compose_email` | Compose an email draft without sending it | `implemented in skill selection only` |
| `compose_message` | Compose an SMS message without sending it | `implemented in skill selection only` |
| `copy_text` | Copy text to the Android clipboard | `implemented in skill selection only` |
| `create_calendar_event` | Create an event in the Android calendar | `implemented in skill selection only` |
| `dial_number` | Open a phone number in the dialer without calling | `implemented in skill selection only` |
| `draw_overlay` | Show text over other Android apps | `implemented in skill selection only` |
| `find_tool` | Retrieve candidate skills for a user command | `router` |
| `get_battery` | Get battery charge and charging state | `implemented in skill selection only` |
| `get_connectivity` | Get the active Android network connection | `implemented in skill selection only` |
| `get_contacts` | Search Android contacts | `implemented in skill selection only` |
| `get_device_info` | Get Android device and screen information | `implemented in skill selection only` |
| `get_location` | Get the phone's current location | `implemented in skill selection only` |
| `get_notifications` | Read current Android notifications | `implemented in skill selection only` |
| `get_step_count` | Get the device step count for a period | `implemented in skill selection only` |
| `get_time` | Get the current time, date, and timezone | `implemented fully` |
| `get_weather` | Get weather for a location | `implemented in skill selection only` |
| `keep_screen_awake` | Keep the Agave screen awake or allow sleep | `implemented in skill selection only` |
| `lock_device` | Lock the Android device | `implemented in skill selection only` |
| `open_map` | Open a place or directions in a maps app | `implemented in skill selection only` |
| `open_settings` | Open an Android Settings panel | `implemented in skill selection only` |
| `open_url` | Open a web URL in the default browser | `implemented in skill selection only` |
| `post_notification` | Post an Android notification | `implemented in skill selection only` |
| `record_audio` | Record audio from the microphone | `implemented in skill selection only` |
| `scan_nearby_devices` | Scan for nearby Bluetooth devices | `implemented in skill selection only` |
| `set_alarm` | Create an alarm through the Android clock app | `implemented in skill selection only` |
| `set_brightness` | Change only the Agave screen brightness | `implemented fully` |
| `set_do_not_disturb` | Enable or disable Android Do Not Disturb | `implemented in skill selection only` |
| `set_exact_alarm` | Schedule an exact background alarm | `implemented in skill selection only` |
| `set_orientation` | Set the Agave screen orientation | `implemented in skill selection only` |
| `set_timer` | Start a countdown timer | `implemented in skill selection only` |
| `set_volume` | Change Android media volume | `implemented fully` |
| `share_text` | Share text through the Android share sheet | `implemented in skill selection only` |
| `speak_text` | Speak text aloud with Android text-to-speech | `implemented in skill selection only` |
| `take_photo` | Take a photo with an Android camera | `implemented in skill selection only` |
| `vibrate` | Vibrate the phone for a duration | `implemented in skill selection only` |

Each manifest lives under [`app/src/main/assets/skills`](app/src/main/assets/skills). See [`docs/skills.md`](docs/skills.md) for the manifest format and routing lifecycle.

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
