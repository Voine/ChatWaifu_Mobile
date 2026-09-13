# IMPLEMENTATION_PLAN.md

## Phase 0 — Inspect

Before changing code:

1. Identify current Activity/Fragment structure.
2. Identify whether UI is XML, View-based, Compose, or mixed.
3. Locate Live2D host view and renderer lifecycle.
4. Locate current chat state/model.
5. Locate ASR/TTS integration points.
6. Report risks before making architectural changes.

## Phase 1 — Static shell

Implement only:

- full-screen character host
- top metadata
- current utterance panel
- bottom floating actions
- input-expanded state
- history bottom sheet with mock messages

No LLM wiring required.

Acceptance criteria:
- app builds
- existing Live2D renderer still works
- no regression to renderer lifecycle
- all four reference states can be reached manually
- UI remains usable without renderer content

## Phase 2 — Existing feature wiring

Connect:
- current message input
- existing model request
- assistant text response
- current TTS start/end state

Do not change model provider architecture unless necessary.

## Phase 3 — Character behavior integration

Later:
- emotion/intent output
- expression mapping
- speaking visual state
- gesture sequencing

## Phase 4 — Companion mode

Explicitly out of V0.1:
- overlay bubble
- MediaProjection/screenshot analysis
- background service
