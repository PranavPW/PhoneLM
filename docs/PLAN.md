# PLAN.md — PhoneLM v2 milestones

v1 scope = **M1 + M2**. M3–M5 are documented, not built.
Companion docs: CODEBASE_MAP.md, RESEARCH_NOTES.md, DECISIONS.md, TEST_LOG.md,
PROGRESS.md, BLOCKERS.md, MANUAL_VERIFY.md.

---

## M1 — Basic working (real engine, buildable app)

**Goal:** `gradlew assembleDebug` passes; bundled GGUF produces real completions on
CPU; embeddings JNI no longer lies (deterministic or removed from RAG path).

Steps:
1. Pin wrapper: `gradle-wrapper.properties` → gradle-8.6-bin.zip (D1). Evidence: `gradlew --version`.
2. Create minimal `app/proguard-rules.pro`; keep release non-minified for now (`isMinifyEnabled=false` already).
3. Fix ChatViewModel conflicting imports (drop `com.phonelm.rag.VectorStore`); fix duplicate `latch.await()` in DocumentProcessor.
4. Delete unused `rag/VectorStore.kt` (D3).
5. NativeBridge.cpp real decode loop per RESEARCH_NOTES R1:
   - migrate to `llama_model_load_from_file`; add `n_gpu_layers` parameter to loadModel
     (Kotlin overload with default 0 = CPU; D6).
   - implement tokenize → batch → decode → greedy/dist sampler chain → token_to_piece loop;
     EOG stop; max-token cap; return full string (D5 blocking model).
6. `getEmbeddings`: remove the fake ramp vector. M1 interim: return NULL unless a
   pooling-capable context is explicitly requested; it is NOT called by any RAG code
   after step 4/7 wiring changes. Full decision revisited in M2 (D2).
