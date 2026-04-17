# RenderPipeline-Driven Metal Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current name-matched Metal render path with a RenderPipeline-driven compiler/cache path so mainline world rendering stops depending on one-off special cases and can converge toward a real first-class Metal backend.

**Architecture:** Keep a small explicit list of true special passes (`lightmap`, `blur/*`, `animate_sprite_*`, `entity_outline_blit`, surface present) and move ordinary raster passes onto a shared Metal pipeline compilation path driven by `RenderPipeline` metadata. Derive topology, blend, depth, cull, write mask, shader family, and vertex layout from Mojang-visible pipeline state first; only keep pipeline-name hooks where shader family selection cannot yet be inferred from metadata alone.

**Tech Stack:** Java 25, Fabric, Blaze3D `RenderPipeline`, JNI, Objective-C/Metal, Gradle, JUnit 5

---

## Scope And Rules

- This plan supersedes the ad-hoc "black screen / patch one pipeline at a time" loop in [2026-04-12-black-screen-world-pipeline-plan_cn.md](/Users/nkanf/projs/MetalExp/docs/plans/2026-04-12-black-screen-world-pipeline-plan_cn.md) for the main raster path.
- No CPU fallback, no CPU simulation of unsupported main rendering passes.
- Critical path passes must either run on hardware or fail explicitly.
- Non-critical passes may be temporarily gated only if the plan explicitly marks them as temporary and logs the skip.
- Prefer modular native files over growing `metalexp_gui_pipeline.m` into a second monolith.
- Do not regress atlas upload, animated sprite blit, lightmap generation, or surface present.

## Target File Structure

### Java

- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
  - Remove most pipeline-name dispatch from ordinary raster draws.
  - Route generic raster draws through a descriptor/spec builder.
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpec.java`
  - Immutable description of a Metal-compilable raster pipeline.
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpecialCase.java`
  - Enumerates the small set of true special-pass escape hatches.
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpecs.java`
  - Builds `MetalRasterPipelineSpec` from `RenderPipeline`.
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/MetalBridge.java`
  - Replace the narrow GUI-only pipeline call shape with a generic raster pipeline spec handoff.
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/NativeMetalBridge.java`
  - Validate and forward the generic raster spec to JNI.
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`
  - Lock spec mapping, topology mapping, blend/depth mapping, and special-pass gating.

### Native

- Modify: `native/src/metalexp_common.h`
  - Add generic raster pipeline/spec enums and compact structs shared by JNI and Metal code.
- Modify: `native/src/metalexp_jni.m`
  - Forward generic raster specs instead of GUI-specialized arguments.
- Create: `native/src/metalexp_raster_pipeline.h`
  - Public native interface for raster pipeline compilation and draw encoding.
- Create: `native/src/metalexp_raster_pipeline.m`
  - Own pipeline cache, descriptor translation, and draw submission for ordinary raster passes.
- Create: `native/src/metalexp_raster_shaders.m`
  - Own shared shader source and shader family lookup for ordinary raster passes.
- Create: `native/src/metalexp_raster_state.m`
  - Map Mojang blend/depth/cull/write-mask/topology semantics to Metal state objects.
- Modify: `native/src/metalexp_gui_pipeline.m`
  - Shrink it back to true special cases only, or delete migrated shared logic from it.
- Modify: `native/src/metalexp_world_pipeline.m`
  - Align world terrain shader semantics with Mojang `terrain.vsh/fsh` and plug into shared state helpers where possible.

## Milestone Sequence

1. Build a shared Java-side raster spec model from `RenderPipeline`.
2. Thread that spec over the bridge into native without changing special-pass behavior.
3. Move ordinary raster draws to a native shared pipeline compiler/cache.
4. Align terrain/world semantics with Mojang shaders using the new path.
5. Re-enable or explicitly gate non-critical pipelines from the new shared path in descending gameplay importance.

---

### Task 1: Freeze The New Backend Contract In Tests

**Files:**
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

- [ ] **Step 1: Add failing tests for `RenderPipeline -> MetalRasterPipelineSpec` mapping**

Add test cases that lock:
- `RenderPipelines.SKY` => position-only vertex family, triangle-fan topology expanded to triangle draw, no sampler, no depth write, no cull
- `RenderPipelines.SUNRISE_SUNSET` => position-color vertex family, translucent blend
- `RenderPipelines.STARS` => position-only vertex family, overlay blend
- `RenderPipelines.CELESTIAL` => position-tex vertex family, overlay blend, sampler0 required
- `RenderPipelines.VIGNETTE` and `RenderPipelines.CROSSHAIR` => exact blend mappings preserved
- `RenderPipelines.LINES` / `LINES_DEPTH_BIAS` => line-family topology and line vertex layout

