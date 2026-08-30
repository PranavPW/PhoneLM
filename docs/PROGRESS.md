# PROGRESS.md

## Phase 0–2 (planning) — 2026-08-23
**WHAT WORKS:** Full codebase audit complete (CODEBASE_MAP.md); research with sources
complete (RESEARCH_NOTES.md); decisions recorded (DECISIONS.md); plan written and
self-critiqued (PLAN.md).
**WHAT IS STILL FAKE:** Everything — no code changed yet. generateCompletion,
getEmbeddings, EmbeddingGenerator all fake per CODEBASE_MAP §3.
**EVIDENCE:** None yet; first build log lands in TEST_LOG.md at M1 step 1.
**NEXT ACTIONS:** Await plan approval → M1 step 1 (wrapper pin + assembleDebug evidence).
**RISKS:** CMake/Vulkan toolchain on Windows NDK build (fallback: GGML_USE_VULKAN=OFF);
Kotlin WordPiece complexity in M2 (fallback chain in PLAN.md self-review §2).

## Cuts applied (Phase 2 self-review)
- No GPU-settings UI in M1 — n_gpu_layers stays an internal parameter, settings UI
  deferred to M4 (PLAN.md Self-Review §1).
- Sequential-only embeddings in M2 — no parallel ONNX chunk inference, batches of 1,
  to bound memory on low-RAM devices (PLAN.md Self-Review §1).
- Device-dependent checks moved out of DoD into MANUAL_VERIFY.md checklist items —
  decode speed, on-device embedding determinism, PDF import E2E, etc.
  (PLAN.md Self-Review §3).

Blocker tracking lives in docs/BLOCKERS.md (currently: none open; two parked items).

## M1 Step 1 — wrapper pin + baseline build (2026-08-23)
**DONE:** Wrapper pinned gradle-9.0-milestone-1 → 8.6-bin.zip; `gradlew --version`
confirms Gradle 8.6 / JVM 17 / Kotlin 1.9.20. Baseline `assembleDebug` captured.
**EVIDENCE:** TEST_LOG.md "M1 Step 1": T1 verbatim output; T2 failure tail + full log
at %TEMP%\opencode\assemble_baseline.log.
**BASELINE FAILURE ROOT CAUSE:** no root gradle.properties → `android.useAndroidX`
not enabled → fails at :app:checkDebugAarMetadata, BEFORE any Kotlin/NDK compilation.
The predicted Kotlin compile errors are still unverified — they will surface only
after step 2 adds gradle.properties.
**WEAKEST PART:** repo has no .gitignore and is not a git repository — the
"git status as proof" gate is unmeetable as stated; minimal-change proof is
procedural instead. Also NDK/CMake path still completely unexercised.
**COULD BREAK ELSEWHERE:** adding gradle.properties may change dependency resolution;
watch for follow-on metadata/compile failures in step 2.

## M1 Step 2 — gradle.properties + git baseline (2026-08-23)
**DONE:** Root gradle.properties created (useAndroidX, no jetifier); git initialized,
.gitignore written (GGUF assets + vendored llama.cpp ignored), baseline commit
388b729 (35 files); assembleDebug re-run.
**EVIDENCE:** TEST_LOG.md §"M1 Step 2": T1 file content; T2 commit hash + clean tree +
.gitignore verbatim; T3 failure tail + repo1.maven.org 404-vs-exists proof; T4 vendored
llama.cpp hash bd2a93d4 recorded in DECISIONS.md D9.
**KEY FINDING:** AndroidX gate PASSED; new error layer = dependency coordinate typo
`com.tom_roush` → should be `com.tom-roush` (verified on Maven Central).
**WEAKEST PART:** res/values and res/drawable are EMPTY while the manifest references
themes/mipmaps/backup-rules XML — a guaranteed resource-link failure hiding behind the
current dependency failure. Also NDK/CMake still never exercised.
**COULD BREAK ELSEWHERE:** fixing the pdfbox typo will surface BOTH the Kotlin compile
errors AND likely the missing-resource errors simultaneously; step 3 must be prepared
to triage both layers honestly.
**TIMING:** failed-config cycle 27s → ~3.4s after enabling Gradle build cache +
parallel execution (user-directed optimization, measured twice).

