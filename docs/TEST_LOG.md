# TEST_LOG.md — evidence log

Every entry: date, step, command, verbatim relevant output tail.

---

## 2026-08-23 — Phase 3 Step 0 (audit conditions)

### T0.1 File existence + directory listing

Command:
```
Get-ChildItem docs | Select-Object Name,Length
```
Output:
```
Name               Length
----               ------
BLOCKERS.md          1026
CODEBASE_MAP.md      6926
DECISIONS.md         4986
PLAN.md              7305
PROGRESS.md          1318
RESEARCH_NOTES.md    6644
TEST_LOG.md           764
```
PASS: BLOCKERS.md and TEST_LOG.md exist and are non-empty.

### T0.2 Grep hits

Commands:
```
Select-String -Path docs\BLOCKERS.md -Pattern "D7"
Select-String -Path docs\PROGRESS.md -Pattern "Phase 2 self-review"
Select-String -Path docs\DECISIONS.md -Pattern "D8"
```
Output:
```
BLOCKERS.md:10: ### P1 - ModelDownloader.kt default URLs are dead links (D7 parking)
BLOCKERS.md:13: - Disposition: ModelDownloader stays parked in v1 per DECISIONS.md D7; becomes
BLOCKERS.md:15: - Cross-ref: docs/DECISIONS.md D7, docs/RESEARCH_NOTES.md R5, docs/CODEBASE_MAP.md §3.
PROGRESS.md:14: ## Cuts applied (Phase 2 self-review)
DECISIONS.md:36: ## D8 - No new dependencies in v1
```
PASS: all three greps hit.

### T0.3 PROGRESS.md no longer contains inline BLOCKERS stub

Command:
```
Select-String -Path docs\PROGRESS.md -Pattern "# BLOCKERS.md"
```
Output:
```
PASS: no inline BLOCKERS stub in PROGRESS.md
```
PASS CRITERIA MET for Step 0. Proceeding to M1 step 1 per supervisor authorization.

---

## 2026-08-23 — M1 Step 1 (wrapper pin + baseline build)

### Intent
Pin Gradle wrapper to 8.6 (D1); produce --version evidence; capture baseline
assembleDebug verbatim; verify .gitignore model-asset coverage.

### T1 — gradlew --version

Command:
```
.\gradlew.bat --version
```
Output (verbatim):
```
Downloading https://services.gradle.org/distributions/gradle-8.6-bin.zip
.............100%

Welcome to Gradle 8.6!
------------------------------------------------------------
Gradle 8.6
------------------------------------------------------------
Build time:   2024-02-02 16:47:16 UTC
Revision:     d55c486870a0dc6f6278f53d21381396d0741c6e

Kotlin:       1.9.20
Groovy:       3.0.17
Ant:          Apache Ant(TM) version 1.10.13 compiled on January 4 2023
JVM:          17.0.17 (Microsoft 17.0.17+10-LTS)
OS:           Windows 11 10.0 amd64
```
PASS: Gradle 8.6 confirmed (was 9.0-milestone-1). AGP/Kotlin untouched.

### T2 — baseline assembleDebug

Command:
```
.\gradlew.bat assembleDebug --console=plain
```
Result: BUILD FAILED in 3m 53s (expected/acceptable — baseline).
Full log saved to %TEMP%\opencode\assemble_baseline.log.
Relevant error (verbatim):
```
> Task :app:checkDebugAarMetadata FAILED
FAILURE: Build failed with an exception.
* What went wrong:
> Configuration `:app:debugRuntimeClasspath` contains AndroidX dependencies,
  but the `android.useAndroidX` property is not enabled, which may cause runtime issues.
```
Diagnosis (for step 2, NOT fixed here): repo root has NO gradle.properties;
`android.useAndroidX=true` (+ `android.nonTransitiveRClass` optional) is missing.
Failure occurs at checkDebugAarMetadata — before Kotlin compile / NDK / CMake,
so the previously predicted compile errors (ChatViewModel import conflict etc.)
are still unverified and will surface after this fix.

### T3 — .gitignore coverage of app/src/main/assets/models/

