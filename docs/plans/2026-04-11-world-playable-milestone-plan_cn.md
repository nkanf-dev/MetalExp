# World Playable Milestone Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Push the Metal backend from menu-only viability to a world-entry vertical slice by adding world-pass diagnostics, minimum depth/render-pass semantics, and the first strict world-path validation loop.

**Architecture:** Keep Minecraft-facing hooks thin by concentrating world-pass census, target validation, and fail-fast behavior in the Java backend layer first. Only extend the native bridge for semantics that are already stable and on the critical path, so the first playable-world loop is driven by observable blockers instead of silent rendering regressions.

**Tech Stack:** Fabric Loom, Java 25, JUnit Jupiter, JNI, Objective-C, Metal, AppKit, QuartzCore

---

## File Structure

### Existing Files To Modify

- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalTexture.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalTextureView.java`
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/MetalBridge.java`
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/NativeMetalBridge.java`
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

### New Client Sources

- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalPassAttachment.java`
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalPassContext.java`
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalUnsupportedPassException.java`

### New Native Sources

- Create: `native/src/metalexp_world_pipeline.m`

### Native Sources To Modify

- Modify: `native/src/metalexp_common.h`
- Modify: `native/src/metalexp_jni.m`

### New Docs To Create

- Create: `docs/logs/2026-04-11-world-pass-census-log_cn.md`

## Conventions

- Keep strict fail-fast behavior for unsupported non-critical world passes; do not add silent world-path fallbacks.
- Add tests before implementation for every new Java-side behavior.
- Keep depth support limited to the minimum semantics needed to validate world main-path rendering: attachment presence, clear, compare/write flags, and diagnostics.
- Do not widen native entrypoints until Java-side pass census proves the path is on the world critical path.

## Task 1: Add World Pass Context And Structured Unsupported-Pass Errors

**Files:**
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalPassAttachment.java`
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalPassContext.java`
- Create: `src/client/java/dev/nkanf/metalexp/client/backend/MetalUnsupportedPassException.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

- [ ] **Step 1: Write a failing test that requires unsupported world passes to expose structured context**

```java
@Test
void unsupportedWorldPipelineReportsStructuredContext() {
	SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
	MetalDeviceBackend backend = new MetalDeviceBackend(
		bridge,
		new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
	);
	MetalTexture color = (MetalTexture) backend.createTexture("world-color", 15, GpuFormat.RGBA8_UNORM, 64, 64, 1, 1);
	MetalTexture depth = (MetalTexture) backend.createTexture("world-depth", 9, GpuFormat.D32_FLOAT, 64, 64, 1, 1);
	MetalTextureView colorView = new MetalTextureView(color, 0, 1);
	MetalTextureView depthView = new MetalTextureView(depth, 0, 1);
	MetalCommandEncoderBackend encoder = (MetalCommandEncoderBackend) backend.createCommandEncoder();
	RenderPipeline unsupportedPipeline = RenderPipelines.ENTITY_SOLID;

	MetalUnsupportedPassException exception = assertThrows(
		MetalUnsupportedPassException.class,
		() -> {
			RenderPassBackend pass = encoder.createRenderPass(() -> "world-pass", colorView, OptionalInt.empty(), depthView, OptionalDouble.empty());
			pass.setPipeline(unsupportedPipeline);
			pass.draw(0, 3);
		}
	);

	assertTrue(exception.getMessage().contains("minecraft:pipeline/entity_solid"));
	assertTrue(exception.getMessage().contains("colorFormat=RGBA8_UNORM"));
	assertTrue(exception.getMessage().contains("depthFormat=D32_FLOAT"));
	assertTrue(exception.getMessage().contains("drawType=draw"));
}
```

- [ ] **Step 2: Run the targeted test to confirm the new diagnostic types do not exist yet**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest.unsupportedWorldPipelineReportsStructuredContext`

Expected: FAIL with compilation errors for missing `MetalPassAttachment`, `MetalPassContext`, or `MetalUnsupportedPassException`.

- [ ] **Step 3: Add focused pass-context model types**

