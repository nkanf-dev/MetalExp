# Native Atlas Blit And Bridge Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the CPU-side `animate_sprite_blit` atlas composition path with a strict native Metal path and split the monolithic Objective-C bridge into focused native modules.

**Architecture:** Keep Java-side Minecraft hooks thin by decoding `SpriteAnimationInfo` in `MetalRenderPassBackend` and delegating atlas updates to a dedicated native bridge entrypoint. Split the current single native bridge file into shared definitions plus focused surface, texture, GUI pipeline, sprite blit, and JNI files so future performance-sensitive work can stay native without growing one monolith.

**Tech Stack:** Fabric Loom, Java 25, JUnit Jupiter, JNI, Objective-C, Metal, AppKit, QuartzCore

---

## File Structure

### Existing Files To Modify

- Modify: `build.gradle`
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/MetalBridge.java`
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/NativeMetalBridge.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

### Native Sources To Create

- Create: `native/src/metalexp_common.h`
- Create: `native/src/metalexp_surface.m`
- Create: `native/src/metalexp_texture.m`
- Create: `native/src/metalexp_gui_pipeline.m`
- Create: `native/src/metalexp_sprite_blit.m`
- Create: `native/src/metalexp_jni.m`

### Native Sources To Remove

- Delete: `native/src/metalexp_bridge_probe.m`

## Task 1: Split Native Bridge Build Inputs

**Files:**
- Modify: `build.gradle`
- Create: `native/src/metalexp_common.h`
- Create: `native/src/metalexp_jni.m`
- Delete: `native/src/metalexp_bridge_probe.m`

- [ ] Move shared native declarations out of the monolithic file into `metalexp_common.h`.
- [ ] Update `buildNativeMacosBridge` so it compiles all native `.m` sources from `native/src/` instead of a single file input.
- [ ] Preserve the existing AppKit/Foundation/Metal/QuartzCore link flags.

## Task 2: Rehome Existing Surface, Texture, And GUI Draw Logic

**Files:**
- Create: `native/src/metalexp_surface.m`
- Create: `native/src/metalexp_texture.m`
- Create: `native/src/metalexp_gui_pipeline.m`
- Create: `native/src/metalexp_jni.m`

- [ ] Move surface probe/bootstrap/configure/acquire/present/surface blit helpers into `metalexp_surface.m`.
- [ ] Move native texture create/upload/release helpers into `metalexp_texture.m`.
- [ ] Move GUI shader source, pipeline creation, sampler creation, and `drawGuiPass` into `metalexp_gui_pipeline.m`.
- [ ] Keep `metalexp_jni.m` as thin JNI forwarding only.

## Task 3: Add Strict Native Animated Sprite Blit

**Files:**
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/MetalBridge.java`
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/NativeMetalBridge.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
- Create: `native/src/metalexp_sprite_blit.m`
- Modify: `native/src/metalexp_common.h`
- Modify: `native/src/metalexp_jni.m`

- [ ] Add a dedicated bridge method for animated sprite blits into atlas textures.
- [ ] In `MetalRenderPassBackend`, require native-backed source and target textures for `minecraft:pipeline/animate_sprite_blit`; throw immediately if either side lacks a native handle.
- [ ] Decode `SpriteAnimationInfo` once in Java, clip the destination rectangle to atlas bounds, and pass only the required native arguments.
- [ ] Implement the actual blit in `metalexp_sprite_blit.m` using a native render pass that samples the source texture and writes directly into the destination atlas texture region.
- [ ] Do not keep or reintroduce any CPU atlas composition fallback.

## Task 4: Refresh Tests Around Native Dispatch

**Files:**
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

- [ ] Update the bridge test double to record animated sprite blit calls and parameters.
- [ ] Replace the old CPU atlas content assertion with a native dispatch assertion for `ANIMATE_SPRITE_BLIT`.
- [ ] Add a strict failure test that proves `ANIMATE_SPRITE_BLIT` throws when the source or target texture lacks a native handle.

## Task 5: Verify Regression Coverage

**Files:**
- Test: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

- [ ] Run `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`
- [ ] Run `./gradlew build`
- [ ] If the native split changes compiler behavior, fix build inputs before closing the task.