Command:
```
Test-Path .gitignore   → False
```
Finding: NO root .gitignore exists at all; therefore no matching line.
Additionally: the directory is NOT a git repository
(`git rev-parse --is-inside-work-tree` → "fatal: not a git repository"),
so `git status` proof-of-minimal-change is unavailable; recorded as an honest
limitation. Proof of minimal change for this step is procedural: the only file
edited was gradle/wrapper/gradle-wrapper.properties (distributionUrl line only),
as documented above.
Disposition: .gitignore creation (including `app/src/main/assets/models/`) is
deferred to step 2 together with gradle.properties, per D4 — kept out of step 1
to respect the single-change rule.

### Step 1 verdict
T1 PASS · T2 logged (baseline failure understood) · T3 gap recorded, fix scheduled.
Only gradle-wrapper.properties changed.

---

## 2026-08-23 — M1 Step 2 (gradle.properties + git baseline + re-run)

### Intent (declared before implementation)
Unblock AndroidX gate via root gradle.properties; establish measurable git baseline
(init + .gitignore + one baseline commit); re-run assembleDebug to expose next error
layer. Verification: T1 file content, T2 git checks, T3 build tail, T4 vendored hash.

### T1 — gradle.properties content
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
```
(caching/parallel lines added later same day as user-directed build-time optimization,
see "Timing" below; no jetifier, per DECISIONS.md D10.)

### T2 — git baseline
```
git init                      → Initialized empty Git repository .../PhoneLM_v2/.git/
git rev-parse --is-inside-work-tree → true
Baseline commit: 388b729 "Baseline: pre-existing state + wrapper pinned to Gradle 8.6 + planning docs"
  35 files changed, 2266 insertions(+)
git status --porcelain after commit → clean at commit time
```
.gitignore content (verbatim):
```
# Gradle
.gradle/
build/

# Android Studio / IDE
.idea/
*.iml
local.properties
.cxx/

# Native build output
app/.cxx/
app/build/

# Bundled GGUF model - NEVER commit (DECISIONS.md D4)
app/src/main/assets/models/

# Vendored llama.cpp - ignored for now, NOT a submodule (DECISIONS.md D9)
app/src/main/cpp/llama.cpp/

# Logs / temp
*.log
```
Post-commit working tree shows only ` M gradle.properties` (the timing optimization).

### T3 — assembleDebug after gradle.properties fix

Result: BUILD FAILED — but the AAR-metadata AndroidX gate PASSED. New error layer:
```
Execution failed for task ':app:checkDebugAarMetadata'.
* What went wrong:
> Could not resolve all files for configuration ':app:debugRuntimeClasspath'.
   > Could not find com.tom_roush:pdfbox-android:2.0.27.0.
     Searched in the following locations: [google, mavenCentral]
```
Full log: %TEMP%\opencode\assemble_step2.log.
Root cause verified externally:
```
https://repo1.maven.org/maven2/com/tom_roush/pdfbox-android/ → 404 Not Found
https://repo1.maven.org/maven2/com/tom_roush/                → 404 Not Found
https://repo1.maven.org/maven2/com/tom-roush/pdfbox-android/ → EXISTS, incl. 2.0.27.0 (2023-01-02)
```
⇒ app/build.gradle.kts:84 dependency coordinate typo: `com.tom_roush` should be
`com.tom-roush`. Fix scheduled for step 3 (NOT fixed in step 2 scope).
Also observed during step 2: res/values and res/drawable are EMPTY while
AndroidManifest references @mipmap/ic_launcher, @style/Theme.PhoneLM,
@xml/data_extraction_rules, @xml/backup_rules → resource-link failure expected
after the dependency layer clears.

### T4 — vendored llama.cpp provenance
```
git -C app/src/main/cpp/llama.cpp rev-parse HEAD
→ bd2a93d4753c4f00443f561ee039220283016ee8
git -C ... log -1 → "gguf-py : add requests to dependencies (#18629)" 2026-01-06
```
Recorded in docs/DECISIONS.md D9.

### Timing optimization (user-directed)
- Run 1 (baseline): 3m53s — one-time dependency downloads over network.
- Run 2 (post-fix): 27s — warm daemon + configuration only.
- Added `org.gradle.caching=true` + `org.gradle.parallel=true` to gradle.properties:
  failed-config cycle now 3.4–4.2s (measured twice with Measure-Command).
- Tool timeouts used: 30 min for full builds; no timeout risk remaining at current scale.
- NDK/CMake path still never reached — real compile time unknown until step ≥4.

### Step 2 verdict
T1 PASS · T2 PASS · T3 fully logged (next-layer failure understood, fix scoped) ·
T4 PASS. Files created this step: gradle.properties, .gitignore, docs/STATUS.md
(+ git metadata + baseline commit).

---

## 2026-08-23 — M1 Step 3 (typo fix + import/latch fixes + proguard-rules.pro)

### Intent (declared before implementation)
Fix pdfbox coordinate typo, ChatViewModel conflicting import, duplicate latch.await();
create minimal proguard-rules.pro; re-run assembleDebug with honest triage
(in-scope fixes fixed, out-of-scope named only).

### T1 — typo fix proof
```
Select-String -Path app\build.gradle.kts -Pattern "tom-roush|tom_roush"
84: implementation("com.tom-roush:pdfbox-android:2.0.27.0")
```
`com.tom-roush` present; zero occurrences of `tom_roush` remain.

### T2 — import/latch diffs (verbatim git diff)
ChatViewModel.kt — dropped import is `com.phonelm.rag.VectorStore`:
```
-import com.phonelm.rag.VectorStore
```
DocumentProcessor.kt:
```
         latch.await()
