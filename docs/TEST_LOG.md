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
