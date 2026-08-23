# RESEARCH_NOTES.md — Phase 1 research (all findings sourced)

## R1. llama.cpp C API decode loop

**Primary source: vendored code itself** — `app/src/main/cpp/llama.cpp/examples/simple/simple.cpp` (matches the exact vendored API revision; no version drift risk).

Confirmed decode-loop pattern from that file:
1. `ggml_backend_load_all()` — load dynamic backends (CPU always available; Vulkan only if built).
2. `llama_model_params` → set `n_gpu_layers` (configurable, NOT hardcoded 99) → `llama_model_load_from_file()`.
3. `const llama_vocab* vocab = llama_model_get_vocab(model)`.
4. Two-pass tokenize: first call with `NULL` buffer returns negative count → allocate → call again:
   `llama_tokenize(vocab, text, len, tokens, n_max, add_special, parse_special)`.
5. Context: `llama_context_default_params()` → `n_ctx`, `n_batch`, `n_threads`, then `llama_init_from_model(model, ctx_params)`.
6. Prompt batch: `llama_batch_get_one(tokens.data(), n)`; loop:
   - `llama_decode(ctx, batch)` (non-zero = fail)
   - `new_token_id = llama_sampler_sample(smpl, ctx, -1)`
   - `llama_vocab_is_eog(vocab, new_token_id)` → break
   - `llama_token_to_piece(vocab, new_token_id, buf, sizeof buf, 0, true)` → append/stream
   - `batch = llama_batch_get_one(&new_token_id, 1)`
7. Sampler chain: `llama_sampler_chain_init(llama_sampler_chain_default_params())` +
   `llama_sampler_chain_add(smpl, llama_sampler_init_greedy())`. For chat quality use
   dist/top-p/temp chain (`llama_sampler_init_dist`, etc.) — same chain API.
8. Cleanup: `llama_free(ctx)`, `llama_free_model(model)` (already present in our NativeBridge unloadModel).

Note: current NativeBridge.cpp uses deprecated `llama_load_model_from_file`; vendored header marks it DEPRECATED in favor of `llama_model_load_from_file` (include/llama.h:441–449). M1 will migrate to the modern name while keeping the Kotlin signature unchanged (no JNI signature change).

Secondary reference (same vendored repo): `examples/llama.android/lib/src/main/cpp/ai_chat.cpp` — an Android JNI chat loop over this same API; useful template for M4 streaming.

## R2. Embedding / pooling API

**Primary source:** vendored `app/src/main/cpp/llama.cpp/examples/embedding/embedding.cpp`.

- Set pooling type via context params (`ctx_params.pooling_type`; enum `llama_pooling_type`: NONE/MEAN/CLS/LAST/RANK — include/llama.h:168–174).
- After `llama_decode`, read `llama_get_embeddings_seq(ctx, seq_id)` when pooling ≠ NONE (token-level via `llama_get_embeddings_ith` otherwise); normalize with `common_embd_normalize(embd, out, n_embd, norm)`.
- Requires `params.embedding = true` path equivalent (context must be created with embeddings enabled).
- **Key architectural caveat (confirmed):** a 0.5B chat model (Qwen2.5-0.5B) is not a trained text embedder; pooled vectors are weak for semantic retrieval. See D2 decision: ONNX MiniLM stays the embedding runtime.

## R3. Lazy grammar / JSON-schema tool calling (PR #9639)

Source: https://github.com/ggerganov/llama.cpp/pull/9639 (merged Jan 30, 2025; author ochafik).

Findings relevant to PhoneLM M3:
- **Lazy grammars**: output is unconstrained until a *trigger word/token* appears, after which a grammar forces schema-valid output. This solves "model must sometimes emit prose AND sometimes emit a tool call" — exactly our regex-replacement problem at ChatViewModel.kt:134.
- Trigger examples: Llama 3.x `{"name":` variants; Hermes/Qwen2.5 `<tool_call>`; Mistral `[TOOL_CALLS]`; DeepSeek `<｜tool▁calls▁begin｜>`. Generic fallback = plain JSON-schema constraint without lazy triggering.
- **Qwen 2.5 has native tool-call support** (Hermes-style templates) — validates choosing Qwen2.5-0.5B-Instruct as bundled v1 model: M3 can later reuse its native style.
- Implementation pieces upstream: minja Jinja engine (vendored here at `cpp/llama.cpp/vendor/minja/`), tool-call grammar generation, parser per style. For M3 we do NOT need llama-server; we can constrain generation directly with `llama_sampler_init_grammar` (lazy trigger semantics may need a small port or a simpler approach: two-phase sampling — free gen until trigger detected, then attach grammar).
- Streaming of tool calls landed later (#12379); not needed for M3 v1.

## R4. Vector store for ≤1k-chunk on-device RAG

Sources:
- https://github.com/asg017/sqlite-vec (README; pre-v1, pure C, brute-force vec0 tables)
- https://alexgarcia.xyz/sqlite-vec/android-ios.html (prebuilt .so since v0.1.2; .aar planned)
- https://docs.objectbox.io/on-device-vector-search (HNSW ANN, nearestNeighbor query, find-with-scores)
- https://mvpfactory.io/blog/on-device-rag-for-android-running-embedding-models-vector-search-in-sqlite-and/ (brute-force single-digit ms under ~50k chunks; mobile chunking guidance)

Conclusion (D3): for ≤1k chunks both are adequate. sqlite-vec adds a native dependency + JNI/NDK build integration for zero practical gain at our scale; ObjectBox HNSW is already wired (entity, plugin, query code all exist). **Keep ObjectBox for v1; document sqlite-vec as migration path** if corpus grows >10k chunks or we need SQL joins/metadata filters.

## R5. Model availability on Hugging Face (verified by fetching repo pages)

### LateMonk/PhoneLM_Models — EXISTS: https://huggingface.co/LateMonk/PhoneLM_Models
Actual files (tree/main, verified):
| File | Size |
|---|---|
| Qwen3-1.7B-Q4_K_M.gguf | 1.11 GB |
| Qwen3-VL-2B-Thinking-Q4_K_M.gguf | 1.11 GB |
| SmolLM2-1.7B-Instruct-Q4_K_M.gguf | 1.06 GB |
| all-MiniLM-L6-v2.onnx | 90.4 MB |
| tokenizer.json | 466 kB |
| mmproj-BF16.gguf | 823 MB |
Apache-2.0. README is empty. Tags: GGUF, ONNX, imatrix, conversational.

⚠ **`phonelm-1.5b-q4_k_m.gguf` does NOT exist** — ModelDownloader.kt's URL is a dead link. Repo also contains NO 0.5B-class model; smallest is 1.06–1.11 GB (too big to bundle for a 4GB-RAM-phone v1 target).

### LateMonk/ForestControl_Models — EXISTS: https://huggingface.co/LateMonk/ForestControl_Models
GGUF + ONNX tags, qwen2 arch, multiple Q4_K_M sizes (344 MB … 18.6 GB), no model card. Parked per supervisor instruction: becomes ModelDownloader's default manifest in M4+ when specified; not wired in M1.

### Bundled-model candidate (primary): Qwen/Qwen2.5-0.5B-Instruct-GGUF — EXISTS
https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF — Apache-2.0.
- `qwen2.5-0.5b-instruct-q4_k_m.gguf` = **491 MB** (larger than the ~400 MB estimate; noted).
- ChatML template; Qwen2.5 family has native llama.cpp tool-calling support (see R3) → future-proofs M3.
Fallback: SmolLM2-360M-Instruct Q4_K_M (~250 MB) if 491 MB blocks device install/emulator push.