-        latch.await()
```
latch.await() occurrence count after fix: 1.

### T3 — app/proguard-rules.pro exists (True)
Content: header comment documenting non-minified-release policy and M5 keep-rules
plan (LlamaEngine JNI name lookup, ONNX/ObjectBox defaults). Full text in file.

### T4 — assembleDebug result

BUILD FAILED in 4m 16s at :app:processDebugResources.
Full log: %TEMP%\opencode\assemble_step3.log. Layer progression vs step 2:
- checkDebugAarMetadata PASSED (dependency resolution now works).
- configureCMakeDebug[arm64-v8a] + buildCMakeDebug[arm64-v8a] RAN AND SUCCEEDED —
  first proof the NDK/CMake/Vulkan toolchain works on this machine. Only
  deprecation NOTES for llama_load_model_from_file / llama_new_context_with_model /
  llama_free_model (expected; modernization already planned in PLAN.md M1 step 5).
- Kotlin compile NOT reached (runs after resource processing).
Verbatim failure:
```
Execution failed for task ':app:processDebugResources'.
   > Android resource linking failed
     ERROR: AndroidManifest.xml:10:5-42:19 AAPT: resource xml/data_extraction_rules not found.
     ERROR: AndroidManifest.xml:10:5-42:19 AAPT: resource xml/backup_rules not found.
     ERROR: AndroidManifest.xml:10:5-42:19 AAPT: resource mipmap/ic_launcher not found.
     ERROR: AndroidManifest.xml:10:5-42:19 AAPT: resource mipmap/ic_launcher_round not found.
     ERROR: AndroidManifest.xml:10:5-42:19 AAPT: resource style/Theme.PhoneLM not found.
     ERROR: AndroidManifest.xml:21:9-29:20 AAPT: resource style/Theme.PhoneLM not found.
BUILD FAILED in 4m 16s
```

### Out-of-scope error inventory (named for step 4, NOT fixed)
All six AAPT errors are OUT of step-3 scope (missing res/values + res/xml +
res/mipmap-* content):
1. `res/xml/data_extraction_rules.xml` — missing; referenced AndroidManifest.xml:10
2. `res/xml/backup_rules.xml` — missing; referenced AndroidManifest.xml:10
3. `res/mipmap-anydpi/ic_launcher.xml` (+ round) — missing; referenced manifest icon attrs
4. `res/values/themes.xml` defining `Theme.PhoneLM` — missing; referenced manifest:10,21
(Also referenced by code but unverified until Kotlin runs: PhoneLMTheme.kt usage.)
Kotlin compile layer still unexercised — will surface in step 4 after resources link.

### T5 — layer proof (git status --porcelain)
```
 M app/build.gradle.kts
 M app/src/main/java/com/phonelm/rag/DocumentProcessor.kt
 M app/src/main/java/com/phonelm/viewmodel/ChatViewModel.kt
