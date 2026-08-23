# DECISIONS.md

Each entry: decision, alternatives considered, rationale.

## D1 — Pin Gradle wrapper to 8.6; do NOT touch AGP/Kotlin/Compose (approved by supervisor)
- **Decision:** `distributionUrl=gradle-8.6-bin.zip`; AGP stays 8.2.0, Kotlin 1.9.20, Compose compiler ext 1.5.4. Fallback if incompatible: gradle-8.4.
- **Alternatives:** (a) upgrade AGP to latest → chains Kotlin + Compose-compiler + JDK bumps into an already-broken build; rejected for M1 risk. (b) keep Gradle 9 milestone → AGP 8.2 does not support it; build cannot pass.
- **Why:** smallest change that makes `assembleDebug` possible; version modernization deferred to M5 with its own STOP-AND-ASK gate.

## D2 — ONE embedding runtime: ONNX MiniLM (all-MiniLM-L6-v2). llama.cpp pooling is NOT used for RAG.
- **Decision:** Real embeddings come from the ONNX MiniLM path (384-dim). The chat GGUF's pooled vectors are not used for retrieval. JNI `getEmbeddings` keeps its signature but M2 will either delete it or repoint it — decided at M2 planning; no RAG code may call it.
- **Alternatives:** use llama.cpp MEAN pooling over the chat model → rejected: a 0.5B chat model is not a trained embedder; retrieval quality would be poor and it couples chat-model choice to RAG quality (RESEARCH_NOTES R2).
- **Consequence:** EmbeddingGenerator must be made REAL in M2: real WordPiece tokenizer (tokenizer.json already on LateMonk repo, 466 kB) + real ONNX session I/O (input_ids, attention_mask, token_type_ids), mean-pool last hidden state.

## D3 — ONE vector store: ObjectBox HNSW. rag/VectorStore.kt deleted. sqlite-vec documented as future alternative.
- **Decision:** Keep `data/VectorStore.kt` (ObjectBox, nearestNeighbor query). Delete `rag/VectorStore.kt` (unused in-memory duplicate that also collides on import with ChatViewModel).
- **Alternatives:** migrate to sqlite-vec now → adds native dependency + NDK glue for zero gain at ≤1k chunks (brute force is single-digit ms at this scale per RESEARCH_NOTES R4); revisit only above ~10k chunks.
- **Dimension (D3b):** ONE fixed dimension end-to-end = **384** (MiniLM output ↔ VectorEntity HnswIndex dimensions=384 ↔ any producer). The 1024-dim fake in NativeBridge.cpp dies with the fake implementation.

## D4 — Bundled model mechanism: scripted copy, gitignored asset
- **Decision:** `scripts/fetch_model.ps1` downloads `qwen2.5-0.5b-instruct-q4_k_m.gguf` (491 MB, verified size — larger than initial ~400 MB estimate) from https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF into `app/src/main/assets/models/`; path gitignored; `assembleDebug` MUST succeed without the file present; runtime shows clean "no model loaded" state when missing.
- **Alternatives:** (a) commit GGUF to git → rejected by supervisor (repo bloat). (b) Gradle download task → adds network execution to every build; script is explicit and cacheable. (c) SmolLM2-360M (~250 MB) fallback only if emulator install of 491 MB fails.
- **APK note:** bundling puts the model inside the APK (>5MB rule) — supervisor pre-approved this specific addition; STOP-AND-ASK applies to anything beyond this file.

## D5 — M1 generation is blocking-on-background-thread; no streaming infra
- **Decision:** `generateCompletion` runs the full loop synchronously on Dispatchers.IO; UI gets one final string (as today). True token streaming (JNI callback vs shared buffer) is an M4 design task.
- **Why:** supervisor directive; avoids designing callback lifetime/cancellation twice.

## D6 — CPU fallback via configurable n_gpu_layers
- **Decision:** `loadModel(path)` overload gains `n_gpu_layers: Int` param defaulting to 0 (CPU) for M1... **revised during self-review (see PLAN.md):** since Vulkan remains compiled in but emulators/devices may lack usable Vulkan compute, default = 0 (CPU-only) with an explicit setting later; n_gpu_layers=99 remains available for real devices where Vulkan works.
- **STOP-AND-ASK scope:** none — no JNI signature *removal*; adding a defaulted parameter is additive and was pre-declared here.

## D7 — ModelDownloader parked
- **Decision:** No changes to ModelDownloader.kt in v1. Its URLs point at files that do not exist (`phonelm-1.5b-q4_k_m.gguf` verified absent from LateMonk/PhoneLM_Models). It becomes ForestControl-manifest-driven in M4+.

## D8 — No new dependencies in v1
- **Decision:** Zero new Gradle/Maven/native dependencies across M1+M2. Everything used is already declared in app/build.gradle.kts (pdfbox-android, ML Kit, ONNX Runtime, ObjectBox, Compose). The bundled GGUF is a gitignored asset fetched by script (D4), not a dependency. sqlite-vec is explicitly NOT added per D3; grammar tool calling (M3) uses llama.cpp's own sampler API already vendored.
- **Cross-ref:** PLAN.md "Self-Review" §4 ("New dependencies? None added"); D3 (sqlite-vec rejected); D4 (model-as-asset).
- **Why:** each new dependency adds build risk to an already-fragile build and violates the free-tier minimal-diff discipline.

## D9 — Git baseline: init repo at root; vendored llama.cpp ignored (supervisor-directed)
- **Decision:** `git init` at repo root. `.gitignore` covers: `app/src/main/assets/models/` (bundled GGUF never committed, per D4), `app/src/main/cpp/llama.cpp/` (vendored tree ignored for now — NOT converted to a submodule in this step), plus standard Android/Gradle ignores (`.gradle/`, `build/`, `local.properties`, `.cxx/`, `.idea/`). One baseline commit of current state follows immediately.
- **Alternatives:** (a) leave un-versioned → minimal-change proof and the no-GGUF-in-git gate are unmeasurable; rejected by supervisor. (b) git submodule for llama.cpp → cleaner provenance but rewrites the vendored layout mid-M1; deferred.
- **Provenance (because the tree is ignored):** vendored llama.cpp upstream commit:
  `bd2a93d4753c4f00443f561ee039220283016ee8` ("gguf-py : add requests to dependencies (#18629)", 2026-01-06).
  All RESEARCH_NOTES R1–R2 API references were verified against exactly this revision.

## D10 — gradle.properties: android.useAndroidX=true only (no jetifier, no extra flags)
- **Decision:** new root `gradle.properties` with `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, and a plain `-Xmx2048m` JVM arg. No `android.enableJetifier` (project has no legacy support-library dependencies to migrate).
- **Alternatives:** (a) do nothing → build permanently fails at checkDebugAarMetadata (baseline evidence TEST_LOG "M1 Step 1" T2). (b) add jetifier → wasted build-time transform, nothing to jetify.
- **Evidence chain:** baseline failure verbatim in docs/TEST_LOG.md §"M1 Step 1" T2.