```java
package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.GpuFormat;

record MetalPassAttachment(
	String label,
	GpuFormat format,
	int width,
	int height,
	int mipLevel,
	boolean nativeBacked
) {
	static MetalPassAttachment from(GpuTextureView textureView) {
		MetalTexture texture = (MetalTexture) textureView.texture();
		return new MetalPassAttachment(
			texture.getLabel(),
			texture.getFormat(),
			texture.getWidth(textureView.baseMipLevel()),
			texture.getHeight(textureView.baseMipLevel()),
			textureView.baseMipLevel(),
			texture.hasNativeTextureHandle()
		);
	}

	String describe() {
		return "label=" + this.label
			+ ", format=" + this.format
			+ ", size=" + this.width + "x" + this.height
			+ ", mipLevel=" + this.mipLevel
			+ ", nativeBacked=" + this.nativeBacked;
	}
}
```

```java
package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.vertex.VertexFormat;

record MetalPassContext(
	String passLabel,
	String pipelineLocation,
	MetalPassAttachment colorAttachment,
	MetalPassAttachment depthAttachment,
	String drawType,
	VertexFormat vertexFormat,
	VertexFormat.IndexType indexType,
	boolean nativePath
) {
	String describe() {
		return "passLabel=" + this.passLabel
			+ ", pipeline=" + this.pipelineLocation
			+ ", colorFormat=" + this.colorAttachment.format()
			+ ", depthFormat=" + (this.depthAttachment == null ? "NONE" : this.depthAttachment.format())
			+ ", drawType=" + this.drawType
			+ ", vertexFormat=" + this.vertexFormat
			+ ", indexType=" + this.indexType
			+ ", nativePath=" + this.nativePath;
	}
}
```

```java
package dev.nkanf.metalexp.client.backend;

final class MetalUnsupportedPassException extends UnsupportedOperationException {
	MetalUnsupportedPassException(String reason, MetalPassContext context) {
		super(reason + " [" + context.describe() + "]");
	}
}
```

- [ ] **Step 4: Thread pass context into render-pass creation and fail-fast helpers**

```java
// src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java
@Override
public RenderPassBackend createRenderPass(Supplier<String> supplier, GpuTextureView colorView, OptionalInt optionalInt, GpuTextureView depthView, OptionalDouble optionalDouble) {
	return new MetalRenderPassBackend(this, supplier.get(), colorView, depthView);
}
```

```java
// src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java
private final String passLabel;
private final MetalPassAttachment colorAttachment;
private final MetalPassAttachment depthAttachment;
private final MetalTexture depthTarget;

MetalRenderPassBackend(MetalCommandEncoderBackend commandEncoderBackend, String passLabel, GpuTextureView colorTextureView, GpuTextureView depthTextureView) {
	this.commandEncoderBackend = commandEncoderBackend;
	this.passLabel = passLabel;
	this.colorAttachment = MetalPassAttachment.from(colorTextureView);
	this.depthAttachment = depthTextureView == null ? null : MetalPassAttachment.from(depthTextureView);
	this.depthTarget = depthTextureView == null ? null : (MetalTexture) depthTextureView.texture();
}

private MetalUnsupportedPassException unsupportedPass(String reason, String drawType) {
	return new MetalUnsupportedPassException(
		reason,
		new MetalPassContext(
			this.passLabel,
			this.pipeline == null ? "<unset>" : this.pipeline.getLocation().toString(),
			this.colorAttachment,
			this.depthAttachment,
			drawType,
			this.pipeline == null ? null : this.pipeline.getVertexFormat(),
			this.indexType,
			this.colorTarget.hasNativeTextureHandle()
		)
	);
}
```

- [ ] **Step 5: Re-run the targeted test and then the whole backend test class**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest.unsupportedWorldPipelineReportsStructuredContext`

Expected: PASS

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`

Expected: PASS

- [ ] **Step 6: Commit the diagnostics checkpoint**

```bash
git add \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalPassAttachment.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalPassContext.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalUnsupportedPassException.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java \
  src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java
git commit -m "feat: add structured metal world pass diagnostics"
```

## Task 2: Carry Depth Attachment Metadata Through The Java Backend

