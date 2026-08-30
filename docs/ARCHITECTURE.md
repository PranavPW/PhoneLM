# ARCHITECTURE.md — PhoneLM v2 deep dive

This is the companion to the bird's-eye view in [README.md](../README.md) and the
evidence trail in [docs/CODEBASE_MAP.md](CODEBASE_MAP.md) /
[docs/RESEARCH_NOTES.md](RESEARCH_NOTES.md) / [docs/DECISIONS.md](DECISIONS.md).

## 1. NativeBridge.cpp — the JNI bridge

### What it used to do

Before M1 it was a stub. `generateCompletion` returned

```
Thinking... (Native inference placeholder for: <prompt>)
```

and `getEmbeddings` returned a deterministic ramp vector of dim 1024. Both are
inventoried in [CODEBASE_MAP.md §3](CODEBASE_MAP.md) and killed (or parked)
during M1.

### What it does now

`app/src/main/cpp/NativeBridge.cpp` owns two globals (`g_model`, `g_ctx`) plus a
cached `g_vocab` pointer. It exposes three JNI symbols looked up by name from
`core/LlamaEngine.kt` (renaming them breaks `System.loadLibrary("phonelm")`; see
the keep-rules note in [app/proguard-rules.pro](../app/proguard-rules.pro) and
[DECISIONS.md D10](DECISIONS.md)).

#### `loadModel(path)` / `loadModelWithGpuLayers(path, n_gpu_layers)`

Migration killed the two deprecated calls. Before:

```cpp
llama_model_params mp = llama_model_default_params();
mp.n_gpu_layers = 99; // forced Vulkan
g_model = llama_load_model_from_file(path, mp);           // deprecated
g_ctx   = llama_new_context_with_model(g_model, cp);       // deprecated
```

After (Step 5):

```cpp
llama_model_params mp = llama_model_default_params();
mp.n_gpu_layers = n_gpu_layers; // 0 = CPU-only fallback (D6)
g_model = llama_model_load_from_file(path, mp);            // modern
g_vocab = llama_model_get_vocab(g_model);                  // cached once
llama_context_params cp = llama_context_default_params();
cp.n_ctx = 2048; cp.n_threads = 4;
g_ctx = llama_init_from_model(g_model, cp);                 // modern
```

`LlamaEngine.kt` keeps the original `external fun loadModel(path): Boolean` and
adds an **additive** overload `loadModelWithGpuLayers(path, nGpuLayers)`. The
Kotlin wrapper `loadModelSafe(path, nGpuLayers = 0)` defaults to CPU so emulators
(which typically lack usable Vulkan compute) work without configuration. The JNI
side forwards the single-arg entry point to the two-arg one with `0`.

No JNI signature was *removed* — the rule in [DECISIONS.md D6](DECISIONS.md) was
honored and the build proves it (`buildCMakeDebug[arm64-v8a]` green).

#### `generateCompletion(prompt)` — the decode loop

Ported from vendored `examples/simple/simple.cpp` (see [PROGRESS.md — Step 5 Decode Plan](PROGRESS.md) and [RESEARCH_NOTES.md R1](RESEARCH_NOTES.md)). Five steps:

1. **KV hygiene.** `llama_memory_clear(llama_get_memory(g_ctx), true)` so
   consecutive calls do not inherit stale positions. (Pattern proven in vendored
   `examples/embedding/embedding.cpp:batch_decode`.)

2. **Two-pass tokenize.** First call with a null buffer returns `-n_tokens`; the
   bridge allocates `vector<llama_token>(n)` and calls again. Flags: `add_special = true`,
   `parse_special = true`, matching `simple.cpp:77–86`.

3. **Sampler chain (greedy, M1).** `llama_sampler_chain_init(default_params())` +
   `llama_sampler_chain_add(..., llama_sampler_init_greedy())`. Greedy is
   deterministic (same prompt ⇒ same output ⇒ testable). A temperature/top-p chain
   is deferred to M4. Source: `simple.cpp:118–125`.

4. **Decode loop.**
   ```cpp
   llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
   for (int n_pos = 0; n_pos + batch.n_tokens < n_prompt + kMaxNewTokens; ) {
       llama_decode(g_ctx, batch);                 // non-zero = fail
       n_pos += batch.n_tokens;
       llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
       if (llama_vocab_is_eog(g_vocab, id)) break; // EOG stop
       char buf[128];
       int n = llama_token_to_piece(g_vocab, id, buf, sizeof(buf), 0, true);
       result.append(buf, n);
       batch = llama_batch_get_one(&id, 1);
   }
   ```
   Cap: `kMaxNewTokens = 256`. Each iteration decodes the current batch, samples
   one token, converts it to a UTF-8 piece, and rebinds the batch to that single
   token (the `simple.cpp:148–191` pattern).

