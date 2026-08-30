# MANUAL_VERIFY.md — consolidated testing guide

Everything the agent cannot verify without a device lives here. The user runs
these on Android Studio / emulator. Numbered; check off as completed.

## A. Build-level tests (agent runs, evidence auto-logged in TEST_LOG.md)
| # | Command | Pass criteria |
|---|---|---|
| B1 | `.\gradlew.bat assembleDebug` (no GGUF present) | exit 0 |
| B2 | Copy GGUF via `scripts/fetch_model.ps1`, re-run | exit 0, APK contains asset |
| B3 | `.\gradlew.bat testDebugUnitTest` | chunking + prompt-assembly tests green |
| B4 | `git status` after any step | only declared files changed |

## B. Emulator/device tests (user runs)
Prereqs: Android Studio emulator, arm64-v8a system image (API 28+ recommended).

1. **Install:** `adb install app/build/outputs/apk/debug/app-debug.apk`
   → success, launcher icon visible.
   *Validates: Step 4 adaptive icons + theme resources.*
2. **Launch cold:** tap icon → dark background immediately, no white flash,
   no crash.
   *Validates: themes.xml windowBackground; backup/extraction rules parse
   harmlessly.*
3. **No-model state:** app opens to Home with clean "no model loaded"
   indication, no crash.
   *Validates: D4 gate — assembleDebug/runtime OK without GGUF.*
4. **Model load (after decode-loop step):** bundled GGUF loads CPU-only →
   "model loaded" state within reasonable time (<60 s acceptable on emulator).
   *Validates: loadModel path, n_gpu_layers=0 CPU fallback.*
5. **Real generation gate (after decode-loop step):** send "Hello" → reply that
   is NOT "Thinking... (Native inference placeholder".
   *Permanently kills the fake-engine bug.*
6. **Embedding determinism gate (after M2):** run
   `.\gradlew.bat connectedDebugAndroidTest` → instrumented test passes:
   same input twice → identical 384-dim vector, L2 norm ≈ 1.0, non-zero.
   *Permanently kills the random-floats bug.*
7. **Logcat sanity:** `adb logcat -s PhoneLM_Native` shows load/completion logs;
   no UnsatisfiedLinkError anywhere.

## C. Regression watchlist (re-checked every future step)
- assembleDebug stays green WITH and WITHOUT the GGUF present.
- APK size delta vs baseline ≤ GGUF file size.
- No new Gradle/Maven dependencies appear (DECISIONS.md D8).
- Vendored llama.cpp tree untouched (D9: gitignored; provenance hash pinned).

## Status log (append-only)
| Date | Items verified | Result |
|---|---|---|
| 2026-08-23 | (none yet — created at Step 4.0) | — |
| 2026-08-23 | Agent-side gates B1 (no-GGUF build), B3 (19 JVM tests), instrumented template compile | PASS — device items now ready for user run |
