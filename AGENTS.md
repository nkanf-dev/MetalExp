# MetalExp Agent Guide

This file is the working agreement for agents and collaborators in this repository.

It applies to the repository root and all git worktrees created from it.

## Project Goal

`MetalExp` is not a lightweight tweak mod. It is an experimental Fabric-delivered backend program whose goal is to add a real native Metal backend to modern Minecraft snapshot builds on macOS.

The intended architecture is:

- `MetalBackend` as a first-class backend parallel to Mojang's `GlBackend` and `VulkanBackend`
- a `MetalExp`-owned graphics API setting instead of patching Mojang's enum
- startup backend negotiation controlled by `MetalExp` config and mixins
- a native macOS bridge for Cocoa, `CAMetalLayer`, and Metal lifecycle management
- a shader/toolchain route based on `GLSL -> SPIR-V -> MSL`

## Non-Goals

Do not drift into these directions unless the user explicitly changes scope:

- MoltenVK presented as "native Metal"
- dynamically extending Mojang's `PreferredGraphicsApi` enum
- broad renderer-mod compatibility as an early priority
- overdesigning abstractions before the current milestone requires them

## Canonical Docs

Read these before making architecture-affecting changes.

1. Design spec:
   `docs/specs/2026-04-09-metalexp-design.md`
2. Current bootstrap plan:
   `docs/plans/2026-04-09-metalexp-bootstrap-plan.md`
3. Project-local Vulkan -> Metal integration research:
   `docs/research/2026-04-09-minecraft-26.2-vulkan-metal-research_cn.md`
4. Upstream reverse-engineering report:
   `~/docs/reverse/2026-04-09-minecraft-26.2-vulkan-research-report_cn.md`

When working on Minecraft integration points, use those docs as the source of truth before inventing a new path.

## Current Implementation Path

Build the project in these major slices:

1. Bootstrap, config persistence, diagnostics, and planning
2. Settings UI replacement with a `MetalExp`-owned graphics API option
3. Startup backend negotiation override driven by `MetalExp` config
4. Real `MetalBackend` introduction as a backend parallel to GL and Vulkan
5. Native bridge bring-up for Cocoa window extraction, `CAMetalLayer`, device, queue, and drawable lifecycle
6. Surface/device/resource core
7. Shader toolchain and pipeline compilation
8. Rendering viability for menu, world entry, resize, and shutdown

Do not skip directly to broad renderer work if the previous layer is still fake or only half-wired.

## Git And PR Rules

Every major feature must live on its own `dev/xxx` branch.

Examples of valid feature boundaries:

- `dev/bootstrap-diagnostics`
- `dev/settings-ui-overlay`
- `dev/backend-negotiation`
- future `dev/metal-backend-stub`
- future `dev/native-surface-bringup`

Examples of invalid boundaries:

- mixing settings UI replacement and startup negotiation in one branch
- mixing native bridge work and shader toolchain work in one branch
- using `codex/xxx` for new work in this repository

### Branch policy

- New feature branches must use the `dev/` prefix
- One branch should represent one functional boundary
- Stacked branches are acceptable when the dependency chain is real and explicit
- If work naturally splits into two reviewable features, create two branches

### Commit policy

- Do not make one giant commit for a whole feature branch
- Do not make commits so tiny that each one is meaningless noise
- Prefer 2-4 commits per feature branch when the work benefits from review checkpoints
- Each commit should still be coherent on its own

Good examples:

- add a preference model and tests
- wire a UI replacement mixin
- add negotiation resolver and tests

Bad examples:

- `wip`
- one commit containing settings UI, negotiation, native bridge, and docs

### PR policy

- When a feature branch is ready, push it and open a PR
- The PR base should match the real dependency chain
- If the feature depends on an unmerged branch, use a stacked PR against that branch
- After opening the PR, notify the user to review it

## Working Style

- Follow the current milestone and avoid overdesign
- Keep Minecraft-facing hooks thin and policy in plain Java where possible
- Prefer testable units before deep mixin work
- Verify claims with fresh `./gradlew test` and `./gradlew build` runs before saying work is complete
- If reverse-engineering is needed, use the existing Recaf/decompiled workflow instead of guessing

## Metal Integration Guidance

The point at which Metal is considered "really integrated" is not when docs mention it. It is when:

- `BackendKind.METAL` resolves to a real `MetalBackend`
- startup negotiation actually instantiates and tries `MetalBackend`
- strict/fallback behavior depends on real Metal backend creation results
- the backend exposes a valid device and surface path instead of being skipped up front

Until then, any Metal path is scaffolding, not completion.

## Notes For Future Agents

- Treat the design spec and research docs as constraints, not inspiration-only reading
- Preserve the repo's current feature boundary discipline
- If a worktree or branch is only a scratch area, do not treat it as the canonical feature branch
- Prefer adding or updating `docs/plans/` when a new major milestone starts
