# MetalExp

`MetalExp` is a Fabric mod project scaffold for a native Metal rendering experiment on modern Minecraft snapshots.

The root layout stays close to a typical Fabric mod:

- `src/main` and `src/client` hold the mod entrypoints, resources, and mixin configs
- `build.gradle`, `gradle.properties`, and the wrapper remain in the root

The project also reserves space for the larger renderer effort:

- `native/` for macOS Cocoa and Metal bridge code
- `shared/` for cross-cutting models and diagnostics
- `docs/` for design notes and implementation plans

## Current Milestone

The current codebase only implements the bootstrap and backend-selection core:

- project-owned backend selection config
- fallback and strict-mode planning
- startup diagnostics
- a client startup hook for future negotiation work

It does not yet replace the in-game option screen or create a real Metal backend.

## Next Steps

1. Retarget dependency versions for the exact Minecraft snapshot you want to build against.
2. Replace the placeholder mixins with graphics-settings and backend-negotiation hooks.
3. Start the native bridge in `native/`.