- [ ] **Step 2: Run the focused test target and confirm compile or assertion failure**

Run:

```bash
./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest
```

Expected:
- Fails because the current backend still exposes narrow GUI/world pipeline-kind routing.

- [ ] **Step 3: Extend the bridge test double to capture generic raster spec fields**

Capture:
- shader family
- vertex layout family
- primitive topology
- blend equations/factors
- compare op
- depth write flag
- cull flag
- write mask
- sampler requirements

- [ ] **Step 4: Re-run the focused test target**

Run:

```bash
./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest
```

Expected:
- Still failing on missing production wiring, but tests now express the target contract.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java
git commit -m "test: lock renderpipeline-driven metal raster contract"
```

---

### Task 2: Add A Java-Side Raster Pipeline Spec Builder

**Files:**
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpec.java`
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpecialCase.java`
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpecs.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`

- [ ] **Step 1: Add immutable spec types**

Define a compact spec model that carries only backend-relevant state, for example:

```java
record MetalRasterPipelineSpec(
	MetalRasterPipelineSpecialCase specialCase,
	ShaderFamily shaderFamily,
	VertexLayoutFamily vertexLayout,
	PrimitiveTopology topology,
	boolean indexed,
	boolean sampler0Required,
	boolean wantsDepthTexture,
	boolean cull,
	boolean depthWrite,
	CompareFunction depthCompare,
	BlendStateSpec blend,
	int colorWriteMask
) {}
```

- [ ] **Step 2: Implement Mojang-state-to-spec mapping**

Use:
- `RenderPipeline.getVertexFormat()`
- `RenderPipeline.getVertexFormatMode()`
- `RenderPipeline.getColorTargetState()`
- `RenderPipeline.getDepthStencilState()`
- `RenderPipeline.getSamplers()`
- `RenderPipeline.getPolygonMode()`
- `RenderPipeline.isCull()`

Keep name-based matching only for:
- `blur/*`
- `lightmap`
- `animate_sprite_*`
- `entity_outline_blit`
- temporary unsupported shader-family disambiguation

- [ ] **Step 3: Replace the current `resolvePipelineKind(...)` and topology helpers with spec creation**

`MetalRenderPassBackend` should:
- ask for `MetalRasterPipelineSpec`
- branch immediately if `specialCase != NONE`
- otherwise use the generic raster path

- [ ] **Step 4: Run the focused tests**

Run:

```bash
./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest
```

Expected:
- Java-side mapping tests pass or move to bridge/native failures.

- [ ] **Step 5: Commit**

```bash
git add \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpec.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpecialCase.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpecs.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java
git commit -m "refactor: derive metal raster specs from renderpipeline metadata"
```

---

### Task 3: Thread Generic Raster Specs Through The Bridge

**Files:**
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/MetalBridge.java`
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/NativeMetalBridge.java`
- Modify: `native/src/metalexp_common.h`
- Modify: `native/src/metalexp_jni.m`

- [ ] **Step 1: Replace the narrow GUI draw bridge call with a generic raster bridge call**

The Java bridge surface should stop assuming:
- GUI-only shader families
- ad-hoc integer pipeline kinds
- hand-picked special blend cases

Add a struct-like call shape carrying generic raster spec fields.

- [ ] **Step 2: Add native shared enums/structs**

In `metalexp_common.h`, define:

```c
typedef struct metalexp_raster_pipeline_desc {
	uint32_t shader_family;
	uint32_t vertex_layout;
	uint32_t primitive_topology;
	uint32_t color_write_mask;
	uint32_t flags;
	uint32_t depth_compare;
	uint32_t color_blend_op;
	uint32_t alpha_blend_op;
	uint32_t src_color_factor;
	uint32_t dst_color_factor;
	uint32_t src_alpha_factor;
	uint32_t dst_alpha_factor;
} metalexp_raster_pipeline_desc;
```

- [ ] **Step 3: Update JNI forwarding and validation**

Validate:
- direct buffers
- required sampler presence
- required depth attachment presence for depth-reading/writing passes
- legal write masks and compare ops

- [ ] **Step 4: Run focused tests**

Run:

```bash
./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest
```

Expected:
- Bridge-level tests pass.

- [ ] **Step 5: Commit**

```bash
git add \
  src/main/java/dev/nkanf/metalexp/bridge/MetalBridge.java \
  src/main/java/dev/nkanf/metalexp/bridge/NativeMetalBridge.java \
  native/src/metalexp_common.h \
  native/src/metalexp_jni.m
git commit -m "refactor: pass generic raster pipeline specs over metal bridge"
```

