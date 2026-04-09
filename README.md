# MetalExp

`MetalExp` is a Fabric mod project scaffold for a native Metal rendering experiment on modern Minecraft snapshots.

The root layout stays close to a typical Fabric mod:

- `src/main` and `src/client` hold the mod entrypoints, resources, and mixin configs
- `build.gradle`, `gradle.properties`, and the wrapper remain in the root

The project also reserves space for the larger renderer effort:

- `native/` for macOS Cocoa and Metal bridge code
- `shared/` for cross-cutting models and diagnostics
- `docs/` for design notes and implementation plans

## Current Scope

This scaffold intentionally keeps the Fabric side simple while reserving directories for the native backend that will come later.

## Next Steps

1. Retarget dependency versions for the exact Minecraft snapshot you want to build against.
2. Replace the placeholder mixins with graphics-settings and backend-negotiation hooks.
3. Start the native bridge in `native/`.