## M1 Step 3 — typo/import/latch/proguard fixes (2026-08-23)
**DONE:** All three in-scope fixes applied exactly as scoped; proguard-rules.pro
created with policy header; assembleDebug re-run.
**EVIDENCE:** TEST_LOG.md §"M1 Step 3" (T1 grep, T2 diffs, T3 file, T4 verbatim
failure + layer progression, T5 porcelain).
**KEY FINDINGS:** (1) Dependency resolution now passes. (2) **NDK/CMake native build
works on this machine** — buildCMakeDebug[arm64-v8a] completed with only expected
llama.cpp deprecation notes; the scariest unknown is now de-risked.
(3) Build fails at processDebugResources: six AAPT errors, all missing-resource
files (themes/mipmaps/backup XML) — inventoried precisely for step 4.
**WEAKEST PART:** Kotlin compile has STILL never run — it sits behind resource
linking. The predicted ChatViewModel-class compile errors remain unverified until
step 4 creates the missing resources.
**COULD BREAK ELSEWHERE:** creating res/values/themes.xml must match the
theme attributes PhoneLMTheme.kt and manifest expect; wrong parent theme could
crash at runtime (device-verify item). Mipmap creation needs valid adaptive-icon
XML or AAPT fails again.

## M1 Step 4 — missing resources + MANUAL_VERIFY.md (2026-08-23)
**DONE:** Step 4.0 audit gap G-PLM-2 closed (docs/MANUAL_VERIFY.md written from the
approved consolidated testing guide). All 7 resource files created exactly as
planned: themes.xml, backup_rules.xml, data_extraction_rules.xml, adaptive-icon
pair (mipmap-anydpi-v26), foreground+background vector drawables. Zero code changes.
**EVIDENCE:** TEST_LOG.md §"M1 Step 4" — T1 all file contents verbatim; T2 build
failure tail; T3 porcelain scope proof.
**RESULT:** The six known AAPT errors CLEARED. One newly exposed pre-existing gap:
no res/values/strings.xml exists but accessibility_service_config.xml references
@string/app_name → still failing at processDebugResources (8s cycle).
**WEAKEST PART:** Kotlin compile has now been blocked by THREE successive resource
layers and has never run once — the entire app source remains compile-unverified.
Each cleared layer keeps exposing one more pre-existing gap; expect possibly more.
**COULD BREAK ELSEWHERE:** strings.xml is trivial risk; the real unknown is the
first javac/Kotlin pass (import conflicts already known; others unknown).

## M1 Step 5 — Kotlin unblock + real decode loop + fetch mechanism (2026-08-23)
**DONE:** strings.xml unblock → first Kotlin compile ever → 4 trivial errors fixed
in-scope (mid-file imports, missing clickable import, nearestNeighbor→nearestNeighbors
plural per ObjectBox 4.1.0 javap proof). Real decode loop implemented in
NativeBridge.cpp per Decode Plan. fetch_model.ps1 created and executed live —
Qwen2.5-0.5B Q4_K_M (468.6 MB) in assets/models/, gitignored.
**EVIDENCE:** TEST_LOG.md §"M1 Step 5". Headlines: BUILD SUCCESSFUL 1m16s (clean),
BUILD SUCCESSFUL 13s (decode loop), BUILD SUCCESSFUL 1m32s (with GGUF), APK 507 MB.
**WHAT IS NOW REAL:** loadModel (modern API, CPU default), generateCompletion
(tokenize→greedy sample→decode loop→detokenize, EOG stop, 256-token cap,
per-call KV clear). The placeholder string is GONE from the generation path.
**STILL FAKE:** JNI getEmbeddings returns deterministic zeros (parked by D2;
unused by RAG path); Kotlin-side generate() still single-emit (D5, M4 scope).
**WEAKEST PART:** NOTHING on-device has run — the decode loop compiled but was
never executed; greedy sampling quality with ChatML-less raw prompting is unknown;
no chat template applied yet (raw prompt in, raw completion out).
**COULD BREAK ELSEWHERE:** runtime crash risk in JNI (buffer sizes, EOG handling)
only surfaces via MANUAL_VERIFY B-section emulator tests; second-generation calls
depend on llama_memory_clear correctness; ObjectBox schema dir now gitignored —
if teammates later need schema versioning, revisit (documented in TEST_LOG).

