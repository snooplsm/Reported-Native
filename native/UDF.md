# Unidirectional data flow

UDF is the required feature architecture for both native apps.

```text
UI -- Action --> Store/ViewModel -- State --> UI
                       |
                       +-- Effect --> platform UI boundary
```

## Contract

- A screen renders one or more immutable state values exposed by a store/ViewModel.
- User input, lifecycle callbacks, permissions, and ML results enter the store as typed actions.
- Only the store/ViewModel mutates feature state or starts feature work.
- Navigation, permission prompts, pickers, and external URLs are one-time effects handled by the platform UI.
- Repositories, media scanners, and ML engines are dependencies of a store/ViewModel. They do not own screen state.
- View-local state is limited to transient rendering details such as focus, animation progress, zoom/pan, and sheet detents. It must not start business work or persist a user decision.

## Android

Feature stores implement `UdfStore<State, Action>`, expose a read-only `StateFlow`, and keep the mutable flow private. One-time effects use a `Channel` or `SharedFlow`.

## iOS

Feature stores conform to `UdfStore`, expose `@Published private(set) var state`, and accept typed values through `onAction`. One-time effects are exposed separately and acknowledged after the view handles them.

## ML boundary

Camera, ALPR, classifier, and on-device language-model output must return to the owning store as an action before it changes anything visible. Loading, progress, selected candidates, errors, and model-install state are feature state, not view-local state.

## File organization

- Keep hand-written source files at or below 1,000 lines. Split earlier when a file owns more than one feature concern.
- Screen files contain composition and rendering. Put dialogs, review flows, media previews, and reusable controls in feature-named files beside the screen.
- Store/ViewModel files contain action routing and state transitions. Move API payload building, persistence, uploads, and lookup coordination into focused collaborators.
- ML orchestration, model inference, image preprocessing, and geometry belong in separate files even when they share one feature boundary.
- Prefer module-internal helpers over public APIs when a component is shared only inside the app target.
