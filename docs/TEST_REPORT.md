# TEST_REPORT — Clean-Room Validation Round (M1 build-level)

Date: 2026-08-24
Commit: 9744559 (base before this round) → new commit after locks (see Push evidence)
Wrapper: Gradle 8.6, NDK 26.1.10909125, CMake 3.22.1, JDK 17

## CR1 — clean

Command: `.\gradlew.bat clean --console=plain`
Tail:
```
> Task :app:externalNativeBuildCleanDebug
Clean ggml-base-arm64-v8a, phonelm-arm64-v8a, ggml-arm64-v8a, llama-arm64-v8a, ggml-cpu-arm64-v8a
> Task :app:externalNativeBuildCleanRelease
> Task :app:clean
BUILD SUCCESSFUL in 58s
3 actionable tasks: 3 executed
```
PASS

## CR2 — testDebugUnitTest (19 original)

Command: `.\gradlew.bat testDebugUnitTest --console=plain` (post-clean)
Tail:
```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 6s
29 actionable tasks: 29 executed
```
XML results from `app/build/test-results/testDebugUnitTest/TEST-*.xml`:
```
com.phonelm.core.ModelLocatorTest: tests=7 failures=0 errors=0 skipped=0
  - valid gguf recognized by extension and nonzero size
  - non-gguf extension rejected
  - bundled copy dir lives under filesDir models
  - directory named gguf rejected
  - empty file rejected
  - largest valid gguf wins regardless of order
  - no candidates resolves to null
com.phonelm.rag.ChunkerTest: tests=7 failures=0 errors=0 skipped=0
  - oversized paragraph is hard-wrapped at maxChars
  - short text is a single chunk
  - hard-wrapped output preserves total character count
  - content is preserved in order
  - blank text yields no chunks
  - paragraphs merge up to max without exceeding it
  - no chunk ever exceeds maxChars
com.phonelm.rag.PromptBuilderTest: tests=5 failures=0 errors=0 skipped=0
  - single context produces context block and question
  - no contexts returns the raw question
  - context text containing newlines is embedded verbatim
  - multiple contexts are separated by blank line and keep order
  - question with special characters passes through unchanged
Total: 19 tests, 0 failures, 0 errors, 0 skipped
```
PASS — all 19 original present and green by name, none deleted or skipped.

## CR3 — assembleDebugAndroidTest

Command: `.\gradlew.bat assembleDebugAndroidTest --console=plain`
Tail:
```
> Task :app:assembleDebugAndroidTest
BUILD SUCCESSFUL in 10s
46 actionable tasks: 23 executed, 4 from cache, 19 up-to-date
```
Anti-placeholder assertion intact (from `app/src/androidTest/java/com/phonelm/JniSmokeTest.kt:55`):
```
assertFalse(... response.contains("Native inference placeholder"))
```
PASS

## CR4 — GGUF-present assembleDebug

State: `app/src/main/assets/models/qwen2.5-0.5b-instruct-q4_k_m.gguf` present (491400032 bytes = 468.6 MB)
Command: `.\gradlew.bat clean assembleDebug --console=plain`
Tail:
```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 2m 5s
44 actionable tasks: 20 executed, 24 from cache
```
APK: `app/build/outputs/apk/debug/app-debug.apk` = 531583642 bytes = 506.96 MB
PASS

## CR5 — GGUF-absent assembleDebug (D4 gate)

Procedure: moved GGUF to `$env:TEMP\phonelm_gguf.bak` (so `assets/models/` empty), then clean build, then restored.
Absent build command: `.\gradlew.bat clean assembleDebug --console=plain` (assets empty)
Tail:
```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 1m 46s
44 actionable tasks: 22 executed, 22 from cache
```
APK (absent): 53094885 bytes = 50.64 MB
Restore: moved file back, verified `Test-Path .../qwen2.5-0.5b-instruct-q4_k_m.gguf` = True
Rebuilt present (clean) to restore final state: 531583642 bytes = 506.96 MB
PASS — D4 gate holds: build succeeds with and without GGUF.

## TC-01 — Chunker edges

File: `app/src/test/java/com/phonelm/rag/ChunkerEdgeTest.kt` (6 tests)
Target: empty, whitespace-only, exact-boundary, +1 wrap, oversized doc, unicode.
Result from `TEST-com.phonelm.rag.ChunkerEdgeTest.xml`: tests=6 failures=0 errors=0 skipped=0
Cases:
- TC-01 empty input yields no chunks
- TC-01 whitespace-only yields no chunks
- TC-01 exact-boundary length is one chunk not two
- TC-01 exact-boundary plus one wraps to two
- TC-01 oversized doc splits deterministically
- TC-01 unicode emoji does not crash and preserves count
PASS

## TC-02 — PromptBuilder

File: `app/src/test/java/com/phonelm/rag/PromptBuilderRegressionTest.kt` (4 tests)
Result: `TEST-com.phonelm.rag.PromptBuilderRegressionTest.xml` tests=4 failures=0 skipped=0
Checks: zero contexts → plain prompt; N contexts → ordered assembly with Title:/Content: and Question: headers; no placeholder string in any template; Context header before Question header.
PASS — also asserts no "Native inference placeholder" can leak into any prompt template.

## TC-03 — ModelLocator

