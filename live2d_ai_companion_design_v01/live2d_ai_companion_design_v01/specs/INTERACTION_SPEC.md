# INTERACTION_SPEC.md

## Core state model

```kotlin
sealed interface CompanionUiState {
    data object Idle : CompanionUiState
    data object InputExpanded : CompanionUiState
    data object Listening : CompanionUiState
    data object Thinking : CompanionUiState
    data class Speaking(val utterance: String) : CompanionUiState
    data object HistoryOpen : CompanionUiState
}
```

This exact sealed hierarchy is illustrative. Adapt to the project architecture if an existing state model is already present.

## Primary transitions

```text
App launch
  -> Idle

Idle
  -> InputExpanded      tap Chat / dialogue area
  -> HistoryOpen        swipe up / history action

InputExpanded
  -> Thinking           submit text
  -> Listening          hold/tap mic, depending existing UX
  -> Idle               dismiss

Thinking
  -> Speaking           response becomes available
  -> InputExpanded      recoverable error / cancel

Speaking
  -> Idle               speech complete
  -> InputExpanded      user begins input
  -> HistoryOpen        user opens history

HistoryOpen
  -> previous state     dismiss sheet
```

## Dialogue behavior

The main screen displays only the current conversational turn or current assistant utterance.

Full conversation history belongs in HistoryOpen.

Do not append a vertically growing message list to the main character screen.

## Keyboard behavior

When IME appears:
- keep the face/upper body visible where possible
- shift/resize lower UI first
- do not simply resize the entire scene into the top half
- renderer layout should remain stable whenever possible

## Back behavior

Priority:
1. close history sheet
2. close input / keyboard
3. standard activity back behavior

## Speaking state

The UI layer should consume speaking state but must not directly own Live2D parameters.

Desired long-term boundary:

```text
CharacterCore / Behavior
    -> CharacterState
        -> Renderer
        -> UI
        -> Voice
```

V0.1 can use mock state.

## History

Opening history must not stop the character runtime by default.

History sheet is an information layer over the character experience, not a route to a separate messaging product.
