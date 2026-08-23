# CODEBASE_MAP.md

Last updated: 2026-08-23 (Phase 0). All file:line references verified by direct read.

## 1. Annotated file tree

```
PhoneLM_v2/
├── build.gradle.kts              # Root plugins: AGP 8.2.0, Kotlin 1.9.20, ObjectBox 4.1.0
├── settings.gradle.kts           # Single :app module; objectbox resolutionStrategy workaround
├── gradle/wrapper/gradle-wrapper.properties  # ⚠ gradle-9.0-milestone-1 (see §4)
├── local.properties              # SDK path (user machine)
└── app/
    ├── build.gradle.kts          # ⚠ Vulkan forced ON (L26); proguard-rules.pro ref (L37)
    ├── proguard-rules.pro        # ❌ MISSING (referenced but does not exist)
    └── src/main/
        ├── AndroidManifest.xml   # INTERNET + broad storage perms; AccessibilityService registered
        ├── cpp/
        │   ├── CMakeLists.txt    # add_subdirectory(llama.cpp); GGML_USE_VULKAN=ON FORCE (L10)
        │   ├── NativeBridge.cpp  # JNI impl — 2 of 4 functions are FAKE (see §3)
        │   └── llama.cpp/        # Vendored upstream (recent; has llama_model_load_from_file,
        │                         #   sampler-chain API, pooling types — NOT an ancient fork)
        └── java/com/phonelm/
            ├── MainActivity.kt            # NavHost: home→chat; viewModels<ChatViewModel>
            ├── core/LlamaEngine.kt        # object w/ 4 external fun; loadModelSafe/generate(Flow)
            ├── viewmodel/ChatViewModel.kt # RAG retrieval + prompt assembly + <think> parse +
            │                              #   ⚠ regex JSON agent-intent parsing (L134)
            ├── rag/
            │   ├── DocumentProcessor.kt   # PDFBox text extract, chunked(500), MLKit OCR path
            │   ├── EmbeddingGenerator.kt  # ONNX MiniLM — ❌ FAKE: no tokenizer, returns random vec
            │   └── VectorStore.kt         # In-memory cosine-sim store (UNUSED by ChatViewModel)
            ├── data/
            │   ├── ModelDownloader.kt     # DownloadManager → HF repo LateMonk/PhoneLM_Models (unverified)
            │   ├── ObjectBox.kt           # BoxStore init via generated MyObjectBox
            │   ├── VectorEntity.kt        # @HnswIndex(dimensions=384, COSINE)
            │   └── VectorStore.kt         # ObjectBox-backed store (nearestNeighbor query)
            ├── service/
            │   ├── AgentBridge.kt         # SharedFlow<AgentAction> bus
            │   └── PhoneLMAccessibilityService.kt  # click-by-text / scroll actions
            └── ui/                        # ChatScreen, HomeScreen, theme (Compose M3)
```

## 2. JNI contract table

| Kotlin `external fun` (core/LlamaEngine.kt) | Native symbol (NativeBridge.cpp) | Status |
|---|---|---|
| `loadModel(path): Boolean` (L17) | `Java_com_phonelm_core_LlamaEngine_loadModel` (L18) | **Real** but hard-wired: `n_gpu_layers=99` (L24); uses deprecated API `llama_load_model_from_file` |
| `unloadModel()` (L18) | `..._unloadModel` (L52) | Real |
| `generateCompletion(prompt): String` (L19) | `..._generateCompletion` (L65) | **FAKE** — returns placeholder string; no tokenize/decode/sample |
| `getEmbeddings(text): FloatArray?` (L20) | `..._getEmbeddings` (L89) | **FAKE** — returns fixed ramp vector (dim 1024), no inference |

Note: the vendored llama.h is a recent upstream snapshot with modern APIs
(`llama_model_load_from_file`, `llama_sampler_chain_*`, pooling types), so the real
decode loop can use current best-practice APIs rather than legacy ones.

