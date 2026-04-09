# MetalExp Design Spec

## Summary

`MetalExp` is an experimental public Fabric mod project that aims to add a native Metal rendering backend to modern Minecraft snapshot builds on macOS.

The project is explicitly **not** a lightweight compatibility patch. It is a backend program delivered in mod form:

- the user-facing graphics API setting is replaced by a `MetalExp`-owned setting
- the startup backend negotiation path is overridden by mixins
- the renderer itself is implemented as a first-class backend stack
- the project includes a native macOS bridge for Cocoa and Metal APIs

The first public target is the modern Blaze3D architecture line represented by Minecraft `26.2-snapshot-1`, with the expectation that snapshot drift will require active maintenance.

## Goals

- Deliver a real native Metal backend, not a Vulkan-to-Metal translation layer
- Keep the project structurally close to a typical Fabric mod project so the Minecraft-facing layer remains approachable
- Use the new backend seams in Blaze3D instead of scattering rendering hacks across unrelated codepaths
- Provide a clean user-visible graphics API choice: `Metal`, `Vulkan`, or `OpenGL`
- Support experimental public distribution to a small technical audience on macOS
- Build strong diagnostics and failure reporting into the project from the start

## Non-Goals

- Broad compatibility with other rendering mods in the first release line
- Immediate support for non-macOS platforms
- Immediate feature parity with every edge case in Mojang's OpenGL and Vulkan backends
- A direct dynamic extension of Mojang's `PreferredGraphicsApi` enum
- A MoltenVK-backed implementation presented as native Metal

## Product Positioning

`MetalExp` is intended for technically comfortable users who are willing to run a snapshot-targeted experimental renderer and provide logs, diagnostics, and issue reports.

The project should present itself honestly:

- macOS only
- snapshot-sensitive
- renderer-mod incompatible unless explicitly documented otherwise
- diagnostics-first support model

## Background

Research on Minecraft `26.2-snapshot-1` shows that Blaze3D is now structured around an explicit backend boundary:

- `GpuBackend`
- `GpuDeviceBackend`
- `GpuSurfaceBackend`
- `CommandEncoderBackend`
- `RenderPassBackend`

This architecture change makes a native Metal backend technically plausible in a way that older OpenGL-centered versions were not. The major cost has not disappeared; it has moved into backend implementation, shader translation, resource binding, synchronization mapping, and long-term maintenance.

See the project-local research report:

- `docs/research/2026-04-09-minecraft-26.2-vulkan-metal-research_cn.md`

## User Experience

### Graphics API Selection

`MetalExp` replaces the original graphics API option with a project-owned option. The user should see a direct and explicit choice:

- `Metal`
- `Vulkan`
- `OpenGL`

The project does **not** attempt to dynamically inject a `METAL` constant into Mojang's enum. Instead:

- the original option is removed or hidden from the effective settings UI
- `MetalExp` inserts its own replacement option
- the real source of truth is stored in `MetalExp` configuration
- startup backend negotiation reads the `MetalExp` configuration and ignores the original option for actual backend selection

### Failure Modes

The project must support at least two runtime behaviors:

- `fallback` mode: if `Metal` fails, continue with `Vulkan` and then `OpenGL`
- `strict` mode: if `Metal` fails, abort with a precise error

This split is essential. `fallback` is friendlier for users. `strict` is better for development, triage, and reproducibility.

## Architecture

### Top-Level Layout

The project layout should remain recognizable as a normal Fabric mod while reserving dedicated space for the renderer program:

- `src/main` and `src/client`
  - Fabric entrypoints
  - mixins
  - mod resources
  - configuration wiring
- `shared/`
  - backend selection models
  - diagnostics payloads
  - capability summaries
  - shared error types
- `native/`
  - Cocoa bridge
  - `CAMetalLayer` management
  - Metal object lifecycle
  - JNI boundary
- `docs/`
  - design specs
  - research
  - implementation plans
  - debugging notes

### Logical Modules

#### 1. Bootstrap Layer

Responsibilities:

- Fabric initialization
- client-only initialization
- configuration loading
- diagnostics initialization
- settings UI replacement
- startup backend negotiation hooks

This layer should stay small. It should not contain renderer business logic.

#### 2. Shared Model Layer

Responsibilities:

- `BackendKind` model such as `METAL`, `VULKAN`, `OPENGL`, optional future `AUTO`
- diagnostics schemas
- capability summaries
- backend failure and fallback descriptions
- shared configuration DTOs

This layer exists to prevent the Fabric bootstrap layer and the renderer backend from tightly coupling to each other.

#### 3. Java Backend Layer

Responsibilities:

- `MetalBackend`
- `MetalDevice`
- `MetalGpuSurface`
- `MetalCommandEncoder`
- `MetalRenderPass`
- `MetalRenderPipeline`
- `MetalGpuBuffer`
- `MetalGpuTexture`
- `MetalGpuTextureView`
- `MetalGpuSampler`

This layer must speak Blaze3D semantics upward and native bridge semantics downward.

#### 4. Shader/Toolchain Layer