?? app/proguard-rules.pro
```
(docs/* and gradle.properties modifications are carry-over from accepted steps 0–2,
not new this step.)

### Step 3 verdict
T1 PASS · T2 PASS · T3 PASS · T4 fully logged with precise out-of-scope inventory ·
T5 PASS.

---

## 2026-08-23 — M1 Step 4.0 (audit gap G-PLM-2 close)

### Action
Created docs/MANUAL_VERIFY.md from the supervisor-approved consolidated testing
guide (build-level tests A/B1–B4; emulator/device checklist B1–B7 incl. real-
generation gate and embedding-determinism gate; regression watchlist C).

### Verification
```
Test-Path docs\MANUAL_VERIFY.md → True
Get-ChildItem docs | Measure-Object -Line → (see below)
```
```
PS C:\...\PhoneLM_v2> Test-Path docs\MANUAL_VERIFY.md
True
```
MANUAL_VERIFY.md is now maintained per standing artifact rules; status log
section added for user-run verification results.

---

## 2026-08-23 — M1 Step 4 (missing resources)

### T1 — new resource file contents (all verbatim)

**app/src/main/res/values/themes.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Pure-Compose app: platform NoActionBar parent; dark window background
         to match PhoneLMTheme.kt dark-by-default and avoid startup white flash. -->
    <style name="Theme.PhoneLM" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">#FF10101A</item>
    </style>
</resources>
```

**app/src/main/res/xml/backup_rules.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="file" path="phonelm_models/" />
</full-backup-content>
```

**app/src/main/res/xml/data_extraction_rules.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="phonelm_models/" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="phonelm_models/" />
    </device-transfer>
</data-extraction-rules>
```

**app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml** and **ic_launcher_round.xml**
(identical content):
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

**app/src/main/res/drawable/ic_launcher_foreground.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Simple chat-bubble glyph inside the 66dp adaptive-icon safe zone -->
    <path
        android:fillColor="#FF7C9CFF"
        android:pathData="M34,38 h40 a6,6 0 0 1 6,6 v20 a6,6 0 0 1 -6,6 h-26 l-10,10 v-10 h-4 a6,6 0 0 1 -6,-6 v-20 a6,6 0 0 1 6,-6 z" />
</vector>
```

**app/src/main/res/drawable/ic_launcher_background.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF10101A"
        android:pathData="M0,0 h108 v108 h-108 z" />
</vector>
```

### T2 — assembleDebug result

BUILD FAILED in 8s at :app:processDebugResources.
Full log: %TEMP%\opencode\assemble_step4.log. Verbatim:
```
Execution failed for task ':app:processDebugResources'.
   > Android resource linking failed
     com.phonelm.app-main-57:/xml/accessibility_service_config.xml:7: error:
       resource string/app_name (aka com.phonelm:string/app_name) not found.
     error: failed linking file resources.
BUILD FAILED in 8s
```
Layer analysis:
- ALL SIX prior AAPT errors CLEARED (themes, backup/extraction rules, mipmaps all link).
- One NEW pre-existing gap exposed (was hidden behind earlier failures):
  `res/xml/accessibility_service_config.xml` line **2** (AAPT reports :7, the
  element end) references `@string/app_name`; no `res/values/strings.xml` exists.
- Kotlin compile layer NOT reached yet — still blocked by resource linking.

### Step 5 Scoping Inventory (unforeseen, out-of-scope, NOT fixed)
1. `app/src/main/res/values/strings.xml` MISSING entirely; needed to satisfy
   `@string/app_name` referenced at res/xml/accessibility_service_config.xml:2.
   Minimal fix: create strings.xml with `<string name="app_name">PhoneLM</string>`
   (matches manifest android:label="PhoneLM").
2. Kotlin compile errors — unknown until item 1 lands. Known candidates from
   CODEBASE_MAP §3 remain unverified.

### T3 — scope discipline proof (git status --porcelain)
New this step (untracked resource dirs/files + MANUAL_VERIFY.md):
```
?? app/src/main/res/drawable/            (ic_launcher_foreground/background.xml)
?? app/src/main/res/mipmap-anydpi-v26/   (ic_launcher.xml, ic_launcher_round.xml)
?? app/src/main/res/values/              (themes.xml)
?? app/src/main/res/xml/backup_rules.xml
?? app/src/main/res/xml/data_extraction_rules.xml
?? docs/MANUAL_VERIFY.md                 (Step 4.0 audit gap close)
?? docs/STATUS.md                        (created Step 2 per supervisor requirement)
```
Modified files are carry-over from accepted Steps 2–3 only. NO source code
touched in Step 4.