---

### Task 4: Build A Shared Native Raster Pipeline Cache

**Files:**
- Create: `native/src/metalexp_raster_pipeline.h`
- Create: `native/src/metalexp_raster_pipeline.m`
- Create: `native/src/metalexp_raster_state.m`
- Modify: `native/src/metalexp_gui_pipeline.m`

- [ ] **Step 1: Move ordinary pipeline cache logic out of `metalexp_gui_pipeline.m`**

Create one cache keyed by:
- device
- shader family
- vertex layout family
- topology
- blend state
- depth state
- cull
- write mask

- [ ] **Step 2: Add shared state mappers**

Implement helpers for:
- `BlendFactor -> MTLBlendFactor`
- `BlendOp -> MTLBlendOperation`
- `CompareOp -> MTLCompareFunction`
- color write mask mapping
- topology mapping
- cull mapping

- [ ] **Step 3: Make ordinary raster draw use the shared cache**

The generic draw path should no longer branch on:
- `sky`
- `stars`
- `celestial`
- `sunrise_sunset`
- `vignette`
- `crosshair`

unless a shader-family gap still forces temporary dispatch.

- [ ] **Step 4: Leave true special passes on explicit entrypoints**

Keep separate:
- animated sprite
- blur
- lightmap
- post blit
- surface present

- [ ] **Step 5: Re-run focused tests and native rebuild**

Run:

```bash
./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest
./gradlew buildNativeMacosBridge --rerun-tasks
```

Expected:
- Tests pass.
- Native bridge rebuild succeeds.

- [ ] **Step 6: Commit**

```bash
git add \
  native/src/metalexp_raster_pipeline.h \
  native/src/metalexp_raster_pipeline.m \
  native/src/metalexp_raster_state.m \
  native/src/metalexp_gui_pipeline.m
git commit -m "refactor: add shared metal raster pipeline cache"
```

---

### Task 5: Split Shared Shader Families And Vertex Layouts

**Files:**
- Create: `native/src/metalexp_raster_shaders.m`
- Modify: `native/src/metalexp_raster_pipeline.m`
- Modify: `native/src/metalexp_gui_pipeline.m`

- [ ] **Step 1: Define explicit shader families**

Start with:
- `position_color`
- `position_tex`
- `position`
- `position_tex_color`
- `line`
- `world_block`

Do not encode pipeline names into the shader family enum.

- [ ] **Step 2: Define explicit vertex layout families**

Start with:
- `POSITION`
- `POSITION_COLOR`
- `POSITION_TEX`
- `POSITION_TEX_COLOR`
- `POSITION_COLOR_NORMAL_LINE_WIDTH`
- `BLOCK`

- [ ] **Step 3: Move shared shader source out of `metalexp_gui_pipeline.m`**

`metalexp_raster_shaders.m` should own:
- shader library source assembly
- shader-family-to-function-name lookup
- vertex descriptor construction helpers

- [ ] **Step 4: Run focused tests and native rebuild**

Run:

```bash
./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest
./gradlew buildNativeMacosBridge --rerun-tasks
```

Expected:
- Tests and native build succeed.

- [ ] **Step 5: Commit**

```bash
git add \
  native/src/metalexp_raster_shaders.m \
  native/src/metalexp_raster_pipeline.m \
  native/src/metalexp_gui_pipeline.m
git commit -m "refactor: modularize shared metal raster shaders and layouts"
```

---

### Task 6: Align World Terrain With Mojang Shader Semantics

**Files:**
- Modify: `native/src/metalexp_world_pipeline.m`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

- [ ] **Step 1: Remove hand-rolled terrain color shortcuts that diverge from Mojang**

Align `metalexp_world_pipeline.m` with `terrain.vsh/fsh` for:
- `ChunkVisibility`
- fog application order
- alpha preservation
- cutout discard
- translucent output
- lightmap sampling

- [ ] **Step 2: Add diagnostics that sample per-pass center pixels only where needed**

Add targeted logs for:
- first terrain pass after sky
- last ordinary raster pass before present
- first pass that turns the center pixel into a flat fog color

Avoid permanent high-volume spam.

- [ ] **Step 3: Add regression tests for terrain semantic preservation**

Lock:
- solid terrain does not force alpha to an incorrect constant when it should preserve fragment alpha semantics
- translucent terrain uses blending rather than opaque overwrite
- world draw input packing still matches `ChunkSection`, `Globals`, and `Fog`

- [ ] **Step 4: Run focused tests**

Run:

```bash
./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest
```

Expected:
- Terrain regression tests pass.