5. **Return.** `NewStringUTF(result.c_str())` — blocking, synchronous (D5). No
   streaming callback infrastructure was built in M1; that is an M4 design task.

#### `getEmbeddings(text)` — parked

Per [DECISIONS.md D2](DECISIONS.md), real embeddings are **ONNX MiniLM (384-dim)**,
not llama.cpp pooling over the chat model. The JNI method now returns a
deterministic zero vector and is not called by any RAG path. It will be revisited
in M2 planning (delete or repoint). The anti-fake guarantee for embeddings is the
instrumented determinism gate planned for M2, not a JNI test.

### Build notes

- `app/src/main/cpp/CMakeLists.txt` adds `llama.cpp` via `add_subdirectory` and
  links `phonelm` against `llama`, `log`, `android`. Vulkan remains compiled in
  (`-DGGML_USE_VULKAN=ON`) but is inert when `n_gpu_layers=0`.
- The NDK path was the single biggest de-risk of M1: `buildCMakeDebug[arm64-v8a]`
  was proven green in [TEST_LOG.md §M1 Step 3](TEST_LOG.md) and again after the
  rewrite in §M1 Step 5.

## 2. D4 — Bundled model mechanism

The model is an **asset, not a dependency**.

```
scripts/fetch_model.ps1  ──download/copy──▶  app/src/main/assets/models/
                                                    │
.gitignore blocks it ────────────────────────────────┘
                                                    │
HomeScreen LaunchedEffect ──copy once──▶  filesDir/models/
                                                    │
                                          LlamaEngine.loadModel(path)
```

- Script: [scripts/fetch_model.ps1](../scripts/fetch_model.ps1). Downloads
  `qwen2.5-0.5b-instruct-q4_k_m.gguf` (468.6 MB, verified size larger than the early
  ~400 MB estimate) from `https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF`,
  or copies a local file via `-SourcePath`. Idempotent; size sanity checks.
- `.gitignore` entry: `app/src/main/assets/models/` (D4). `assembleDebug` must
  succeed without the file; the app shows a clean "no model loaded" state then
  (see [MANUAL_VERIFY.md](MANUAL_VERIFY.md)).
- Runtime copy: `HomeScreen`'s `LaunchedEffect` copies bundled assets into
  `filesDir/models/` on first launch (so the engine, which needs a real file
  path, can load them offline). Subsequent launches skip the copy. Resolution
  order (implemented in `core/ModelLocator.kt`): bundled copy > Downloads
  fallback, largest valid `.gguf` wins. APK size delta ≈ model size (507 MB
  total vs ~38 MB base, [TEST_LOG.md §M1 Step 5](TEST_LOG.md)).

## 3. D9 — Vendored llama.cpp gitignore strategy

The entire `app/src/main/cpp/llama.cpp/` tree is **ignored, not submoduled**.

```
.gitignore:  app/src/main/cpp/llama.cpp/
```

Why: adding a submodule mid-M1 would rewrite the vendored layout while the build
was still being unblocked, and submodules add clone-time friction for reviewers.
The tree stays as a plain directory populated by the developer. Provenance is
recorded textually instead:

> `bd2a93d4753c4f00443f561ee039220283016ee8` — "gguf-py : add requests to dependencies (#18629)", 2026-01-06.

All API references in [RESEARCH_NOTES.md R1–R2](RESEARCH_NOTES.md) were verified
against exactly this revision. A future step may promote this to a submodule once
M1's build is stable and the supervisor approves the rewrite.

## 4. RAG pipeline (M2 target)

```
PDFBox (text) ─┐
               ├─▶  Chunker (pure Kotlin, paragraph-aware, hard-wrap)
MLKit OCR ─────┘          │
                          ▼
                 ONNX Runtime — all-MiniLM-L6-v2.onnx (384-dim, deterministic)
                          │
                          ▼
                 ObjectBox HNSW  (VectorEntity @HnswIndex(dimensions=384))
                          │
ChatViewModel ◀── nearestNeighbors(queryEmbedding, topK) ──┘
     │  PromptBuilder.buildRagPrompt(question, contexts)
     ▼
LlamaEngine.generateCompletion(finalPrompt)
```

- **Embedding runtime:** ONNX MiniLM, not llama pooling (D2). D3 fixes the
  dimension end-to-end at 384. sqlite-vec was evaluated and rejected for v1
  (adequate only above ~10k chunks; see [RESEARCH_NOTES.md R4](RESEARCH_NOTES.md)).
- **Pure pieces already shipped in M1:** `rag/Chunker.kt`, `rag/PromptBuilder.kt`,
  `core/ModelLocator.kt` — JVM-tested (19 tests), wired into
  `DocumentProcessor` / `ChatViewModel` / `HomeScreen` respectively. Real
  tokenizer + ONNX inference remain for M2.
