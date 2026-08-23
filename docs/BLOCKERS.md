# BLOCKERS.md

Honest blocker tracking. An accurate blocker beats a fake success.

## Currently open
None.

## Parked / informational (not currently blocking)

### P1 — ModelDownloader.kt default URLs are dead links (D7 parking)
- `data/ModelDownloader.kt:22` fetches `phonelm-1.5b-q4_k_m.gguf`, verified ABSENT from
  LateMonk/PhoneLM_Models (see RESEARCH_NOTES.md R5; actual repo contents listed there).
- Disposition: ModelDownloader stays parked in v1 per DECISIONS.md D7; becomes
  ForestControl-manifest-driven in M4+.
- Cross-ref: docs/DECISIONS.md D7, docs/RESEARCH_NOTES.md R5, docs/CODEBASE_MAP.md §3.

### P2 — HF repo status (verified 2026-08-23)
- LateMonk/PhoneLM_Models EXISTS — https://huggingface.co/LateMonk/PhoneLM_Models
- LateMonk/ForestControl_Models EXISTS — https://huggingface.co/LateMonk/ForestControl_Models
- Neither contains a ≤500MB chat GGUF suitable for bundling; v1 bundles public
  Qwen/Qwen2.5-0.5B-Instruct-GGUF instead (DECISIONS.md D4).
- Cross-ref: docs/RESEARCH_NOTES.md R5.