7. Bundled-model mechanism (D4): `scripts/fetch_model.ps1` → assets/models/*.gguf;
   .gitignore entry; HomeScreen picks asset if present else shows "no model loaded" state.
8. Tests:
   - JVM unit tests: prompt assembly (context block formatting), model-path resolution
     (asset-present vs absent logic extracted into pure function).
   - Instrumented test TEMPLATE (user runs): load bundled GGUF, send prompt, assert
     non-placeholder output ≠ "Thinking... (Native inference placeholder".
9. MANUAL_VERIFY.md checklist: install, CPU-only load of Qwen2.5-0.5B Q4_K_M, first
   completion latency sanity (<60 s on emulator acceptable), no-crash unload.

**Definition of Done:** assembleDebug succeeds WITHOUT gguf present AND with it present;
unit tests green; instrumented smoke template committed; MANUAL_VERIFY written.
**Evidence:** TEST_LOG.md entries with command tails.
**Risks:** CMake/Vulkan toolchain failure in CI-less env → fallback: disable Vulkan
compile entirely for M1 (GGML_USE_VULKAN=OFF) since default is CPU anyway (STOP-AND-ASK
before removing vendored Vulkan sources — we only toggle the flag).
**OUT:** streaming UI, chat template/Jinja, agent grammar, RAG retrieval quality.

## M2 — Real RAG

**Goal:** import PDF → chunked → REAL 384-dim MiniLM embeddings → ObjectBox HNSW →
retrieval injected into prompt → answer cites source file/page.

Steps:
1. Real tokenizer for MiniLM inside EmbeddingGenerator: parse tokenizer.json (vocab +
   merges/wordpiece), produce input_ids/attention_mask/token_type_ids.
2. Real ONNX inference: run session, mean-pool last hidden state → L2-normalized 384-dim.
   Kill all random/dummy paths.
3. Sentence-boundary chunker (~256 tokens, ~32 overlap) as pure Kotlin — unit tested.
4. Single shared EmbeddingGenerator instance via ViewModel (kill DocumentProcessor's private one).
5. Retrieval in ChatViewModel already scaffolded — verify with real vectors; citations
   appended ("[source: file.pdf p.3]").
6. Tests: chunker edge cases; cosine ranking order with synthetic vectors; tokenizer
   round-trip known pairs ([CLS]/[SEP]); embedding gate (below).
7. **Embedding test gate (permanent anti-random regression):** instrumented test asserting
   same-input → identical vector byte-for-byte, dim == 384, L2 norm ≈ 1.

**DoD:** user-run manual check: import a small PDF, ask about its content, answer
references it with citation. Unit tests green.
**Risks:** WordPiece-in-Kotlin is the most likely failure point → fallback: use the
ONNX model's built-in tokenizer if exported with one, or ship precomputed HF tokenizer
ids for the fixed test fixture and accept greedy whitespace+vocab fallback ONLY if
quality is demonstrably acceptable (recorded honestly in BLOCKERS.md otherwise).
**OUT:** OCR pipeline fixes (parked), reranking, >10k-chunk optimization.

## M3 — Agent loop (documented design only in v1)

Grammar/JSON-schema constrained intents replacing regex at ChatViewModel.kt:134.
Design per RESEARCH_NOTES R3: two-phase sampling (free generation until trigger string
e.g. `<tool_call>` detected in emitted text, then attach `llama_sampler_init_grammar`
with the action schema `{action: enum[click,scroll], target_text: string}`).
AccessibilityService actions gated behind confirm-before-act dialog.
Qwen2.5 native tool-call style is the reference format.

## M4 — Model UX (documented only)

Token-by-token streaming (JNI callback design vs shared-ring-buffer — decide then),
stop-generation cancellation, model manager UI, ForestControl-driven ModelDownloader.

## M5 — Quality (documented only)

Benchmarks (tokens/s by quant/device), battery guardrails, release signing, AGP/Gradle
modernization (own STOP-AND-ASK plan), minification with real proguard rules.

---

# Self-Review (Phase 2 critique — cuts applied)

## 1. Over-scoped for a 4GB-RAM phone?
- Bundled 491 MB Qwen2.5-0.5B: RAM footprint of a Q4_K_M 0.5B ≈ 0.5–0.7 GB — OK.
  But APK bloat risk → **CUT:** model stays OUT of git and OUT of default debug builds
  until fetched by script; document SmolLM2-360M (~250 MB) as swap if emulator install fails.
- M2 chunking at 256 tokens w/ overlap over whole PDFs: fine; **CUT:** parallel
  embedding of chunks — sequential only, batches of 1, to bound ONNX memory.
- **CUT from M1:** n_gpu_layers>0 as a user-visible setting (UI). It stays a parameter;
  settings UI is M4.

## 2. Most likely step to fail + fallback?
- **Real WordPiece tokenizer in Kotlin (M2 step 1)** — highest complexity/risk.
  Fallback chain: (a) simplified greedy longest-match vocab tokenizer (no merges) —
  measure quality hit honestly; (b) pre-tokenize on host and cache ids per document
  (defeats arbitrary-text RAG, unacceptable long-term — would become BLOCKER);
  (c) escalate blocker rather than silently shipping garbage embeddings.
- Second: NDK/CMake Vulkan build failures (M1). Fallback: GGML_USE_VULKAN=OFF toggle.

## 3. Unverifiable without a device → MANUAL_VERIFY.md items
- Actual decode correctness/speed, Vulkan-vs-CPU behavior, model load times, PDF import
  end-to-end, accessibility actions, embedding determinism on-device, memory pressure.
  All converted to numbered MANUAL_VERIFY checklist items; JVM tests cover only
  chunker/prompt-assembly/path-resolution/tokenizer-pure-logic/retrieval-ranking.

## 4. New dependencies?
- None added. Everything used is already declared (pdfbox, MLKit, ONNX, ObjectBox,
  Compose). The bundled GGUF is an asset fetched by script, not a dependency.
  sqlite-vec explicitly NOT added (D3).

## Revised-plan deltas applied
M1: dropped GPU settings UI; model-not-committed enforced; getEmbeddings de-fanged early.
M2: sequential embedding; explicit tokenizer fallback chain; embedding determinism gate.
All device-dependent verification moved out of DoD into MANUAL_VERIFY.md.