### Step 4 verdict
T1 PASS · T2 fully logged with triage (in-scope fixes needed: none; inventory
above for Step 5) · T3 PASS.

---

## 2026-08-23 — M1 Step 5 (Kotlin unblock + real decode loop + fetch mechanism)

### Phase 5.1 / T1 — strings.xml + first Kotlin exposure

Created `app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">PhoneLM</string>
</resources>
```
Run 1: processDebugResources PASSED → Kotlin compiled for the FIRST time.
```
e: ChatViewModel.kt:24:2 imports are only allowed in the beginning of file
> Task :app:kaptGenerateStubsDebugKotlin FAILED
```
Triage: TRIVIAL (mid-file imports from fake-engine era) → fixed in-scope by
merging the five stray imports into the header block (git diff on file).
(One transient Gradle daemon crash during this phase, recovered on re-run;
daemon heap stays at -Xmx2048m for now.)

Run 2 exposed two more trivial errors, both fixed in-scope:
```
e: data/VectorStore.kt:40:50 Unresolved reference: nearestNeighbor
e: ui/ChatScreen.kt:162:29 Unresolved reference: clickable
```
Fixes: (a) ObjectBox 4.1.0 API is `nearestNeighbors(float[], int)` PLURAL —
verified via javap against objectbox-java-4.1.0.jar
(`io.objectbox.Property.nearestNeighbors`); one-character rename.
(b) added `import androidx.compose.foundation.clickable`.

Run 3: **BUILD SUCCESSFUL in 1m 16s** — project compiles end-to-end for the
first time ever.

### Phase 5.2 / T2 — research checkpoint
"Step 5 Decode Plan" (5 bullets) written to PROGRESS.md before any C++ change,
based on RESEARCH_NOTES R1 + vendored examples/simple/simple.cpp.

### Phase 5.3 / T3 — real decode loop

NativeBridge.cpp rewritten: modern `llama_model_load_from_file`, additive JNI
overload `loadModelWithGpuLayers(path, n_gpu_layers)` (existing signatures
unchanged), two-pass tokenize, greedy sampler chain (deterministic M1),
llama_decode → sample → EOG → token_to_piece loop capped at 256 tokens,
KV cache cleared per generation, getEmbeddings de-fanged to deterministic zeros.
One C++ iteration needed: forward declaration for the JNI overload
(`error: use of undeclared identifier ...loadWithGpuLayers`) → fixed.
Final evidence:
```
> Task :app:buildCMakeDebug[arm64-v8a]
C/C++: ... note: 'llama_free_model' has been explicitly marked deprecated here
BUILD SUCCESSFUL in 13s
```
Only remaining native warnings: intentional use of deprecated
`llama_free_model` alias in unloadModel (modernization deferred; noted).

### Phase 5.4 / T4 — scripts/fetch_model.ps1

Script created (D4): downloads Qwen2.5-0.5B-Instruct Q4_K_M into
app/src/main/assets/models/, supports `-SourcePath` local-copy mode, size
sanity checks, idempotent re-runs. Executed live:
```
Downloading https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf
OK: ...\app\src\main\assets\models\qwen2.5-0.5b-instruct-q4_k_m.gguf (468.6 MB)
```

### Post-phase gates
- assembleDebug WITH GGUF present: BUILD SUCCESSFUL in 1m 32s.
- APK: app-debug.apk = 507.0 MB (= ~38 MB app + 468.6 MB asset; within
  MANUAL_VERIFY watchlist bound).
- gitignore blocks assets/models/ (`git status --porcelain` shows NO entry under
  app/src/main/assets/models/). Added `app/objectbox-models/` (ObjectBox build
  metadata) to .gitignore to keep tree clean.

### T5 — scope proof (git status --porcelain, code files only)
```
M app/src/main/cpp/NativeBridge.cpp          (Phase 5.3)
M app/src/main/java/com/phonelm/core/LlamaEngine.kt   (additive overload)
M app/src/main/java/com/phonelm/viewmodel/ChatViewModel.kt  (import placement)
M app/src/main/java/com/phonelm/ui/ChatScreen.kt     (clickable import)
M app/src/main/java/com/phonelm/data/VectorStore.kt  (nearestNeighbors rename)
A app/src/main/res/values/strings.xml        (mechanical unblocker, approved)
A scripts/fetch_model.ps1                    (Phase 5.4)
M .gitignore                                 (objectbox-models line)
?? app/src/main/assets/models/*.gguf         (IGNORED - never committed)
```
All within declared Step 5 scope.