## 3. Everything fake or broken (file:line)

**Fake:**
- `app/src/main/cpp/NativeBridge.cpp:66–87` — `generateCompletion`: placeholder string; TODO block at L76–80.
- `app/src/main/cpp/NativeBridge.cpp:89–111` — `getEmbeddings`: static dummy vector, dim 1024.
- `app/src/main/java/com/phonelm/core/LlamaEngine.kt:27–36` — `generate()` fakes "streaming" (single emit).
- `app/src/main/java/com/phonelm/rag/EmbeddingGenerator.kt:39–88` — no real tokenizer; runs nothing on ONNX; returns `Math.random()` vector (L82); dummy input tensor built but unused (L72).
- Agent intent parsing `ChatViewModel.kt:134` — fragile regex over model output.

**Broken / inconsistent:**
- `gradle/wrapper/gradle-wrapper.properties:4` — **Gradle 9.0-milestone-1** vs **AGP 8.2.0** (root build.gradle.kts:2). AGP 8.2 does not support Gradle 9 milestones → wrapper must be pinned to stable 8.x.
- `app/build.gradle.kts:37` — references `proguard-rules.pro`; file **does not exist** (`Test-Path` returned False). Release builds break.
- `ChatViewModel.kt:6` imports `com.phonelm.rag.VectorStore` **and** `:30` imports `com.phonelm.data.VectorStore` — conflicting same-simple-name imports; likely a compile error today.
- Dimension mismatch: JNI fake embeddings are **1024-dim** (NativeBridge.cpp:97); `VectorEntity` HNSW index expects **384** (`data/VectorEntity.kt:14`).
- Two competing VectorStores: `rag/VectorStore.kt` (in-memory, unused by app flow) vs `data/VectorStore.kt` (ObjectBox, used by ViewModel).
- `DocumentProcessor.kt:23–24` constructs its own `EmbeddingGenerator` + ObjectBox `VectorStore`, bypassing the ViewModel's instances → separate ONNX sessions per call site.
- `DocumentProcessor.kt:74–75` — duplicate `latch.await()` (harmless but wrong).
- OCR fallback never invoked: scanned PDFs return early (`DocumentProcessor.kt:34–37`).
- No GGUF bundled anywhere under `src/main/assets` or res.
- No test source sets at all (`app/src/test`, `app/src/androidTest` do not exist).
- `EmbeddingGenerator.kt:21` reads models from public `Downloads/PhoneLM/` — fragile storage tied to `MANAGE_EXTERNAL_STORAGE`.

## 4. Build-system diagnosis

| Item | Value | Verdict |
|---|---|---|
| Gradle wrapper | 9.0-milestone-1 | Incompatible with AGP 8.2.0 → pin to gradle-8.6-bin.zip (DECISIONS.md D1) |
| AGP | 8.2.0 | Fine with Gradle 8.2+; NOT upgrading per supervisor decision |
| Kotlin / Compose compiler | 1.9.20 + extension 1.5.4 | Correct pairing |
| NDK / CMake | 26.1.10909125 / 3.22.1, arm64-v8a only | Consistent |
| Vulkan | Forced in 3 places: `app/build.gradle.kts:26`, `cpp/CMakeLists.txt:10` (FORCE), `NativeBridge.cpp:24` (`n_gpu_layers=99`) | Needs configurable n_gpu_layers + CPU fallback; emulator typically lacks Vulkan compute support |
| proguard-rules.pro | Missing | Must create minimal file |
| ObjectBox | Plugin 4.1.0 generates `MyObjectBox` at build time; `resolutionStrategy` workaround in settings.gradle.kts | Plausibly fine; verify on first build |

## 5. Verification status

Everything above was established by reading source files only. No build was run in
Phase 0. First evidence-producing action of Phase 3 will be `gradlew assembleDebug`
after M1 build fixes, logged in docs/TEST_LOG.md.
