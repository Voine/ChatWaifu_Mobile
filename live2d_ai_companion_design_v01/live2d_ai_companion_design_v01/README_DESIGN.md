# Live2D AI Companion — Design Pack v0.1

This folder is the implementation handoff for the first UI iteration of the Android app.

## Source of truth

1. Markdown specs in `/specs` define structure, behavior, state, and implementation constraints.
2. Images in `/references` define visual direction and composition.
3. If an image appears to conflict with a spec, follow the spec.
4. Do not infer new product requirements from decorative details in the concept art.

## V0.1 scope

Implement only the core foreground experience:

- Home / Idle
- Home / Input expanded
- Home / Speaking
- History bottom sheet

Do **not** implement yet:

- floating companion overlay
- screenshot recognition
- character marketplace
- complex scene switching
- Unity integration
- long-term memory backend
- production LLM / ASR / TTS wiring

Use mock data and existing project infrastructure where possible.

## Product principle

The character is the primary interface. Chat history is secondary.

The app should feel like a virtual character that can converse, not like a messaging app with a character rendered above it.

## Reference images

- `references/overview.png`
- `references/home_idle.png`
- `references/home_input.png`
- `references/home_speaking.png`
- `references/history_sheet.png`

## Recommended implementation

- Native Android
- Jetpack Compose for UI overlays
- Existing Native/C++ Live2D renderer remains unchanged in V0.1
- Place Live2D behind Compose using `AndroidView`, `SurfaceView`, `TextureView`, or the project’s existing host
- ViewModel/state-driven screen state
- No Unity runtime in the foreground chat path

## First Agent instruction

Open `agent/COPILOT_TASK.md` and follow it exactly.