Responsibilities:

- shader source lookup
- preprocessing and define injection
- GLSL to SPIR-V compilation
- SPIR-V reflection
- resource layout construction
- SPIR-V to MSL translation
- Metal shader library and function creation
- shader and pipeline cache keys

This is one of the two highest-risk parts of the project and must remain structurally isolated.

#### 5. Native Bridge Layer

Responsibilities:

- GLFW window to Cocoa window/view extraction
- `CAMetalLayer` attachment and management
- `MTLDevice` creation
- `MTLCommandQueue` creation
- library compilation and function lookup
- pipeline creation
- drawable acquisition
- command buffer submission
- `presentDrawable`

This layer is a formal subsystem, not a bag of helper calls.

## Minecraft Integration Strategy

### Why Not Extend Mojang's Enum

The project must not try to dynamically add `METAL` to `PreferredGraphicsApi`. That would create avoidable fragility around:

- enum constant arrays
- codecs and config serialization
- switch tables
- option instance behavior
- snapshot-to-snapshot maintenance

Replacing the setting at the UI and configuration layer produces cleaner product semantics with much less runtime fragility.

### Integration Entry Points

#### Settings Replacement

The settings UI path should:

- remove or suppress the original graphics API option
- insert a `MetalExp` graphics API option
- store the result in `MetalExp` configuration

This choice is the user-visible truth.

#### Startup Negotiation

At startup:

- read `MetalExp` configuration
- compute backend trial order
- attempt `MetalBackend` first when the user chose `Metal`
- apply `fallback` or `strict` failure policy
- if the user chose `Vulkan` or `OpenGL`, defer to the corresponding original backend path as much as possible

The goal is to own the negotiation boundary, not the entire startup process.

#### Window and Surface Bring-Up

The project should keep Minecraft's existing window ownership and host abstraction. `MetalExp` only takes over the backend-specific surface path:

- use the existing GLFW window handle
- unwrap the Cocoa window/view through the native bridge
- attach a `CAMetalLayer`
- build `MetalGpuSurface` around the drawable lifecycle

#### Renderer Initialization

Once `MetalBackend.createDevice(...)` succeeds:

- return a valid `GpuDevice` implementation
- pass it through normal renderer initialization
- avoid further Mixin-based rendering patches whenever possible

This boundary discipline is central to long-term maintainability.

## Shader and Resource Binding Design

### Chosen Route

The first implementation route is:

`GLSL -> SPIR-V -> MSL`

Rationale:

- aligns with the existing Mojang Vulkan-era shader story
- reuses the engine's current source and preprocessing expectations
- produces a stable intermediate representation
- is faster to validate than inventing a native GLSL-to-MSL compiler path

### Shader Pipeline Stages

#### 1. Source Stage

- load Mojang shader source from the game's shader source path
- inject the same or equivalent preprocessing defines used by the modern Vulkan path
- normalize stage-specific input before compilation

#### 2. IR Stage

- use `shaderc` to compile source into SPIR-V
- treat SPIR-V as the canonical intermediate representation for the first implementation line

#### 3. Reflection and Binding Stage

- inspect SPIR-V resources
- combine reflection results with Blaze3D pipeline declarations
- build an engine-owned resource layout model

#### 4. Metal Emission Stage

- translate SPIR-V to MSL
- compile Metal libraries and functions
- build structures that map the engine-owned layout model into concrete Metal slots

### Binding Model

The first implementation should use an engine-managed slot map instead of argument buffers.

Reasons:

- easier debugging
- simpler deterministic diagnostics
- lower first-version complexity
- closer alignment with the project's public experimental phase

The layout model must distinguish at least:

- uniform buffers
- sampled textures
- samplers
- texel buffers or equivalent typed buffer resources

The important architectural decision is that the project owns an intermediate layout model. It must not permanently encode Vulkan descriptor-set assumptions into the Metal backend.

### Pipeline Compilation

`MetalRenderPipeline` should own:

- vertex and fragment shader handles
- vertex format mapping
- color attachment formats
- depth and stencil state
- blend state
- raster state
- resource layout references
- stable cache keys

Pipeline caching must key on actual render state, not only shader identity.

## Command Encoding, Synchronization, and Presentation

### Core Principle

The project should not try to reproduce Vulkan behavior mechanically. It should preserve Blaze3D-visible semantics while allowing the Metal backend to absorb Metal-specific execution details.

### Command Encoding Model

`MetalCommandEncoder` should be the central frame execution coordinator. It should manage:

- command buffer lifecycle
- render encoder lifecycle
- blit encoder lifecycle
- pass boundaries
- draw submission
- output stage coordination
- present scheduling

The encoder should maintain a clear internal state machine so that runtime bugs are diagnosable.

### Render Pass Mapping

Because modern Minecraft is already using a dynamic-rendering style on the Vulkan path, `RenderPassBackend` maps naturally to a Metal render command encoder model.

The project should embrace that similarity:

- build pass descriptors from attachments and clear/load/store semantics
- open a render encoder
- bind pipeline and resources
- issue draw calls
- end the encoder cleanly

