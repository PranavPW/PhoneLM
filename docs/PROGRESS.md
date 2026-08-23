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
