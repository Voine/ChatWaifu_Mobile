# ARCHITECTURE.md

## Goal

Preserve the current native Android + C++ Live2D stack while introducing a modern UI shell that can evolve independently.

## Recommended boundary

```text
Android App
│
├── Companion UI (Compose)
│   ├── Home screen
│   ├── Input
│   ├── History sheet
│   └── Settings/navigation later
│
├── Character Core
│   ├── conversation state
│   ├── behavior state
│   ├── voice state
│   └── future memory / vision
│
└── Character Renderer
    └── Existing Native Live2D / Cubism C++
```

## V0.1 rule

Do not migrate Live2D into Unity.

Do not replace the existing renderer.

Do not rewrite JNI boundaries unless necessary for clean lifecycle integration.

## Compose integration

Preferred layout:

```kotlin
Box(Modifier.fillMaxSize()) {
    CharacterRendererHost(...)
    CompanionOverlay(...)
}
```

`CharacterRendererHost` may wrap the existing view using `AndroidView`.

## State ownership

Prefer:
- one screen-level ViewModel
- immutable UI state
- events flowing upward
- renderer driven through a narrow interface

Avoid:
- Compose elements calling JNI directly
- individual composables owning renderer lifecycle
- LLM callbacks directly mutating UI widgets

## Renderer interface direction

Long-term target, not mandatory for V0.1:

```kotlin
interface CharacterRenderer {
    fun setVisible(visible: Boolean)
    fun applyState(state: CharacterVisualState)
    fun pause()
    fun resume()
}
```

## Future compatibility

Architecture should leave room for:

- Android foreground full-character mode
- background Companion Service
- overlay bubble
- screenshot/vision context
- optional desktop renderer
- optional Unity scene mode

These are future concerns. Do not build them now.
