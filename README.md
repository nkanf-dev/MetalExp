# MetalExp

<p align="center">
  <img src="docs/assets/metalexp-logo.svg" alt="MetalExp logo" width="720" />
</p>

<p align="center">
  Experimental Fabric-delivered backend work for bringing a real native Metal renderer to modern Minecraft snapshots on macOS.
</p>

## What MetalExp Is

`MetalExp` is not a lightweight graphics tweak and it is not a Vulkan-to-Metal wrapper.
The project aims to introduce a real `MetalBackend` alongside Mojang's OpenGL and Vulkan backends, with project-owned settings, startup negotiation, diagnostics, and a native macOS bridge.

## Current Status

The repository now covers the first real hardware-GUI milestone of the project:

- project-owned graphics API preference and config persistence
- startup backend planning with fallback and strict modes
- startup diagnostics logging
- video settings replacement with a `MetalExp` graphics API option
- startup backend negotiation override driven by `MetalExp` config
- a real Java-side `MetalBackend` creation path that now returns a `GpuDevice`
- a native bridge for library loading, process-level Metal capability checks, window-level Cocoa host probing, and persistent `CAMetalLayer` host-surface bootstrap/release
- bootstrap Metal device, surface, buffer, texture, sampler, query, and command-encoder paths that are sufficient to pass `RenderSystem.initRenderer(...)`, `MainTarget` attachment allocation, and real native GUI/panorama draws into a `CAMetalDrawable`

What is still missing:

- shader translation and pipeline compilation
- broad pipeline coverage beyond the currently wired menu-oriented passes
- world rendering viability, resource binding breadth, and general-purpose command submission
- resize/shutdown hardening and broader runtime validation
- a maintainable native split for the growing Metal bridge instead of the current single-file implementation

Today, selecting `METAL` means the game really attempts Metal backend creation instead of failing up front. On supported macOS setups, `MetalExp` now probes Metal/Cocoa readiness, bootstraps a persistent native host surface, constructs a Java `GpuDevice`, uploads both 2D and cubemap textures through the native bridge, and executes native Metal GUI/panorama draws that can reach the Minecraft main menu in `STRICT` mode. It is still not a general-purpose renderer yet, because large parts of the pipeline matrix, world rendering, shader toolchain work, and backend hardening remain unfinished.

## Target Environment

- Platform: macOS
- Minecraft target: `26.2-snapshot-1`
- Loader: Fabric Loader
- Java: 25

## Repository Layout

- `src/main` and `src/client`: Fabric entrypoints, mixins, config, diagnostics, and Minecraft-facing integration points
- `native/`: reserved for the macOS Cocoa and Metal bridge
- `shared/`: reserved for shared models, diagnostics payloads, and future backend-facing types
- `docs/`: design specs, milestone plans, and reverse-engineering notes

## Development Flow

`dev` is the primary development branch for this repository.
Unless explicitly requested otherwise, ongoing implementation and cleanup work should land on `dev`.

Canonical project references:

1. `docs/specs/2026-04-09-metalexp-design.md`
2. `docs/plans/2026-04-09-metalexp-bootstrap-plan.md`
3. `docs/research/2026-04-09-minecraft-26.2-vulkan-metal-research_cn.md`

## Near-Term Roadmap

1. Replace the current Metal scaffolding with a real `MetalBackend` entry path.
2. Expand real Metal render-pass coverage from the current menu-oriented passes to the rest of the GUI pipeline matrix.
3. Add the shader toolchain path from GLSL to SPIR-V to MSL.
4. Carry the renderer from main-menu viability into in-world rendering.
5. Split and harden the native bridge for resize, shutdown, diagnostics, and long-term maintenance.

## Building

```bash
./gradlew test
./gradlew build
```

On macOS, the Gradle build also compiles `build/native/libmetalexp_native.dylib` for the current JNI probe implementation.

## License

This project is licensed under the MIT License. See `LICENSE`.
