# Known limitations

## BM25 substitutes for the missing retrieval head

The independent C99 engine does not implement Needle 2's contrastive tool-retrieval head. Agave instead uses a visible two-stage experiment: Needle first calls `find_tool`, an in-memory BM25 index ranks skills from the generated keywords, and Needle runs again with a candidate subset.

This workaround has important limitations:

- BM25 is lexical and has no semantic vector representation.
- Retrieval quality depends on Needle's generated keywords and manifest tags/examples.
- Candidate schemas are packed under a 210-token prefix budget; a relevant schema may be excluded.
- Recompiling, re-priming candidates, and restoring `find_tool` adds substantial latency.
- The result measures a combined keyword-generation/BM25/selection pipeline, not the unavailable contrastive head.

The full routing trace is displayed and persisted so retrieval recall and conditional tool selection can be evaluated separately. A future vector or hybrid retriever can implement the same retrieval interface without changing the two-stage flow.

## CPU backend

Inference uses portable C99 kernels and a small Android CPU thread pool. It does not use QNN, NNAPI, Hexagon, Vulkan, or GPU acceleration. The first build prioritizes correctness and instrumentation; ARM NEON optimization remains future work.

## Fixed tools and partial execution

Bundled skill definitions are read-only APK assets. The catalog supports private-storage overrides, but Agave does not yet provide an on-device skill editor or live schema reload. `get_time`, `set_brightness`, and `set_volume` execute locally. The other 33 retrievable skills are selection experiments only and return an explicit non-execution result.

Time lookup supports `here`, selected common city aliases, and valid IANA time-zone IDs. It is not a general geocoder. Brightness affects only the Agave window and resets when that window is recreated. Volume controls Android's device media stream and is quantized to the hardware volume steps.

## Session semantics

The waiting state always uses the primed `find_tool` prefix. Candidate selection uses a temporary candidate-specific prefix, then restores `find_tool`. Interaction records persist, but prior interactions are not fed back into either inference pass as conversational context.

## Model lifecycle

The complete `.cact` asset is copied into native RAM at startup and retained for the process lifetime. There is no in-app model unload or model switching.
