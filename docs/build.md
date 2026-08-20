# Building Agave

Agave builds only for 64-bit ARM Android devices. An emulator or an x86/x86_64 device cannot run the resulting APK.

## Prerequisites

- JDK 17 or newer
- Android SDK Platform 35
- Android SDK Build Tools
- Android NDK `28.2.13676358`
- CMake 3.22.1
- `curl` plus either `sha256sum` or `shasum`
- A physical `arm64-v8a` Android device running API 29 or newer for installation

Android Studio can install the SDK, NDK, and CMake versions through **Tools → SDK Manager**. If the Android SDK is not in its standard location, create an untracked `local.properties` file containing:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

## Download the base model

Run the repository script before building:

```bash
./scripts/download-model.sh
```

It downloads the public [`needle2.cact`](https://huggingface.co/Cactus-Compute/needle2/blob/main/needle2.cact) base model from `Cactus-Compute/needle2` into:

```text
app/src/main/assets/needle2.cact
```

The script follows Hugging Face redirects and verifies this SHA-256 digest before replacing the destination:

```text
b43aabfcaf1a6db6acf488076eab71d823c08697c7af4521fc1d174b60ede5ba
```

If a verified copy is already present, the script exits without downloading it again. An alternate mirror can be supplied without editing the script:

```bash
AGAVE_MODEL_URL=https://example.invalid/needle2.cact ./scripts/download-model.sh
```

A custom destination can be passed as the first argument, although Gradle only packages the standard asset path:

```bash
./scripts/download-model.sh /tmp/needle2.cact
```

No Hugging Face account or access token is required for the public base model. A checksum failure leaves any existing destination untouched.

## Build the debug APK

From the repository root:

```bash
./scripts/download-model.sh
./gradlew :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The `.cact` asset is deliberately stored uncompressed in the APK. At startup, Agave reads it into a retained native allocation and binds the C99 engine directly over those bytes.

## Install on a device

Enable USB debugging, connect an ARM64 device, and verify its ABI:

```bash
adb shell getprop ro.product.cpu.abi
```

The result must be `arm64-v8a`. Install or update Agave with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Verification

Run the Android lint task:

```bash
./gradlew :app:lintDebug
```

The C99 engine bundled under `app/src/main/cpp/engine` comes from [`andrisgauracs/needle-2-esp32`](https://github.com/andrisgauracs/needle-2-esp32); its exact upstream revision is recorded in the main [README](../README.md#c99-engine-source-and-attribution).
