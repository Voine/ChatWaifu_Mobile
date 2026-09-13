# COPILOT_TASK.md

You are implementing V0.1 of a Live2D AI Companion Android app.

## First action

Read these files before editing any source code:

1. `/design_v01/README_DESIGN.md`
2. `/design_v01/specs/UI_SPEC.md`
3. `/design_v01/specs/INTERACTION_SPEC.md`
4. `/design_v01/specs/ARCHITECTURE.md`
5. `/design_v01/specs/IMPLEMENTATION_PLAN.md`

Then inspect the existing Android project.

## Important

Do not begin by rewriting the renderer.

Do not migrate to Unity.

Do not treat the reference images as exact pixel specifications.

The Markdown specs are the source of truth.

## Your first response should contain only

1. Current project structure relevant to this task.
2. How Live2D is currently hosted and its lifecycle.
3. Proposed minimal-change implementation path.
4. Files you expect to add or modify.
5. Risks / unknowns.
6. A Phase 1 implementation checklist.

Do not modify code until this analysis is complete.

## After approval or when asked to proceed

Implement Phase 1 from `IMPLEMENTATION_PLAN.md`.

Prefer Jetpack Compose for newly introduced overlay UI if it can coexist cleanly with the existing project.

If the project is not yet Compose-enabled, evaluate the smallest safe integration instead of force-migrating the whole app.

## Quality bar

- character remains visually dominant
- no primary-page chat waterfall
- current utterance only on home
- history in translucent bottom sheet
- no unnecessary abstractions
- preserve existing native renderer
- build after each coherent change