File: `app/src/test/java/com/phonelm/core/ModelLocatorRegressionTest.kt` (4 tests)
Result: `TEST-com.phonelm.core.ModelLocatorRegressionTest.xml` tests=4 failures=0
Checks: bundled wins (largest-wins documented); bundled absent → downloads fallback; neither → null without exception; non-gguf candidates ignored.
PASS

## TC-04 — JNI contract lock

File: `app/src/test/java/com/phonelm/JniContractLockTest.kt` (1 test)
Method: parses `LlamaEngine.kt` `external fun (\w+)` vs `NativeBridge.cpp` `Java_com_phonelm_core_LlamaEngine_\w+`, asserts every external has a matching native symbol.
Result: `TEST-com.phonelm.JniContractLockTest.xml` tests=1 failures=0
Found externals: [loadModel, loadModelWithGpuLayers, unloadModel, generateCompletion, getEmbeddings]; symbols: 5 matches.
PASS — kills silent JNI drift.

## TC-05 — Anti-placeholder static lock

File: `app/src/test/java/com/phonelm/AntiPlaceholderLockTest.kt` (1 test)
Checks: `NativeBridge.cpp` contains 0 occurrences of "Native inference placeholder" and contains `llama_sampler_sample`.
Result: tests=1 failures=0
PASS — complements runtime JniSmokeTest anti-placeholder gate.

## TC-06 — Pure-class wiring lock

File: `app/src/test/java/com/phonelm/WiringLockTest.kt` (1 test)
Checks: `ChatViewModel.kt` contains `PromptBuilder.buildRagPrompt` and `HomeScreen.kt` contains `ModelLocator.resolveModel`.
Result: tests=1 failures=0
PASS — locks audit PL5 (no orphans).

## TC-07 — No-new-runtime-dep gate

File: `app/src/test/java/com/phonelm/DependencyFreezeTest.kt` (1 test)
Method: parses `app/build.gradle.kts` for `implementation(...)` / `api(...)` (excludes testImplementation/androidTestImplementation/debugImplementation), asserts each expected D8 substring present and no unknown group.
Frozen baseline (13 runtime impl substrings locked):
- androidx.core:core-ktx:1.12.0
- androidx.lifecycle:lifecycle-runtime-ktx:2.6.2
- androidx.activity:activity-compose:1.8.1
- androidx.compose:compose-bom:2023.08.00 (platform)
- androidx.compose.ui:ui
- androidx.compose.ui:ui-graphics
- androidx.compose.ui:ui-tooling-preview
- androidx.compose.material3:material3
- androidx.navigation:navigation-compose:2.7.5
- androidx.compose.material:material-icons-extended
- com.tom-roush:pdfbox-android:2.0.27.0
- com.google.android.gms:play-services-mlkit-text-recognition:19.0.0
- com.microsoft.onnxruntime:onnxruntime-android:1.16.3
Result: tests=1 failures=0; count in 11..14, no unknown group.
PASS

## TC-08 — GGUF-not-in-git

Check: `git ls-files | Select-String "\.gguf"` and `git ls-files | Select-String "assets/models"`
Evidence:
```
> git ls-files | Select-String "\.gguf"
(no output)
> git ls-files | Select-String "assets/models"
(no output)
COUNT: 0
```
File test: `app/src/test/java/com/phonelm/GgufNotInGitTest.kt` (1 test) runs `git ls-files` via ProcessBuilder and asserts both filters empty.
Result: `TEST-com.phonelm.GgufNotInGitTest.xml` tests=1 failures=0
PASS

## TC-09 — README standalone

File: `app/src/test/java/com/phonelm/ReadmeStandaloneLockTest.kt` (1 test)
Checks: extracts ```mermaid block from README.md, asserts no ForestControl/OpenTrae/Python, and asserts contains Kotlin/Compose UI, LlamaEngine.kt, NativeBridge.cpp, vendored llama.cpp.
Result: tests=1 failures=0
PASS

Full run after locks: `.\gradlew.bat testDebugUnitTest` → BUILD SUCCESSFUL in 5s, total 39 tests across 12 suites, 0 failures, 0 errors, 0 skipped:
```
com.phonelm.AntiPlaceholderLockTest: 1
com.phonelm.core.ModelLocatorRegressionTest: 4
com.phonelm.core.ModelLocatorTest: 7
com.phonelm.DependencyFreezeTest: 1
com.phonelm.GgufNotInGitTest: 1
com.phonelm.JniContractLockTest: 1
com.phonelm.rag.ChunkerEdgeTest: 6
com.phonelm.rag.ChunkerTest: 7
com.phonelm.rag.PromptBuilderRegressionTest: 4
com.phonelm.rag.PromptBuilderTest: 5
com.phonelm.ReadmeStandaloneLockTest: 1
com.phonelm.WiringLockTest: 1
Total: 39
```

## RUNTIME VALIDATION: PENDING HUMAN (MANUAL_VERIFY §B)

This entire clean-room round is build-level only. The following 7 items from
docs/MANUAL_VERIFY.md §B remain HUMAN-ONLY and were NOT claimed:

1. Install (adb install, launcher icon visible)
2. Cold launch (dark background, no crash)
3. No-model state (clean “no model loaded”)
4. Model load (bundled GGUF → “model loaded”)
5. Real-generation gate (generateCompletion != placeholder)
6. Embedding determinism gate (instrumented, 384-dim, norm≈1)
7. Logcat sanity (adb logcat -s PhoneLM_Native)

No emulator was launched; no runtime results are asserted.

## Push evidence
