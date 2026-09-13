# UI_SPEC.md

## 1. Visual direction

Reference mood: light, translucent, cool-toned anime UI with a restrained futuristic feel.

Do not copy any specific game UI literally.

Design characteristics:

- Character occupies the majority of the screen.
- UI floats over the character instead of dividing the screen into rigid sections.
- Frosted / translucent panels.
- Thin borders, low-contrast separators.
- Soft corner radii.
- Minimal shadows.
- Avoid Material-card-heavy composition.
- Avoid dense messaging-app bubbles in the primary view.
- Prefer calm cool blue / pale neutral accents.
- Text remains highly readable over changing character/background imagery.

## 2. Screen composition

Root:

```text
Box
├── CharacterRenderLayer
├── ReadabilityGradientLayer
└── CompanionUiLayer
    ├── TopBar
    ├── CharacterMeta
    ├── CurrentUtterancePanel
    ├── InputLayer
    └── BottomActions
```

### CharacterRenderLayer

- Full-bleed.
- Must remain visually dominant.
- Renderer lifecycle is owned by existing native integration.
- V0.1 must not restructure JNI/C++ renderer unless required to display under Compose.

### ReadabilityGradientLayer

Use subtle gradients only where text/UI overlays need contrast:

- top edge
- lower 30–40% of viewport
- optional local scrim behind dialogue

Avoid globally dimming the character.

## 3. Home / Idle

Primary reference: `home_idle.png`

State intent:
- user opens app
- character is already present
- minimal UI chrome
- current utterance may be visible
- input box is not permanently expanded

Elements:

- top-left: character name + optional small status
- top-right: lightweight utility actions
- center: Live2D character
- lower area: current dialogue panel
- bottom: 3–4 lightweight actions

Suggested actions:
- Chat
- Interact
- Camera/Vision placeholder
- More

The bottom actions should feel like floating controls, not a navigation bar.

## 4. Home / Input expanded

Primary reference: `home_input.png`

Trigger:
- tap Chat action
- tap current dialogue panel
- optionally swipe upward from bottom

Behavior:
- keep character visible
- input panel animates upward
- bottom actions collapse or fade
- keyboard appearance must not push character into an unusably small area

Input UI:
- translucent rounded container
- text field
- microphone button
- optional send action when text exists

## 5. Home / Speaking

Primary reference: `home_speaking.png`

State intent:
- assistant response is currently being spoken/rendered

Differences from idle:
- current utterance panel is active
- optional waveform / voice activity indicator
- expression/animation state driven externally
- input remains available but visually secondary

Do not show full history here.

## 6. History bottom sheet

Primary reference: `history_sheet.png`

Behavior:
- open via upward swipe or explicit history affordance
- use modal / partially expanded bottom sheet
- preserve character visible behind translucent sheet where practical
- history scrolls independently

Recommended sheet states:
- hidden
- half-expanded (~55–65% height)
- full-height

Message layout:
- user and assistant can have different alignment
- avoid oversized chat bubbles
- compact timestamp
- optional avatar only for assistant messages

## 7. Dimensions (starting values)

These are starting points, not immutable constants.

- page horizontal padding: 16dp
- compact spacing: 8dp
- normal spacing: 12–16dp
- large spacing: 24dp
- dialogue radius: 20–24dp
- input radius: 22–28dp
- floating action size: 44–52dp
- panel border: ~1dp with low alpha
- translucent panel alpha: roughly 0.62–0.78 depending on content
- blur: use only if platform/render path performs well

## 8. Typography

Prefer existing app typography first.

Hierarchy:
- character name: title/small-title
- dialogue: body-large
- helper/status: body-small
- controls: label-medium

Avoid excessive weight contrast.

## 9. Motion

Motion should reinforce character presence, not feel like a dashboard.

Suggested:
- bottom controls fade/slide 120–220ms
- input panel spring or ease-out 180–260ms
- history sheet standard Material physics acceptable
- current utterance panel crossfade / content-size animation
- no flashy scale animations on every interaction

## 10. Accessibility / resilience

- Ensure contrast over arbitrary Live2D backgrounds.
- Respect system font scaling where practical.
- Avoid critical controls beneath display cutouts.
- UI must remain usable when Live2D renderer is temporarily unavailable.
