# Known limitations

## Tool retrieval is not implemented

The independent C99 engine does not implement Needle 2's contrastive tool-retrieval head. The official runtime retrieves the five most relevant schemas when a catalog contains more than five tools; this port instead places every schema directly in the prompt.

Needle 2 has a 256-token sliding attention window. Tool schemas are pinned as KV sinks, but a large schema catalog can consume most or all usable context before the request is added. In practice, this implementation should be treated as supporting roughly two or three detailed tools at once.

Agave currently bundles three compact schemas: `set_led`, `get_weather`, and `get_time`. Before adding a broader Home Assistant catalog, evaluate one of these approaches:

1. Port the contrastive retrieval head to the C99 engine.
2. Perform a separate local schema-retrieval pass and expose only a small selected subset to Needle.
3. Adopt an official runtime API if Cactus exposes both Needle-compatible loading and token callbacks.

This limitation must not be hidden by silently truncating the catalog: if the correct schema is absent from the active subset, grammar-constrained decoding makes that tool impossible to call.

## CPU backend

Inference uses portable C99 kernels and a small Android CPU thread pool. It does not use QNN, NNAPI, Hexagon, Vulkan, or GPU acceleration. The first build prioritizes correctness and instrumentation; ARM NEON optimization remains future work.

## Fixed tools and no execution

The schemas are bundled and cannot be edited in the app. Agave displays generated calls and provides an LED preview for `set_led`, but intentionally executes none of the tools yet.

## Session semantics

Every request rewinds to the same primed schema prefix. Interaction records persist, but prior interactions are not fed back into the model as conversational context.

## Model lifecycle

The complete `.cact` asset is copied into native RAM at startup and retained for the process lifetime. There is no in-app model unload or model switching.