## M1 Final Gates — JVM tests + instrumented template (2026-08-23)
**DONE:** 19 JVM unit tests green (Chunker/PromptBuilder/ModelLocator); instrumented
JNI smoke-test template compiles and is ready for the user's emulator run.
**EVIDENCE:** TEST_LOG.md §"M1 Final Gates": T1 testDebugUnitTest BUILD SUCCESSFUL;
T2 assembleDebugAndroidTest BUILD SUCCESSFUL; T3 porcelain scope proof.
**EXTRACTION FOR TESTABILITY:** PromptBuilder, Chunker, ModelLocator extracted as
pure Kotlin and wired into ChatViewModel/DocumentProcessor/HomeScreen respectively.
HomeScreen now completes the D4 mechanism: copies bundled asset GGUFs to filesDir
on first launch, resolves bundled>Downloads deterministically. Test-only deps added
(junit + androidx.test) with D8 amendment logged.
**HONEST TEST-FAILURE NOTE:** first test run had 1 failure caused by a bad
assertion I wrote (expected lossless rejoin of hard-wrapped chunks); fixed the
test, not the code — recorded here per no-fake-success rule.
**WEAKEST PART:** everything remains device-unverified: the smoke test has never
actually RUN on an emulator; greedy decoding quality without a chat template is
expected to be mediocre; EmbeddingGenerator is still random floats (M2 target).
**COULD BREAK ELSEWHERE:** HomeScreen asset-copy runs on every launch (guarded by
existence check, but a truncated copy from a killed process would persist —
length==0 guard only catches fully empty files); revisit if users report stuck
loads.

## Step 5 Decode Plan (Phase 5.2 research checkpoint)

Re-read RESEARCH_NOTES.md R1 and vendored examples/simple/simple.cpp. The exact
sequence being ported into NativeBridge.cpp:

1. **Load (modern API):** `llama_model_default_params()` → set `n_gpu_layers`
   (0 = CPU-only default, D6) → `llama_model_load_from_file()` (replaces deprecated
   `llama_load_model_from_file`); vocab obtained once via
   `llama_model_get_vocab(model)`; context via `llama_init_from_model()` with
   n_ctx=2048, n_threads=4 (unchanged from current code).
2. **Tokenize (two-pass):** call `llama_tokenize(vocab, prompt, len, NULL, 0,
   true, true)` → negative count n; allocate vector<llama_token>(n); call again
   to fill. (simple.cpp L74–86 pattern.)
3. **Sampler chain:** `llama_sampler_chain_init(llama_sampler_chain_default_params())`
   + `llama_sampler_chain_add(smpl, llama_sampler_init_greedy())` — greedy chosen
   for M1 determinism (same prompt ⇒ same output ⇒ testable); temperature/top-p
   chain deferred to M4 UX work.
4. **Decode loop:** prime with `llama_batch_get_one(tokens.data(), n_tokens)`;
   iterate: `llama_decode(ctx, batch)` → `llama_sampler_sample(smpl, ctx, -1)`
   → EOG check (`llama_vocab_is_eog`) → `llama_token_to_piece(vocab, id, buf,
   sizeof buf, 0, true)` appended to std::string result → rebind
   `batch = llama_batch_get_one(&new_token_id, 1)`. Cap at 256 new tokens.
5. **State hygiene:** clear KV cache before each generation via
   `llama_memory_clear(llama_get_memory(ctx), true)` (pattern proven in vendored
   examples/embedding/embedding.cpp batch_decode) so consecutive
   generateCompletion calls don't inherit stale positions; JNI signatures stay
   UNCHANGED except an ADDITIVE `loadModel(path, n_gpu_layers)` overload
   (Kotlin default arg keeps existing callers source-compatible).