### Step 5 verdict
PASS CRITERIA MET: CMake builds with the REAL decode loop; Kotlin compiles clean;
fetch mechanism functional and proven; nothing fake left in the generation path
except the parked JNI getEmbeddings stub (documented, unused).

---

## 2026-08-23 — M1 Final Gates (JVM unit tests + instrumented template)

### Scope
Pure-JVM tests for chunking / prompt assembly / model-path resolution;
instrumented JNI smoke-test template for user's emulator run.
D8 amendment (logged): `testImplementation junit:4.13.2`,
`androidTestImplementation androidx.test.ext:junit:1.1.5`, `androidx.test:runner:1.5.2`
— test-scope only, zero runtime dependencies added.

### New production code extracted for testability (pure Kotlin)
- rag/PromptBuilder.kt — RAG prompt assembly (was inline in ChatViewModel).
- rag/Chunker.kt — paragraph-aware chunking w/ hard wrap (replaces blind
  String.chunked(500)); wired into DocumentProcessor.kt.
- core/ModelLocator.kt — deterministic GGUF resolution (bundled copy > Downloads,
  largest wins); wired into HomeScreen LaunchedEffect which now ALSO copies
  bundled asset GGUFs to filesDir once (completes the D4 runtime mechanism).

### T1 — .\gradlew.bat testDebugUnitTest

First run: 18 tests, 1 FAILED — `ChunkerTest > content is preserved in order`.
Root cause: BAD ASSERTION (expected lossless rejoin of hard-wrapped chunks,
contradicting the documented hard-wrap contract). Test fixed to assert the real
contract + added char-count-preservation test.
Final run:
```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 7s
```
19 tests total across ChunkerTest(7), PromptBuilderTest(5), ModelLocatorTest(7):
all green. HTML report at app/build/reports/tests/testDebugUnitTest/.

### T2 — instrumented template compiles

app/src/androidTest/java/com/phonelm/JniSmokeTest.kt:
- jniLoad_generate_unload_realOutput: copies bundled asset GGUF → filesDir,
  loadModel → generateCompletion → asserts NOT placeholder / not blank /
  no "Error:" prefix → unloadModel. Uses Assume to SKIP (not fail) if no GGUF.
- generate_withoutModel_returnsErrorNotCrash: unload-first safety gate.
```
.\gradlew.bat assembleDebugAndroidTest
> Task :app:assembleDebugAndroidTest
BUILD SUCCESSFUL in 16s
```

### T3 — scope proof (git status --porcelain)
New this step: app/src/test/, app/src/androidTest/, ModelLocator.kt, Chunker.kt,
PromptBuilder.kt (+ wiring diffs in DocumentProcessor/ChatViewModel/HomeScreen),
test deps in app/build.gradle.kts. All other modified/untracked entries are
accepted Step 2–5 carry-over. No out-of-scope changes.

### M1 Final Gates verdict
T1 PASS · T2 PASS · T3 PASS → **M1 functionally complete at the build level.**

---

## 2026-08-23 — DOCS (portfolio-grade README + ARCHITECTURE)

### T1 — README.md contains Mermaid diagram syntax
```
Test-Path README.md → True
Select-String -Pattern "```mermaid" → line 28 ```mermaid
```

### T2 — scripts/fetch_model.ps1 referenced in README
```
Select-String -Pattern "fetch_model\.ps1" → lines 102, 105
  .\scripts\fetch_model.ps1
  .\scripts\fetch_model.ps1 -SourcePath C:\path\to\your\model.gguf
```

### T3 — git commit + push
```
git add README.md docs/ARCHITECTURE.md docs/STATUS.md docs/TEST_LOG.md
git commit -m "docs: add portfolio-grade README and architecture guide"
[main cf39032] docs: add portfolio-grade README and architecture guide
 4 files changed, 396 insertions(+), 5 deletions(-)
 create mode 100644 README.md
 create mode 100644 docs/ARCHITECTURE.md

git push
To https://github.com/PranavPW/PhoneLM.git
   5bf68e0..cf39032  main -> main
```
T1 PASS · T2 PASS · T3 PASS → **DOCS published. M1 Build-Level PUBLISHED.**