**Files:**
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalTextureView.java`
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

- [ ] **Step 1: Write a failing test that requires depth attachment metadata to be preserved**

```java
@Test
void renderPassCarriesDepthAttachmentMetadata() {
	MetalDeviceBackend backend = new MetalDeviceBackend(
		new SurfaceTrackingBridge(),
		new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
	);
	MetalTexture color = (MetalTexture) backend.createTexture("world-color", 15, GpuFormat.RGBA8_UNORM, 32, 32, 1, 1);
	MetalTexture depth = (MetalTexture) backend.createTexture("world-depth", 9, GpuFormat.D32_FLOAT, 32, 32, 1, 1);
	MetalTextureView colorView = new MetalTextureView(color, 0, 1);
	MetalTextureView depthView = new MetalTextureView(depth, 0, 1);
	MetalRenderPassBackend pass = (MetalRenderPassBackend) new MetalCommandEncoderBackend(backend.surfaceLease())
		.createRenderPass(() -> "depth-pass", colorView, OptionalInt.empty(), depthView, OptionalDouble.empty());

	assertEquals(GpuFormat.RGBA8_UNORM, pass.colorAttachment().format());
	assertEquals(GpuFormat.D32_FLOAT, pass.depthAttachment().format());
	assertEquals("depth-pass", pass.passLabel());
}
```

- [ ] **Step 2: Run the targeted test and verify the new accessors/context wiring is missing**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest.renderPassCarriesDepthAttachmentMetadata`

Expected: FAIL because `MetalRenderPassBackend` does not expose depth metadata yet.

- [ ] **Step 3: Add package-visible accessors for the threaded metadata**

```java
// src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java
String passLabel() {
	return this.passLabel;
}

MetalPassAttachment colorAttachment() {
	return this.colorAttachment;
}

MetalPassAttachment depthAttachment() {
	return this.depthAttachment;
}
```

- [ ] **Step 4: Make the depth-aware constructor the single render-pass entrypoint**

```java
// src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java
@Override
public RenderPassBackend createRenderPass(Supplier<String> supplier, GpuTextureView colorView, OptionalInt optionalInt) {
	return new MetalRenderPassBackend(this, supplier.get(), colorView, null);
}

@Override
public RenderPassBackend createRenderPass(Supplier<String> supplier, GpuTextureView colorView, OptionalInt optionalInt, GpuTextureView depthView, OptionalDouble optionalDouble) {
	return new MetalRenderPassBackend(this, supplier.get(), colorView, depthView);
}
```

- [ ] **Step 5: Re-run the targeted test and the full backend test suite**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest.renderPassCarriesDepthAttachmentMetadata`

Expected: PASS

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`

Expected: PASS

- [ ] **Step 6: Commit the depth-metadata checkpoint**

```bash
git add \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalTextureView.java \
  src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java
git commit -m "feat: thread metal depth attachment metadata"
```

## Task 3: Implement Minimum Depth Clear Semantics In Java-Side Texture Storage

**Files:**
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalTexture.java`
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

- [ ] **Step 1: Write a failing test for depth clear**

```java
@Test
void clearDepthTextureWritesFloatDepthValues() {
	MetalDeviceBackend backend = new MetalDeviceBackend(
		new SurfaceTrackingBridge(),
		new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
	);
	MetalTexture depth = (MetalTexture) backend.createTexture("depth", 9, GpuFormat.D32_FLOAT, 4, 4, 1, 1);
	MetalCommandEncoderBackend encoder = (MetalCommandEncoderBackend) backend.createCommandEncoder();

	encoder.clearDepthTexture(depth, 0.25D);
	ByteBuffer snapshot = depth.snapshotStorage(0).order(ByteOrder.nativeOrder());

	for (int i = 0; i < 16; i++) {
		assertEquals(0.25F, snapshot.getFloat(i * Float.BYTES), 0.0001F);
	}
}
```

- [ ] **Step 2: Run the targeted test to verify `clearDepthTexture()` is still a no-op**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest.clearDepthTextureWritesFloatDepthValues`

Expected: FAIL because the snapshot still contains zeroes.

- [ ] **Step 3: Add depth fill helpers to `MetalTexture`**

```java
// src/client/java/dev/nkanf/metalexp/client/backend/MetalTexture.java
void fillDepth(int mipLevel, float depthValue) {
	if (getFormat() != GpuFormat.D32_FLOAT) {
		throw new IllegalStateException("Metal depth fill requires a D32_FLOAT texture.");
	}

	ByteBuffer destination = this.mipStorage[mipLevel].duplicate().order(ByteOrder.nativeOrder());
	int pixelCount = getWidth(mipLevel) * getHeight(mipLevel) * getDepthOrLayers();
	for (int pixel = 0; pixel < pixelCount; pixel++) {
		destination.putFloat(pixel * Float.BYTES, depthValue);
	}
}
```