### Synchronization Strategy

The first implementation should be conservative:

- one main command buffer per frame
- render and blit work ordered explicitly
- drawable present encoded at the end of the frame
- correctness first, optimization later

The project must resist the temptation to overdesign synchronization in milestone one. A stable, understandable model is more important than chasing ideal parallelism too early.

### Surface and Output Path

The project should preserve the architecture where the engine renders primarily into internal render targets and only later emits the final image to the presentable surface.

Advantages:

- weaker coupling between scene rendering and surface presentation
- easier debugging and capture
- better alignment with resize and surface-rebuild scenarios
- closer conceptual fit to the direction already visible in Mojang's Vulkan path

## Native Bridge Requirements

The native bridge is required, not optional.

It must support:

- Cocoa interop for the GLFW-owned host window
- `CAMetalLayer` creation and lifecycle
- Metal device and queue creation
- shader library creation
- pipeline state creation
- drawable acquisition and presentation
- explicit error reporting back to Java

The project should prefer a small, disciplined JNI surface instead of leaking large quantities of low-level Objective-C interaction into Java.

## Configuration

The project should own its own configuration file rather than treating Mojang's original option as the runtime source of truth.

At minimum the config should include:

- selected backend: `metal`, `vulkan`, `opengl`
- failure policy: `fallback`, `strict`
- diagnostics level
- optional future capability gating toggles

The UI should reflect this config, and startup must consume it directly.

## Diagnostics and Supportability

Diagnostics are a core feature of the project.

The project must log or export:

- selected backend and fallback decisions
- macOS and GPU capability summary
- Metal device identity
- shader compilation phase failures
- pipeline compilation failures
- surface and drawable failures
- startup negotiation transcript

The public support model should require users to attach a diagnostics report for graphics issues whenever possible.

## Testing Strategy

### Unit Tests

Cover:

- configuration parsing
- backend selection logic
- fallback policy
- cache keys
- resource layout mapping

### Toolchain Tests

Cover:

- shader preprocessing
- GLSL to SPIR-V compilation
- SPIR-V to MSL translation
- reflection outputs
- layout generation

### Native Integration Tests

Cover:

- layer attachment
- device creation
- command queue creation
- minimal pipeline creation
- drawable acquisition and present

### Minecraft Integration Tests

Cover:

- startup to main menu
- entry into a world
- basic UI rendering
- basic 3D rendering
- resize handling
- clean shutdown

## Compatibility Policy

The first release line explicitly does not promise compatibility with other deep rendering mods.

The intended support target is:

- plain Fabric environment
- non-renderer mods only on a best-effort basis
- no first-release promise for Sodium, Iris, or equivalent render-stack rewrites

## Versioning and Maintenance Strategy

Because the target line is a snapshot, the codebase must separate:

- Minecraft-version-sensitive adaptation code
- backend core code
- shader/toolchain core code
- native bridge code

This allows snapshot retargeting work to concentrate in the adaptation layer instead of destabilizing the entire renderer.

## Milestones

### Milestone 1: Project Bootstrap

- stable Fabric scaffold
- project configuration model
- diagnostics bootstrap
- settings replacement scaffolding
- startup negotiation scaffolding

### Milestone 2: Native Surface Bring-Up

- Cocoa extraction
- `CAMetalLayer` attachment
- drawable acquisition
- clear and present

### Milestone 3: Resource Core

- buffers
- textures
- samplers
- uploads
- blit support
- basic frame lifecycle

### Milestone 4: Shader and Pipeline Core

- GLSL to SPIR-V to MSL pipeline
- reflection
- layout mapping
- first pipeline compile
- first successful draw

### Milestone 5: Rendering Viability

- menu rendering
- world entry
- depth and stencil support
- texture correctness
- basic ongoing stability

### Milestone 6: Experimental Public Release

- fallback and strict modes
- issue-oriented diagnostics export
- installation documentation
- version support statement
- triage templates

## Success Criteria

The project should be considered successful for its first meaningful release when all of the following are true:

- users can select `Metal` in the replaced graphics API UI
- the game can start on supported macOS systems and initialize a native Metal backend
- the client can reach the main menu and enter a world with visible rendering
- failures produce actionable diagnostics
- the backend is implemented through the backend boundary, not by broad rendering-time patching

## Risks

### High Risk

- shader binding and resource layout correctness
- Metal-side interpretation of explicit synchronization expectations
- snapshot-driven hook drift

### Medium Risk

- pipeline cache invalidation mistakes
- surface recreation during resize or presentation failures
- JNI boundary design becoming too chatty or too low-level

### Lower but Still Relevant Risk

- user confusion around support boundaries
- underestimated maintenance cost of public experimental releases

## Open Decisions

These are intentionally left as project decisions, not design gaps:

- whether to introduce `AUTO` in the first public configuration schema
- how much capability probing is exposed in the UI versus diagnostics only
- whether the native bridge is implemented in Objective-C, Objective-C++, or a mixed Objective-C plus C JNI boundary

None of these decisions block project bootstrap.