- [ ] **Step 5: Commit**

```bash
git add \
  native/src/metalexp_world_pipeline.m \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java \
  src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java
git commit -m "fix: align metal terrain semantics with mojang shaders"
```

---

### Task 7: Reintroduce Non-Critical Pipelines By Priority

**Files:**
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpecs.java`
- Modify: `native/src/metalexp_raster_shaders.m`
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

- [ ] **Step 1: Re-enable line-family pipelines using the shared line shader family**

Target:
- `pipeline/lines`
- `pipeline/lines_translucent`
- `pipeline/lines_depth_bias`
- `pipeline/secondary_block_outline`

- [ ] **Step 2: Re-enable high-visibility entity and particle families**

Target:
- `entity_cutout`
- `entity_cutout_cull`
- `entity_translucent`
- `item_cutout`
- `opaque_particle`
- `eyes`

- [ ] **Step 3: For any remaining unsupported ordinary raster pipeline, fail explicitly with structured context**

No silent generic fallback.

- [ ] **Step 4: Run focused tests**

Run:

```bash
./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest
```

Expected:
- Tests cover either proper mapping or explicit structured failure.

- [ ] **Step 5: Commit**

```bash
git add \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRasterPipelineSpecs.java \
  native/src/metalexp_raster_shaders.m \
  src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java
git commit -m "feat: extend shared metal raster path to lines entities and particles"
```

---

### Task 8: End-To-End Verification And Plan Exit Criteria

**Files:**
- Modify: `docs/logs/2026-04-11-world-pass-census-log_cn.md`
- Create: `docs/logs/2026-04-12-renderpipeline-refactor-validation-log_cn.md`

- [ ] **Step 1: Run the focused backend tests**

Run:

```bash
./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest
```

Expected:
- PASS

- [ ] **Step 2: Run full build**

Run:

```bash
./gradlew build
```

Expected:
- PASS
- Existing native-access warnings may remain
- No new compilation or test failures

- [ ] **Step 3: Rebuild native bridge explicitly**

Run:

```bash
./gradlew buildNativeMacosBridge --rerun-tasks
```

Expected:
- PASS

- [ ] **Step 4: Run client world-entry validation**

Run:

```bash
./gradlew runClient --args='--quickPlaySingleplayer "New World"'
```

Expected:
- No black screen
- No full-screen flat fog-color overwrite
- `sky/sunrise/stars/celestial/terrain` all execute through the shared path or explicit special path by design
- Remaining unsupported ordinary raster pipelines fail explicitly with structured context instead of drawing corruption

- [ ] **Step 5: Capture visual evidence**

Run:

```bash
screencapture -x /tmp/metalexp-renderpipeline-refactor.png
```

Expected:
- Screenshot shows world geometry rather than a flat monotone screen with diagnostic HUD only

- [ ] **Step 6: Update the validation log**

Record:
- commands run
- screenshot path
- first remaining blocker, if any
- whether the blocker is on the shared raster path or a true special pass

- [ ] **Step 7: Commit**

```bash
git add \
  docs/logs/2026-04-11-world-pass-census-log_cn.md \
  docs/logs/2026-04-12-renderpipeline-refactor-validation-log_cn.md
git commit -m "docs: record renderpipeline-driven metal validation results"
```

---

## Exit Criteria

- Ordinary raster passes are no longer primarily selected by pipeline location string.
- `MetalRenderPassBackend` chooses between:
  - true special-pass path
  - shared raster path
  - explicit structured unsupported failure
- Native ordinary raster draws go through a shared pipeline cache keyed by compiled state, not by hand-authored GUI/world buckets.
- The world can enter without a black screen or a full-screen flat fog-color overwrite.
- Remaining rendering blockers are isolated to named unsupported families with structured diagnostics.

## Explicit Non-Goals For This Plan

- Full shader-toolchain replacement to automatic GLSL -> SPIR-V -> MSL compilation
- Argument-buffer or bindless redesign
- Final performance tuning to 120 FPS+
- Broad renderer-mod compatibility

## Self-Review

- Spec coverage:
  - Shared `RenderPipeline -> backend pipeline` compilation path: covered by Tasks 2-5.
  - Keep true special passes explicit: covered by Tasks 2, 4, and 8.
  - Native modularization: covered by Tasks 4 and 5.
  - No CPU fallback and explicit failure: covered by Tasks 6-8.
- Placeholder scan:
  - No `TODO`, `TBD`, or deferred unnamed steps remain.
- Type consistency:
  - Plan consistently uses `MetalRasterPipelineSpec`, shared raster path, special-pass path, and terrain alignment terminology.