- [ ] **Step 4: Implement the minimum encoder-side depth clear**

```java
// src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java
@Override
public void clearDepthTexture(GpuTexture gpuTexture, double value) {
	((MetalTexture) gpuTexture).fillDepth(0, (float) value);
}
```

- [ ] **Step 5: Run the targeted test and the full backend suite**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest.clearDepthTextureWritesFloatDepthValues`

Expected: PASS

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`

Expected: PASS

- [ ] **Step 6: Commit the depth-clear checkpoint**

```bash
git add \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalCommandEncoderBackend.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalTexture.java \
  src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java
git commit -m "feat: add metal depth clear storage semantics"
```

## Task 4: Add The First Depth-Aware Native World Pipeline Entry Point

**Files:**
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/MetalBridge.java`
- Modify: `src/main/java/dev/nkanf/metalexp/bridge/NativeMetalBridge.java`
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
- Create: `native/src/metalexp_world_pipeline.m`
- Modify: `native/src/metalexp_common.h`
- Modify: `native/src/metalexp_jni.m`
- Modify: `src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java`

- [ ] **Step 1: Write a failing bridge test that requires a depth-aware world draw delegate**

```java
@Test
void opaqueWorldGeometryUsesDedicatedNativeDelegate() {
	SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
	MetalDeviceBackend backend = new MetalDeviceBackend(
		bridge,
		new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
	);
	MetalTexture color = (MetalTexture) backend.createTexture("world-color", 15, GpuFormat.RGBA8_UNORM, 32, 32, 1, 1);
	MetalTexture depth = (MetalTexture) backend.createTexture("world-depth", 9, GpuFormat.D32_FLOAT, 32, 32, 1, 1);
	RenderPassBackend pass = ((MetalCommandEncoderBackend) backend.createCommandEncoder())
		.createRenderPass(() -> "world-opaque", new MetalTextureView(color, 0, 1), OptionalInt.empty(), new MetalTextureView(depth, 0, 1), OptionalDouble.empty());

	pass.setPipeline(RenderPipelines.ENTITY_SOLID);
	pass.draw(0, 3);

	assertEquals(1, bridge.nativeWorldDrawCount);
}
```

- [ ] **Step 2: Run the targeted test to confirm there is no native world entrypoint yet**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest.opaqueWorldGeometryUsesDedicatedNativeDelegate`

Expected: FAIL with compilation errors because the bridge test double does not expose `nativeWorldDrawCount` and the Java bridge has no `drawWorldPass(...)` entrypoint yet.

- [ ] **Step 3: Extend the bridge with a dedicated world draw method**

```java
// src/main/java/dev/nkanf/metalexp/bridge/MetalBridge.java
default void drawWorldPass(
	long nativeCommandContextHandle,
	long nativeColorTextureHandle,
	long nativeDepthTextureHandle,
	int pipelineKind,
	ByteBuffer vertexData,
	int vertexStride,
	int vertexCount
) {
	throw new UnsupportedOperationException("Metal world draw is not implemented by this bridge.");
}
```

```objc
// native/src/metalexp_common.h
void metalexp_draw_world_pass(
	jlong native_command_context_handle,
	jlong native_color_texture_handle,
	jlong native_depth_texture_handle,
	jint pipeline_kind,
	const void *vertex_bytes,
	jlong vertex_capacity,
	jint vertex_stride,
	jint vertex_count
);
```

- [ ] **Step 4: Add the first native implementation in a separate file**

```objc
// native/src/metalexp_world_pipeline.m
#include "metalexp_common.h"

void metalexp_draw_world_pass(
	jlong native_command_context_handle,
	jlong native_color_texture_handle,
	jlong native_depth_texture_handle,
	jint pipeline_kind,
	const void *vertex_bytes,
	jlong vertex_capacity,
	jint vertex_stride,
	jint vertex_count
) {
	@throw [NSException exceptionWithName:@"MetalExpWorldPipelineException"
		reason:@"Metal world pipeline entrypoint is wired, but no world pipeline kinds are implemented yet."
		userInfo:nil];
}
```

- [ ] **Step 5: Route the first world-family branch through the new delegate**

```java
// src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java
if (WORLD_OPAQUE_LOCATIONS.contains(this.pipeline.getLocation().toString())) {
	if (this.depthAttachment == null) {
		throw unsupportedPass("Metal world opaque draw requires a depth attachment.", "draw");
	}
	withCommandContext(handle -> this.colorTarget.metalBridge().drawWorldPass(
		handle,
		this.colorTarget.nativeTextureHandle(),
		this.depthTarget.nativeTextureHandle(),
		PIPELINE_KIND_WORLD_OPAQUE,
		vertexStorage,
		this.pipeline.getVertexFormat().getVertexSize(),
		vertexCount
	));
	return;
}
```

- [ ] **Step 6: Update the test double, rerun the backend suite, and commit**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`

Expected: PASS with the bridge test double recording the world delegate call or the explicit native exception path.

```bash
git add \
  src/main/java/dev/nkanf/metalexp/bridge/MetalBridge.java \
  src/main/java/dev/nkanf/metalexp/bridge/NativeMetalBridge.java \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java \
  native/src/metalexp_common.h \
  native/src/metalexp_jni.m \
  native/src/metalexp_world_pipeline.m \
  src/test/java/dev/nkanf/metalexp/client/backend/MetalDeviceBackendTest.java
git commit -m "feat: wire first depth-aware metal world draw path"
```

## Task 5: Run The First World-Pass Census In Client And Capture The Blocking Set

**Files:**
- Modify: `src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java`
- Create: `docs/logs/2026-04-11-world-pass-census-log_cn.md`

- [ ] **Step 1: Add one-shot world-pass census logging behind the existing backend logger**

```java
private void logWorldPass(MetalPassContext context) {
	MetalExpClient.LOGGER.info("[MetalExp][world-pass] {}", context.describe());
}
```

- [ ] **Step 2: Run the client, enter a singleplayer world, and stop on the first unsupported pass**

Run: `./gradlew runClient`

Expected:
- Main menu opens on Metal backend
- Entering a world either succeeds to the first rendered frames or fails fast with a `MetalUnsupportedPassException`
- The log contains one or more `[MetalExp][world-pass]` entries with structured context

- [ ] **Step 3: Record the first blocker set in a dedicated log doc**

```markdown
# 2026-04-11 世界主路径 pass census 日志

## 首轮阻塞项

1. `minecraft:pipeline/entity_solid`
   - colorFormat=`RGBA8_UNORM`
   - depthFormat=`D32_FLOAT`
   - drawType=`drawIndexed`
   - 结论：属于世界主路径，下一轮优先级最高
```

- [ ] **Step 4: Re-run the backend tests and full build after the census instrumentation**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`

Expected: PASS

Run: `./gradlew build`

Expected: PASS

- [ ] **Step 5: Commit the census checkpoint**

```bash
git add \
  src/client/java/dev/nkanf/metalexp/client/backend/MetalRenderPassBackend.java \
  docs/logs/2026-04-11-world-pass-census-log_cn.md
git commit -m "docs: capture initial metal world pass census"
```

## Task 6: Verify The Milestone Gate And Prepare The Next Plan

**Files:**
- Modify: `docs/specs/2026-04-11-world-playable-milestone-design_cn.md`
- Modify: `docs/logs/2026-04-11-world-pass-census-log_cn.md`
- Create: `docs/plans/2026-04-11-world-opaque-family-plan_cn.md`

- [ ] **Step 1: Compare the collected blocker set against the milestone spec**

```markdown
## Spec Check

- [x] world pass census exists
- [x] unsupported pass errors now carry structured context
- [x] depth attachment metadata is observable
- [ ] first opaque world family renders correctly
- [ ] short playable session achieved
```

- [ ] **Step 2: Write the follow-up plan for the first opaque world family only**

```markdown
# World Opaque Family Implementation Plan

**Goal:** Implement the first opaque world geometry family proven by census to block world entry.
```

- [ ] **Step 3: Run final verification for this plan checkpoint**

Run: `./gradlew test --tests dev.nkanf.metalexp.client.backend.MetalDeviceBackendTest`

Expected: PASS

Run: `./gradlew build`

Expected: PASS

- [ ] **Step 4: Commit the handoff docs**

```bash
git add \
  docs/specs/2026-04-11-world-playable-milestone-design_cn.md \
  docs/logs/2026-04-11-world-pass-census-log_cn.md \
  docs/plans/2026-04-11-world-opaque-family-plan_cn.md
git commit -m "docs: hand off first metal world blocker set"
```
