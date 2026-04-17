package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.nkanf.metalexp.bridge.MetalBridge;
import dev.nkanf.metalexp.bridge.MetalBridgeProbe;
import dev.nkanf.metalexp.bridge.MetalBridgeProbeStatus;
import dev.nkanf.metalexp.bridge.MetalHostSurfaceBootstrap;
import dev.nkanf.metalexp.bridge.NativeMetalBridge;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.shaders.UniformType;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetalDeviceBackendTest {
	@Test
	void exposesBootstrapDeviceInfoAndDescriptor() {
		MetalSurfaceDescriptor descriptor = new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D);
		MetalDeviceBackend backend = new MetalDeviceBackend(new SurfaceTrackingBridge(), descriptor);

		assertEquals(descriptor, backend.surfaceDescriptor());
		assertEquals("Metal", backend.getDeviceInfo().backendName());
		assertEquals("Apple", backend.getDeviceInfo().vendorName());
		assertFalse(backend.isDebuggingEnabled());
	}

	@Test
	void createsPlaceholderResourcesForInitializationPaths() {
		MetalDeviceBackend backend = new MetalDeviceBackend(
			new SurfaceTrackingBridge(),
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);

		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.REPEAT,
			FilterMode.NEAREST,
			FilterMode.LINEAR,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer buffer = (MetalBuffer) backend.createBuffer(() -> "buf", 3, 16L);
		MetalTexture texture = (MetalTexture) backend.createTexture("tex", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 4, 4, 1, 1);
		MetalTextureView textureView = (MetalTextureView) backend.createTextureView(texture);
		MetalQueryPool queryPool = (MetalQueryPool) backend.createTimestampQueryPool(3);
		MetalCommandEncoderBackend encoder = (MetalCommandEncoderBackend) backend.createCommandEncoder();

		ByteBuffer source = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
		source.putInt(1234).flip();
		buffer.write(0L, source);
		ByteBuffer mapped = encoder.mapBuffer(buffer.slice(), false, true).data();

		assertEquals(AddressMode.CLAMP_TO_EDGE, sampler.getAddressModeU());
		assertEquals(FilterMode.LINEAR, sampler.getMagFilter());
		assertEquals(4, textureView.getWidth(0));
		assertEquals(3, queryPool.size());
		assertEquals(1234, mapped.getInt(0));
	}

	@Test
	void createsSurfaceBackendWithRealBridgeLifecycleHooks() throws SurfaceException {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalSurfaceDescriptor descriptor = new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D);
		MetalDeviceBackend backend = new MetalDeviceBackend(bridge, descriptor);

		MetalSurfaceBackend surfaceBackend = (MetalSurfaceBackend) backend.createSurface(123L);
		surfaceBackend.configure(new GpuSurface.Configuration(1280, 720, true));
		surfaceBackend.acquireNextTexture();
		surfaceBackend.present();
		surfaceBackend.close();

		assertEquals(descriptor, surfaceBackend.descriptor());
		assertFalse(surfaceBackend.isSuboptimal());
		assertEquals(33L, bridge.configuredHandle.get());
		assertEquals(1280, bridge.configuredWidth);
		assertEquals(720, bridge.configuredHeight);
		assertTrue(bridge.configuredVsync);
		assertTrue(bridge.acquired.get());
		assertTrue(bridge.presented.get());
		assertEquals(33L, bridge.releasedHandle.get());
		assertFalse(surfaceBackend.isAcquired());
		assertTrue(surfaceBackend.isClosed());
	}

	@Test
	void surfaceAcquireWrapsBridgeFailuresAsSurfaceException() throws SurfaceException {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		bridge.acquireFailure = new IllegalStateException("CAMetalLayer did not provide a drawable during acquire.");
		MetalSurfaceBackend surfaceBackend = new MetalSurfaceBackend(
			new MetalSurfaceLease(bridge, new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D))
		);
		surfaceBackend.configure(new GpuSurface.Configuration(1280, 720, true));

		SurfaceException exception = assertThrows(SurfaceException.class, surfaceBackend::acquireNextTexture);

		assertEquals("CAMetalLayer did not provide a drawable during acquire.", exception.getMessage());
		assertFalse(surfaceBackend.isAcquired());
	}

	@Test
	void presentBecomesBestEffortWhenLeaseClosesAfterAcquire() throws SurfaceException {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalSurfaceLease surfaceLease = new MetalSurfaceLease(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalSurfaceBackend surfaceBackend = new MetalSurfaceBackend(surfaceLease);
		surfaceBackend.configure(new GpuSurface.Configuration(1280, 720, true));
		surfaceBackend.acquireNextTexture();

		surfaceLease.close();
		surfaceBackend.present();

		assertFalse(surfaceBackend.isAcquired());
		assertFalse(bridge.presented.get());
	}

	@Test
	void supportsRenderSystemInitializationAndMainTargetAllocation() throws Exception {
		GpuDevice device = new GpuDevice(
			new MetalDeviceBackend(
				new SurfaceTrackingBridge(),
				new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
			)
		);
		MainTarget mainTarget = null;

		resetRenderSystemState();
		setRenderThread(Thread.currentThread());
		try {
			RenderSystem.initRenderer(device);
			mainTarget = new MainTarget(64, 64);

			assertEquals(64, mainTarget.getColorTextureView().getWidth(0));
			assertEquals(64, mainTarget.getDepthTextureView().getHeight(0));
		} finally {
			if (mainTarget != null) {
				mainTarget.destroyBuffers();
			}

			RenderSystem.shutdownRenderer();
			resetRenderSystemState();
		}
	}

	@Test
	void supportsNoOpRenderPassAndSurfaceBlitLifecycle() throws Exception {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		GpuDevice device = new GpuDevice(
			new MetalDeviceBackend(
				bridge,
				new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
			)
		);
		GpuSurface surface = null;
		CommandEncoder commandEncoder = null;
		RenderPass renderPass = null;

		resetRenderSystemState();
		setRenderThread(Thread.currentThread());
		try {
			RenderSystem.initRenderer(device);

			com.mojang.blaze3d.textures.GpuTexture colorTexture = device.createTexture("surface-color", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 32, 32, 1, 1);
			com.mojang.blaze3d.textures.GpuTextureView colorTextureView = device.createTextureView(colorTexture);
			com.mojang.blaze3d.textures.GpuTexture colorTextureB = device.createTexture("surface-color-b", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 32, 32, 1, 1);
			com.mojang.blaze3d.textures.GpuTexture depthTexture = device.createTexture("surface-depth", 15, com.mojang.blaze3d.GpuFormat.D32_FLOAT, 32, 32, 1, 1);
			surface = device.createSurface(123L);
			surface.configure(new GpuSurface.Configuration(32, 32, true));
			surface.acquireNextTexture();

			commandEncoder = device.createCommandEncoder();
			commandEncoder.clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0D);
			commandEncoder.clearDepthTexture(depthTexture, 1.0D);
			commandEncoder.copyTextureToTexture(colorTexture, colorTextureB, 0, 0, 0, 0, 0, 32, 32);
			renderPass = commandEncoder.createRenderPass(() -> "noop-pass", colorTextureView, OptionalInt.empty());
			renderPass.draw(3, 1);
			renderPass.close();
			renderPass = null;

			surface.blitFromTexture(commandEncoder, colorTextureView);
			assertEquals(0, bridge.commandContextSubmitCount);
			commandEncoder.submit();
			surface.present();
			assertTrue(bridge.blitCalled.get());
			assertEquals(((MetalTexture) colorTexture).nativeTextureHandle(), bridge.lastBlitTextureHandle);
			assertEquals(1, bridge.commandContextCreateCount);
			assertEquals(1, bridge.commandContextSubmitCount);
			assertEquals(1, bridge.commandContextReleaseCount);

			colorTextureView.close();
			colorTextureB.close();
			depthTexture.close();
			colorTexture.close();
		} finally {
			if (renderPass != null) {
				renderPass.close();
			}

			if (surface != null) {
				surface.close();
			}

			RenderSystem.shutdownRenderer();
			resetRenderSystemState();
		}
	}

	@Test
	void submitFromSeparateCommandEncoderFlushesPendingSurfaceBlit() throws Exception {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		GpuDevice device = new GpuDevice(
			new MetalDeviceBackend(
				bridge,
				new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
			)
		);
		GpuSurface surface = null;

		resetRenderSystemState();
		setRenderThread(Thread.currentThread());
		try {
			RenderSystem.initRenderer(device);

			MetalTexture colorTexture = (MetalTexture) device.createTexture("surface-color", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 32, 32, 1, 1);
			MetalTextureView colorTextureView = (MetalTextureView) device.createTextureView(colorTexture);
			surface = device.createSurface(123L);
			surface.configure(new GpuSurface.Configuration(32, 32, true));
			surface.acquireNextTexture();

			CommandEncoder blitEncoder = device.createCommandEncoder();
			surface.blitFromTexture(blitEncoder, colorTextureView);
			assertEquals(0, bridge.commandContextSubmitCount);

			CommandEncoder submitEncoder = device.createCommandEncoder();
			submitEncoder.submit();

			assertTrue(bridge.blitCalled.get());
			assertEquals(1, bridge.commandContextCreateCount);
			assertEquals(1, bridge.commandContextSubmitCount);
			assertEquals(1, bridge.commandContextReleaseCount);
			surface.present();

			colorTextureView.close();
			colorTexture.close();
		} finally {
			if (surface != null) {
				surface.close();
			}

			RenderSystem.shutdownRenderer();
			resetRenderSystemState();
		}
	}

	@Test
	void incompleteSubmittedSurfaceCommandContextWaitsUntilClose() throws Exception {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		bridge.commandContextsComplete = false;
		GpuDevice device = new GpuDevice(
			new MetalDeviceBackend(
				bridge,
				new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
			)
		);
		GpuSurface surface = null;

		resetRenderSystemState();
		setRenderThread(Thread.currentThread());
		try {
			RenderSystem.initRenderer(device);

			MetalTexture colorTexture = (MetalTexture) device.createTexture("surface-color", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 32, 32, 1, 1);
			MetalTextureView colorTextureView = (MetalTextureView) device.createTextureView(colorTexture);
			surface = device.createSurface(123L);
			surface.configure(new GpuSurface.Configuration(32, 32, true));
			surface.acquireNextTexture();

			CommandEncoder commandEncoder = device.createCommandEncoder();
			surface.blitFromTexture(commandEncoder, colorTextureView);
			commandEncoder.submit();
			surface.present();

			assertEquals(1, bridge.commandContextCreateCount);
			assertEquals(1, bridge.commandContextSubmitCount);
			assertEquals(0, bridge.commandContextWaitCount);
			assertEquals(0, bridge.commandContextReleaseCount);

			surface.close();
			surface = null;

			assertEquals(1, bridge.commandContextWaitCount);
			assertEquals(1, bridge.commandContextReleaseCount);

			colorTextureView.close();
			colorTexture.close();
		} finally {
			if (surface != null) {
				surface.close();
			}

			RenderSystem.shutdownRenderer();
			resetRenderSystemState();
		}
	}

	@Test
	void supportsDynamicTextureUpload() throws Exception {
		GpuDevice device = new GpuDevice(
			new MetalDeviceBackend(
				new SurfaceTrackingBridge(),
				new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
			)
		);
		DynamicTexture dynamicTexture = null;
		NativeImage copyImage = null;
		MetalTexture uploadTexture = null;
		MetalTexture copiedTexture = null;
		MetalBuffer readbackBuffer = null;

		resetRenderSystemState();
		setRenderThread(Thread.currentThread());
		try {
			RenderSystem.initRenderer(device);

			NativeImage image = new NativeImage(2, 2, true);
			image.setPixel(0, 0, 0xFF3366CC);
			image.setPixel(1, 1, 0xFF112233);
			dynamicTexture = new DynamicTexture(() -> "dynamic-upload", image);

			MetalTexture texture = (MetalTexture) dynamicTexture.getTexture();
			ByteBuffer storage = texture.snapshotStorage(0);
			CommandEncoder commandEncoder = device.createCommandEncoder();
			AtomicBoolean readbackCompleted = new AtomicBoolean();

			copyImage = new NativeImage(2, 2, true);
			copyImage.setPixel(0, 0, 0xFFAA5500);
			copyImage.setPixel(1, 1, 0xFF00BB66);
			uploadTexture = (MetalTexture) device.createTexture("upload-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
			copiedTexture = (MetalTexture) device.createTexture("dynamic-copy", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
			commandEncoder.writeToTexture(uploadTexture, copyImage);
			commandEncoder.copyTextureToTexture(uploadTexture, copiedTexture, 0, 0, 0, 0, 0, 2, 2);
			readbackBuffer = (MetalBuffer) device.createBuffer(() -> "dynamic-readback", 9, 16L);
			commandEncoder.copyTextureToBuffer(copiedTexture, readbackBuffer, 0L, () -> readbackCompleted.set(true), 0);
			commandEncoder.submit();

			assertEquals(16, storage.remaining());
			assertFalse(isAllZero(storage));
			assertFalse(isAllZero(copiedTexture.snapshotStorage(0)));
			assertFalse(isAllZero(readbackBuffer.sliceStorage(0L, 16L)));
			assertTrue(readbackCompleted.get());
		} finally {
			if (readbackBuffer != null) {
				readbackBuffer.close();
			}

			if (copiedTexture != null) {
				copiedTexture.close();
			}

			if (uploadTexture != null) {
				uploadTexture.close();
			}

			if (copyImage != null) {
				copyImage.close();
			}

			if (dynamicTexture != null) {
				dynamicTexture.close();
			}

			RenderSystem.shutdownRenderer();
			resetRenderSystemState();
		}
	}

	@Test
	void writeRegionExpandsUndersizedSourceRowLengthForOffsetSubregions() {
		MetalDeviceBackend backend = new MetalDeviceBackend(
			new SurfaceTrackingBridge(),
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture texture = (MetalTexture) backend.createTexture("subregion-upload", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		ByteBuffer atlasPixels = ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder());
		putRgba(atlasPixels, 1, 2, 3, 255);
		putRgba(atlasPixels, 4, 5, 6, 255);
		putRgba(atlasPixels, 10, 20, 30, 255);
		putRgba(atlasPixels, 40, 50, 60, 255);
		putRgba(atlasPixels, 7, 8, 9, 255);
		putRgba(atlasPixels, 11, 12, 13, 255);
		putRgba(atlasPixels, 70, 80, 90, 255);
		putRgba(atlasPixels, 100, 110, 120, 255);
		atlasPixels.flip();

		texture.writeRegion(atlasPixels, 4, 2, 0, 0, 0, 2, 2, 2, 0);

		ByteBuffer uploaded = texture.readRegion(0, 0, 0, 2, 2);
		assertPixelRgba(uploaded, 2, 0, 0, 10, 20, 30, 255);
		assertPixelRgba(uploaded, 2, 1, 0, 40, 50, 60, 255);
		assertPixelRgba(uploaded, 2, 0, 1, 70, 80, 90, 255);
		assertPixelRgba(uploaded, 2, 1, 1, 100, 110, 120, 255);
	}

	@Test
	void unsupportedWorldPipelineReportsStructuredContext() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture color = (MetalTexture) backend.createTexture("world-color", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 64, 64, 1, 1);
		MetalTexture depth = (MetalTexture) backend.createTexture("world-depth", 9, com.mojang.blaze3d.GpuFormat.D32_FLOAT, 64, 64, 1, 1);
		MetalTextureView colorView = new MetalTextureView(color, 0, 1);
		MetalTextureView depthView = new MetalTextureView(depth, 0, 1);
		MetalCommandEncoderBackend encoder = (MetalCommandEncoderBackend) backend.createCommandEncoder();
		MetalUnsupportedPassException exception = assertThrows(
			MetalUnsupportedPassException.class,
			() -> {
				RenderPassBackend pass = encoder.createRenderPass(() -> "world-pass", colorView, OptionalInt.empty(), depthView, java.util.OptionalDouble.empty());
				pass.setPipeline(RenderPipelines.ENTITY_SOLID);
				pass.draw(0, 3);
			}
		);

		assertTrue(exception.getMessage().contains("minecraft:pipeline/entity_solid"));
		assertTrue(exception.getMessage().contains("world-color"));
		assertTrue(exception.getMessage().contains("world-depth"));
		assertTrue(exception.getMessage().contains("RGBA8_UNORM"));
		assertTrue(exception.getMessage().contains("D32_FLOAT"));
		assertTrue(exception.getMessage().contains("drawType=draw"));
	}

	@Test
	void renderPassCarriesDepthAttachmentMetadata() {
		MetalDeviceBackend backend = new MetalDeviceBackend(
			new SurfaceTrackingBridge(),
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture color = (MetalTexture) backend.createTexture("world-color", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 32, 32, 1, 1);
		MetalTexture depth = (MetalTexture) backend.createTexture("world-depth", 9, com.mojang.blaze3d.GpuFormat.D32_FLOAT, 32, 32, 1, 1);
		MetalTextureView colorView = new MetalTextureView(color, 0, 1);
		MetalTextureView depthView = new MetalTextureView(depth, 0, 1);

		MetalRenderPassBackend pass = (MetalRenderPassBackend) new MetalCommandEncoderBackend(backend.surfaceLease())
			.createRenderPass(() -> "depth-pass", colorView, OptionalInt.empty(), depthView, java.util.OptionalDouble.empty());

		assertEquals(com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, pass.colorAttachment().format());
		assertEquals(com.mojang.blaze3d.GpuFormat.D32_FLOAT, pass.depthAttachment().format());
		assertEquals("depth-pass", pass.passLabel());
	}

	@Test
	void clearDepthTextureWritesFloatDepthValues() {
		MetalDeviceBackend backend = new MetalDeviceBackend(
			new SurfaceTrackingBridge(),
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture depth = (MetalTexture) backend.createTexture("depth", 9, com.mojang.blaze3d.GpuFormat.D32_FLOAT, 4, 4, 1, 1);
		MetalCommandEncoderBackend encoder = (MetalCommandEncoderBackend) backend.createCommandEncoder();

		encoder.clearDepthTexture(depth, 0.25D);
		ByteBuffer snapshot = depth.snapshotStorage(0).order(ByteOrder.nativeOrder());

		for (int i = 0; i < 16; i++) {
			assertEquals(0.25F, snapshot.getFloat(i * Float.BYTES), 0.0001F);
		}
	}

	@Test
	void solidTerrainUsesDedicatedNativeDelegate() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture color = (MetalTexture) backend.createTexture("world-color", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 32, 32, 1, 1);
		MetalTexture depth = (MetalTexture) backend.createTexture("world-depth", 9, com.mojang.blaze3d.GpuFormat.D32_FLOAT, 32, 32, 1, 1);
		MetalTexture source = (MetalTexture) backend.createTexture("terrain-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		MetalTexture lightmap = (MetalTexture) backend.createTexture("lightmap-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 16, 16, 1, 1);
		source.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);
		lightmap.writeRegion(solidPixels(16, 16, 255, 255, 255, 255), 4, 16, 0, 0, 0, 16, 16, 0, 0);
		MetalTextureView sourceView = (MetalTextureView) backend.createTextureView(source);
		MetalTextureView lightmapView = (MetalTextureView) backend.createTextureView(lightmap);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.NEAREST,
			FilterMode.NEAREST,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(
			() -> "world-vertices",
			GpuBuffer.USAGE_VERTEX,
			blockTriangleVertices()
		);
		MetalBuffer indexBuffer = (MetalBuffer) backend.createBuffer(() -> "world-indices", GpuBuffer.USAGE_INDEX, triangleIndices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer chunkSectionBuffer = (MetalBuffer) backend.createBuffer(() -> "chunk-section", GpuBuffer.USAGE_UNIFORM, chunkSectionBytes());
		MetalBuffer globalsBuffer = (MetalBuffer) backend.createBuffer(() -> "globals", GpuBuffer.USAGE_UNIFORM, globalsBytes());
		MetalBuffer fogBuffer = (MetalBuffer) backend.createBuffer(() -> "fog", GpuBuffer.USAGE_UNIFORM, fogBytes());
		RenderPass renderPass = new RenderPass(
			((MetalCommandEncoderBackend) backend.createCommandEncoder())
				.createRenderPass(
					() -> "solid-terrain",
					new MetalTextureView(color, 0, 1),
					OptionalInt.empty(),
					new MetalTextureView(depth, 0, 1),
					java.util.OptionalDouble.empty()
				),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.SOLID_TERRAIN);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("ChunkSection", chunkSectionBuffer.slice());
			renderPass.setUniform("Globals", globalsBuffer.slice());
			renderPass.setUniform("Fog", fogBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.setIndexBuffer(indexBuffer, com.mojang.blaze3d.vertex.VertexFormat.IndexType.SHORT);
			renderPass.bindTexture("Sampler0", sourceView, sampler);
			renderPass.bindTexture("Sampler2", lightmapView, sampler);
			renderPass.drawIndexed(0, 0, 3, 1);
		} finally {
			renderPass.close();
		}

		assertEquals(1, bridge.nativeWorldDrawCount);
		assertEquals(9, bridge.lastPipelineKind);
		assertTrue(bridge.lastWorldVertexBufferHandle != 0L);
		assertTrue(bridge.lastWorldIndexBufferHandle != 0L);
		assertEquals(source.nativeTextureHandle(), bridge.lastWorldSampler0TextureHandle);
		assertEquals(lightmap.nativeTextureHandle(), bridge.lastWorldSampler2TextureHandle);
		assertEquals(160, bridge.lastWorldUniform.remaining());
		assertEquals(2.0F, bridge.lastWorldUniform.getFloat(144), 0.0001F);
		assertEquals(2.0F, bridge.lastWorldUniform.getFloat(148), 0.0001F);
		assertEquals(0.0F, bridge.lastWorldUniform.getFloat(152), 0.0001F);
	}

	@Test
	void blurPostPassCopiesInputTextureWithoutVertexBuffer() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture source = (MetalTexture) backend.createTexture("blur-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		MetalTexture target = (MetalTexture) backend.createTexture("blur-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		source.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);

		RenderPipeline blurPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("minecraft", "blur/0"))
			.withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
			.withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "post/box_blur"))
			.withSampler("InSampler")
			.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER)
			.withUniform("BlurConfig", UniformType.UNIFORM_BUFFER)
			.build();

		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(new MetalTextureView(target, 0, 1)),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(blurPipeline);
			renderPass.bindTexture(
				"InSampler",
				new MetalTextureView(source, 0, 1),
				backend.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, java.util.OptionalDouble.empty())
			);
			renderPass.draw(0, 3);
		} finally {
			renderPass.close();
		}

		assertFalse(isAllZero(target.snapshotStorage(0)));
		assertArrayEquals(copyBytes(source.snapshotStorage(0)), copyBytes(target.snapshotStorage(0)));
	}

	@Test
	void lightmapPassRendersFromUniformsWithoutVertexBuffer() {
		MetalDeviceBackend backend = new MetalDeviceBackend(
			new SurfaceTrackingBridge(),
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture target = (MetalTexture) backend.createTexture("lightmap-target", 13, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 16, 16, 1, 1);
		MetalBuffer lightmapInfo = (MetalBuffer) backend.createBuffer(
			() -> "lightmap-info",
			GpuBuffer.USAGE_UNIFORM,
			lightmapInfoBytes()
		);
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(new MetalTextureView(target, 0, 1)),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.LIGHTMAP);
			renderPass.setUniform("LightmapInfo", lightmapInfo.slice());
			renderPass.draw(0, 3);
		} finally {
			renderPass.close();
		}

		ByteBuffer output = target.snapshotStorage(0);
		assertPixelRgba(output, 16, 0, 0, 0, 0, 0, 255);
		assertPixelRgba(output, 16, 15, 15, 255, 255, 255, 255);
	}

	@Test
	void delegatesSkyDrawToNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("sky-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "sky-vertices", GpuBuffer.USAGE_VERTEX, positionTriangleVertices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(
			() -> "dynamic",
			GpuBuffer.USAGE_UNIFORM,
			dynamicTransformsBytesWithColorModulator(0.2F, 0.4F, 0.6F, 1.0F)
		);
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.SKY);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.draw(0, 3);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(7, bridge.lastPipelineKind);
		assertEquals(1, bridge.lastPrimitiveKind);
		assertEquals(colorTexture.nativeTextureHandle(), bridge.lastDrawTargetTextureHandle);
		assertEquals(12, bridge.lastVertexStride);
	}

	@Test
	void buildsSkyRasterSpecFromRenderPipelineMetadata() {
		MetalRasterPipelineSpec spec = MetalRasterPipelineSpecs.describe(RenderPipelines.SKY, false);

		assertEquals(MetalRasterPipelineSpecialCase.NONE, spec.specialCase());
		assertEquals(MetalRasterPipelineSpec.ShaderFamily.SKY, spec.shaderFamily());
		assertEquals(MetalRasterPipelineSpec.VertexLayoutFamily.POSITION, spec.vertexLayoutFamily());
		assertEquals(MetalRasterPipelineSpec.PrimitiveTopology.TRIANGLE, spec.primitiveTopology());
		assertFalse(spec.sampler0Required());
		assertFalse(spec.depthWrite());
		assertFalse(spec.cull());
		assertFalse(spec.blendState().enabled());
	}

	@Test
	void buildsSunriseStarsCelestialAndLineSpecsFromRenderPipelineMetadata() {
		MetalRasterPipelineSpec sunrise = MetalRasterPipelineSpecs.describe(RenderPipelines.SUNRISE_SUNSET, false);
		MetalRasterPipelineSpec stars = MetalRasterPipelineSpecs.describe(RenderPipelines.STARS, true);
		MetalRasterPipelineSpec celestial = MetalRasterPipelineSpecs.describe(RenderPipelines.CELESTIAL, true);
		MetalRasterPipelineSpec lines = MetalRasterPipelineSpecs.describe(RenderPipelines.LINES, true);
		MetalRasterPipelineSpec linesDepthBias = MetalRasterPipelineSpecs.describe(RenderPipelines.LINES_DEPTH_BIAS, true);

		assertEquals(MetalRasterPipelineSpec.ShaderFamily.POSITION_COLOR, sunrise.shaderFamily());
		assertEquals(MetalRasterPipelineSpec.VertexLayoutFamily.POSITION_COLOR, sunrise.vertexLayoutFamily());
		assertTrue(sunrise.blendState().enabled());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.SRC_ALPHA, sunrise.blendState().sourceColorFactor());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_SRC_ALPHA, sunrise.blendState().destinationColorFactor());

		assertEquals(MetalRasterPipelineSpec.ShaderFamily.STARS, stars.shaderFamily());
		assertEquals(MetalRasterPipelineSpec.VertexLayoutFamily.POSITION, stars.vertexLayoutFamily());
		assertTrue(stars.blendState().enabled());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.SRC_ALPHA, stars.blendState().sourceColorFactor());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ONE, stars.blendState().destinationColorFactor());

		assertEquals(MetalRasterPipelineSpec.ShaderFamily.POSITION_TEX, celestial.shaderFamily());
		assertEquals(MetalRasterPipelineSpec.VertexLayoutFamily.POSITION_TEX, celestial.vertexLayoutFamily());
		assertTrue(celestial.sampler0Required());
		assertTrue(celestial.blendState().enabled());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.SRC_ALPHA, celestial.blendState().sourceColorFactor());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ONE, celestial.blendState().destinationColorFactor());

		assertEquals(MetalRasterPipelineSpec.ShaderFamily.LINE, lines.shaderFamily());
		assertEquals(MetalRasterPipelineSpec.VertexLayoutFamily.POSITION_COLOR_NORMAL_LINE_WIDTH, lines.vertexLayoutFamily());
		assertEquals(MetalRasterPipelineSpec.PrimitiveTopology.LINE, lines.primitiveTopology());
		assertEquals(0.0F, lines.depthBiasScaleFactor());
		assertEquals(0.0F, lines.depthBiasConstant());

		assertEquals(MetalRasterPipelineSpec.ShaderFamily.LINE, linesDepthBias.shaderFamily());
		assertEquals(1.0F, linesDepthBias.depthBiasScaleFactor());
		assertEquals(1.0F, linesDepthBias.depthBiasConstant());
	}

	@Test
	void buildsExactTexturedBlendSpecsForVignetteAndCrosshair() {
		MetalRasterPipelineSpec vignette = MetalRasterPipelineSpecs.describe(RenderPipelines.VIGNETTE, true);
		MetalRasterPipelineSpec crosshair = MetalRasterPipelineSpecs.describe(RenderPipelines.CROSSHAIR, true);

		assertEquals(MetalRasterPipelineSpec.ShaderFamily.GUI_TEXTURED, vignette.shaderFamily());
		assertTrue(vignette.sampler0Required());
		assertTrue(vignette.blendState().enabled());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ZERO, vignette.blendState().sourceColorFactor());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_SRC_COLOR, vignette.blendState().destinationColorFactor());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ZERO, vignette.blendState().sourceAlphaFactor());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ONE, vignette.blendState().destinationAlphaFactor());

		assertEquals(MetalRasterPipelineSpec.ShaderFamily.GUI_TEXTURED, crosshair.shaderFamily());
		assertTrue(crosshair.sampler0Required());
		assertTrue(crosshair.blendState().enabled());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_DST_COLOR, crosshair.blendState().sourceColorFactor());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_SRC_COLOR, crosshair.blendState().destinationColorFactor());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ONE, crosshair.blendState().sourceAlphaFactor());
		assertEquals(MetalRasterPipelineSpec.BlendFactor.ZERO, crosshair.blendState().destinationAlphaFactor());
	}

	@Test
	void skyTriangleFanExpandsToTriangleIndicesForNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("sky-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "sky-fan-vertices", GpuBuffer.USAGE_VERTEX, panoramaVertices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(
			() -> "dynamic",
			GpuBuffer.USAGE_UNIFORM,
			dynamicTransformsBytesWithColorModulator(0.2F, 0.4F, 0.6F, 1.0F)
		);
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.SKY);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.draw(0, 4);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(1, bridge.lastPrimitiveKind);
		assertEquals(6, bridge.lastIndexCount);
		assertArrayEquals(new int[]{0, 1, 2, 0, 2, 3}, readIntArray(bridge.lastIndexBytes));
	}

	@Test
	void delegatesStarsAndCelestialToDedicatedNativePipelines() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture sourceTexture = (MetalTexture) backend.createTexture("celestial-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		sourceTexture.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);
		MetalTextureView sourceView = (MetalTextureView) backend.createTextureView(sourceTexture);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("sky-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalSampler sampler = (MetalSampler) backend.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, java.util.OptionalDouble.empty());
		MetalBuffer positionBuffer = (MetalBuffer) backend.createBuffer(() -> "stars-vertices", GpuBuffer.USAGE_VERTEX, panoramaVertices());
		MetalBuffer positionTexBuffer = (MetalBuffer) backend.createBuffer(() -> "celestial-vertices", GpuBuffer.USAGE_VERTEX, positionTexQuadVertices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(
			() -> "dynamic",
			GpuBuffer.USAGE_UNIFORM,
			dynamicTransformsBytesWithColorModulator(1.0F, 1.0F, 1.0F, 0.75F)
		);
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.STARS);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, positionBuffer);
			renderPass.draw(0, 4);

			assertEquals(16, bridge.lastPipelineKind);
			assertEquals(1, bridge.lastPrimitiveKind);
			assertEquals(12, bridge.lastVertexStride);

			renderPass.setPipeline(RenderPipelines.CELESTIAL);
			renderPass.bindTexture("Sampler0", sourceView, sampler);
			renderPass.setVertexBuffer(0, positionTexBuffer);
			renderPass.draw(0, 4);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(15, bridge.lastPipelineKind);
		assertEquals(1, bridge.lastPrimitiveKind);
		assertEquals(20, bridge.lastVertexStride);
		assertEquals(sourceTexture.nativeTextureHandle(), bridge.lastSamplerTextureHandle);
		assertEquals(6, bridge.lastIndexCount);
	}

	@Test
	void drawMultipleIndexedPrefersSharedIndexBufferArgument() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("sky-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "sky-vertices", GpuBuffer.USAGE_VERTEX, positionTriangleVertices());
		MetalBuffer sharedIndexBuffer = (MetalBuffer) backend.createBuffer(() -> "shared-indices", GpuBuffer.USAGE_INDEX, triangleIndices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(
			() -> "dynamic",
			GpuBuffer.USAGE_UNIFORM,
			dynamicTransformsBytesWithColorModulator(0.2F, 0.4F, 0.6F, 1.0F)
		);
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.SKY);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.drawMultipleIndexed(
				List.of(new RenderPass.Draw<Void>(0, vertexBuffer, new DummyGpuBuffer(GpuBuffer.USAGE_INDEX, 6L), VertexFormat.IndexType.SHORT, 0, 3, 0)),
				sharedIndexBuffer,
				VertexFormat.IndexType.SHORT,
				List.of(),
				null
			);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(7, bridge.lastPipelineKind);
	}

	@Test
	void cloudsPipelineIsCurrentlySkippedWithoutFailing() {
		MetalDeviceBackend backend = new MetalDeviceBackend(
			new SurfaceTrackingBridge(),
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("clouds-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(
				new MetalTextureView(colorTexture, 0, 1)
			),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.CLOUDS);
			renderPass.drawIndexed(0, 0, 6, 1);
		} finally {
			renderPass.close();
			colorTexture.close();
		}
	}

	@Test
	void delegatesEntityOutlineBlitToNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture source = (MetalTexture) backend.createTexture("outline-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		source.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);
		MetalTexture target = (MetalTexture) backend.createTexture("outline-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		MetalTextureView sourceView = (MetalTextureView) backend.createTextureView(source);
		MetalTextureView targetView = (MetalTextureView) backend.createTextureView(target);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.LINEAR,
			FilterMode.LINEAR,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer blitConfig = (MetalBuffer) backend.createBuffer(
			() -> "blit-config",
			GpuBuffer.USAGE_UNIFORM,
			blitConfigBytes()
		);
		RenderPipeline blitPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("minecraft", "pipeline/entity_outline_blit"))
			.withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
			.withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "post/blit"))
			.withSampler("InSampler")
			.withUniform("BlitConfig", UniformType.UNIFORM_BUFFER)
			.build();
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(targetView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(blitPipeline);
			renderPass.bindTexture("InSampler", sourceView, sampler);
			renderPass.setUniform("BlitConfig", blitConfig.slice());
			renderPass.draw(0, 3);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(8, bridge.lastPipelineKind);
		assertEquals(target.nativeTextureHandle(), bridge.lastDrawTargetTextureHandle);
		assertEquals(source.nativeTextureHandle(), bridge.lastSamplerTextureHandle);
		assertEquals(16, bridge.lastDynamicTransformsUniform.remaining());
		assertEquals(1.0F, bridge.lastDynamicTransformsUniform.getFloat(0), 0.0001F);
		assertEquals(1.0F, bridge.lastDynamicTransformsUniform.getFloat(12), 0.0001F);
	}

	@Test
	void animateSpriteBlitDrawDelegatesToNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture atlasTexture = (MetalTexture) backend.createTexture("atlas-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView atlasView = (MetalTextureView) backend.createTextureView(atlasTexture);
		MetalTexture spriteTexture = (MetalTexture) backend.createTexture("sprite-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		spriteTexture.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);
		MetalTextureView spriteView = (MetalTextureView) backend.createTextureView(spriteTexture);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.NEAREST,
			FilterMode.NEAREST,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer spriteAnimationInfo = (MetalBuffer) backend.createBuffer(
			() -> "sprite-animation-info",
			GpuBuffer.USAGE_UNIFORM,
			spriteAnimationInfoBytes(2.0F, 3.0F, 2.0F, 2.0F, 0.0F, 0.0F, 0)
		);
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(atlasView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.ANIMATE_SPRITE_BLIT);
			renderPass.bindTexture("Sprite", spriteView, sampler);
			renderPass.setUniform("SpriteAnimationInfo", spriteAnimationInfo.slice());
			renderPass.draw(0, 6);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.animatedSpriteBlitCalled.get());
		assertEquals(atlasTexture.nativeTextureHandle(), bridge.lastAnimatedSpriteTargetTextureHandle);
		assertEquals(spriteTexture.nativeTextureHandle(), bridge.lastAnimatedSpriteSourceTextureHandle);
		assertEquals(0, bridge.lastAnimatedSpriteTargetMipLevel);
		assertEquals(0, bridge.lastAnimatedSpriteSourceMipLevel);
		assertEquals(2, bridge.lastAnimatedSpriteDstX);
		assertEquals(3, bridge.lastAnimatedSpriteDstY);
		assertEquals(2, bridge.lastAnimatedSpriteDstWidth);
		assertEquals(2, bridge.lastAnimatedSpriteDstHeight);
		assertEquals(0.0F, bridge.lastAnimatedSpriteLocalUMin, 0.0001F);
		assertEquals(0.0F, bridge.lastAnimatedSpriteLocalVMin, 0.0001F);
		assertEquals(1.0F, bridge.lastAnimatedSpriteLocalUMax, 0.0001F);
		assertEquals(1.0F, bridge.lastAnimatedSpriteLocalVMax, 0.0001F);
		assertFalse(bridge.lastAnimatedSpriteLinearFiltering);
		assertFalse(bridge.lastAnimatedSpriteRepeatU);
		assertFalse(bridge.lastAnimatedSpriteRepeatV);
	}

	@Test
	void animateSpriteBlitDrawFailsWithoutNativeTextureHandles() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		bridge.disableNativeTextureCreation = true;
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture atlasTexture = (MetalTexture) backend.createTexture("atlas-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView atlasView = (MetalTextureView) backend.createTextureView(atlasTexture);
		MetalTexture spriteTexture = (MetalTexture) backend.createTexture("sprite-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		MetalTextureView spriteView = (MetalTextureView) backend.createTextureView(spriteTexture);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.NEAREST,
			FilterMode.NEAREST,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer spriteAnimationInfo = (MetalBuffer) backend.createBuffer(
			() -> "sprite-animation-info",
			GpuBuffer.USAGE_UNIFORM,
			spriteAnimationInfoBytes(2.0F, 3.0F, 2.0F, 2.0F, 0.0F, 0.0F, 0)
		);
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(atlasView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.ANIMATE_SPRITE_BLIT);
			renderPass.bindTexture("Sprite", spriteView, sampler);
			renderPass.setUniform("SpriteAnimationInfo", spriteAnimationInfo.slice());

			IllegalStateException exception = assertThrows(IllegalStateException.class, () -> renderPass.draw(0, 6));
			assertEquals("Metal animated sprite blit requires a native-backed atlas target texture.", exception.getMessage());
			assertFalse(bridge.animatedSpriteBlitCalled.get());
		} finally {
			renderPass.close();
		}
	}

	@Test
	void delegatesGuiColoredDrawToNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("gui-color-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-color-vertices", GpuBuffer.USAGE_VERTEX, coloredGuiQuadVertices());
		MetalBuffer indexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-color-indices", GpuBuffer.USAGE_INDEX, quadIndices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(() -> "dynamic", GpuBuffer.USAGE_UNIFORM, dynamicTransformsBytes());
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.GUI);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.setIndexBuffer(indexBuffer, com.mojang.blaze3d.vertex.VertexFormat.IndexType.SHORT);
			renderPass.drawIndexed(0, 0, 6, 1);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(1, bridge.lastPipelineKind);
		assertEquals(colorTexture.nativeTextureHandle(), bridge.lastDrawTargetTextureHandle);
		assertEquals(16, bridge.lastVertexStride);
		assertEquals(0L, bridge.lastSamplerTextureHandle);
	}

	@Test
	void genericGuiRasterPassDoesNotBindDepthTexture() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("gui-color-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTexture depthTexture = (MetalTexture) backend.createTexture("gui-depth-target", 24, com.mojang.blaze3d.GpuFormat.D32_FLOAT, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalTextureView depthView = (MetalTextureView) backend.createTextureView(depthTexture);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-color-vertices", GpuBuffer.USAGE_VERTEX, coloredGuiQuadVertices());
		MetalBuffer indexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-color-indices", GpuBuffer.USAGE_INDEX, quadIndices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(() -> "dynamic", GpuBuffer.USAGE_UNIFORM, dynamicTransformsBytes());
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(null, "GUI before blur", colorView, depthView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.GUI);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.setIndexBuffer(indexBuffer, com.mojang.blaze3d.vertex.VertexFormat.IndexType.SHORT);
			renderPass.drawIndexed(0, 0, 6, 1);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(0L, bridge.lastRasterDepthTextureHandle);
	}

	@Test
	void nativeGenericRasterGuiTexturedDrawWritesPixels() {
		System.setProperty("metalexp.nativeLibraryPath", Path.of("build/native/libmetalexp_native.dylib").toAbsolutePath().toString());
		NativeMetalBridge bridge = NativeMetalBridge.getInstance();
		long targetHandle = 0L;
		long sourceHandle = 0L;
		long commandContextHandle = 0L;

		try {
			assertTrue(bridge.probe().isReady());
			targetHandle = bridge.createTexture(8, 8, 1, 1, true, true, false, false);
			sourceHandle = bridge.createTexture(1, 1, 1, 1, false, true, false, false);
			bridge.uploadTextureRgba8(sourceHandle, 0, solidPixels(1, 1, 255, 0, 0, 255), 1, 1);
			commandContextHandle = bridge.createCommandContext();

			bridge.drawRasterPass(
				commandContextHandle,
				targetHandle,
				0L,
				2,
				4,
				1,
				0xF,
				1 << 1,
				1,
				0.0F,
				0.0F,
				1,
				1,
				5,
				15,
				5,
				15,
				clipSpaceTexturedGuiQuadVertices(),
				24,
				0,
				quadIndices(),
				Short.BYTES,
				0,
				6,
				identityProjectionUniformBytes(),
				identityDynamicTransformsBytes(),
				sourceHandle,
				false,
				false,
				false,
				false,
				0,
				0,
				0,
				0
			);
			bridge.submitCommandContext(commandContextHandle);
			bridge.waitForCommandContext(commandContextHandle);

			ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
			bridge.readTextureRgba8(targetHandle, 0, 0, 4, 4, 1, 1, pixel);
			assertPixelRgba(pixel, 1, 0, 0, 255, 0, 0, 255);
		} finally {
			if (commandContextHandle != 0L) {
				bridge.releaseCommandContext(commandContextHandle);
			}
			if (sourceHandle != 0L) {
				bridge.releaseTexture(sourceHandle);
			}
			if (targetHandle != 0L) {
				bridge.releaseTexture(targetHandle);
			}
		}
	}

	@Test
	void delegatesGuiTexturedDrawToNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture sourceTexture = (MetalTexture) backend.createTexture("gui-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		sourceTexture.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);

		MetalTexture colorTexture = (MetalTexture) backend.createTexture("gui-textured-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalTextureView sourceView = (MetalTextureView) backend.createTextureView(sourceTexture);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.NEAREST,
			FilterMode.NEAREST,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-textured-vertices", GpuBuffer.USAGE_VERTEX, texturedGuiQuadVertices());
		MetalBuffer indexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-textured-indices", GpuBuffer.USAGE_INDEX, quadIndices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(() -> "dynamic", GpuBuffer.USAGE_UNIFORM, dynamicTransformsBytes());
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.GUI_TEXTURED);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.bindTexture("Sampler0", sourceView, sampler);
			renderPass.setIndexBuffer(indexBuffer, com.mojang.blaze3d.vertex.VertexFormat.IndexType.SHORT);
			renderPass.drawIndexed(0, 0, 6, 1);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(2, bridge.lastPipelineKind);
		assertEquals(colorTexture.nativeTextureHandle(), bridge.lastDrawTargetTextureHandle);
		assertEquals(sourceTexture.nativeTextureHandle(), bridge.lastSamplerTextureHandle);
		assertFalse(bridge.lastLinearFiltering);
	}

	@Test
	void delegatesGuiTexturedNonIndexedDrawToNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture sourceTexture = (MetalTexture) backend.createTexture("gui-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		sourceTexture.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("gui-textured-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalTextureView sourceView = (MetalTextureView) backend.createTextureView(sourceTexture);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.NEAREST,
			FilterMode.NEAREST,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-textured-vertices", GpuBuffer.USAGE_VERTEX, texturedGuiQuadVertices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(() -> "dynamic", GpuBuffer.USAGE_UNIFORM, dynamicTransformsBytes());
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.GUI_TEXTURED);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.bindTexture("Sampler0", sourceView, sampler);
			renderPass.draw(0, 6);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(2, bridge.lastPipelineKind);
		assertEquals(colorTexture.nativeTextureHandle(), bridge.lastDrawTargetTextureHandle);
		assertEquals(sourceTexture.nativeTextureHandle(), bridge.lastSamplerTextureHandle);
	}

	@Test
	void delegatesGuiOpaqueTexturedBackgroundDrawToNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture sourceTexture = (MetalTexture) backend.createTexture("gui-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		sourceTexture.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("gui-textured-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalTextureView sourceView = (MetalTextureView) backend.createTextureView(sourceTexture);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.NEAREST,
			FilterMode.NEAREST,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-textured-vertices", GpuBuffer.USAGE_VERTEX, texturedGuiQuadVertices());
		MetalBuffer indexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-textured-indices", GpuBuffer.USAGE_INDEX, quadIndices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(() -> "dynamic", GpuBuffer.USAGE_UNIFORM, dynamicTransformsBytes());
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.bindTexture("Sampler0", sourceView, sampler);
			renderPass.setIndexBuffer(indexBuffer, com.mojang.blaze3d.vertex.VertexFormat.IndexType.SHORT);
			renderPass.drawIndexed(0, 0, 6, 1);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(4, bridge.lastPipelineKind);
		assertEquals(sourceTexture.nativeTextureHandle(), bridge.lastSamplerTextureHandle);
	}

	@Test
	void delegatesGuiPremultipliedTexturedDrawToNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture sourceTexture = (MetalTexture) backend.createTexture("gui-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		sourceTexture.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("gui-textured-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalTextureView sourceView = (MetalTextureView) backend.createTextureView(sourceTexture);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.NEAREST,
			FilterMode.NEAREST,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-textured-vertices", GpuBuffer.USAGE_VERTEX, texturedGuiQuadVertices());
		MetalBuffer indexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-textured-indices", GpuBuffer.USAGE_INDEX, quadIndices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(() -> "dynamic", GpuBuffer.USAGE_UNIFORM, dynamicTransformsBytes());
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.bindTexture("Sampler0", sourceView, sampler);
			renderPass.setIndexBuffer(indexBuffer, com.mojang.blaze3d.vertex.VertexFormat.IndexType.SHORT);
			renderPass.drawIndexed(0, 0, 6, 1);
		} finally {
			renderPass.close();
		}

			assertTrue(bridge.guiDrawCalled.get());
			assertEquals(2, bridge.lastRasterShaderFamily);
			assertEquals(sourceTexture.nativeTextureHandle(), bridge.lastSamplerTextureHandle);
		}

	@Test
	void supportsCubemapTextureCreationAndLayeredUpload() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture cubemap = (MetalTexture) backend.createTexture(
			"panorama-cube",
			com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST
				| com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING
				| com.mojang.blaze3d.textures.GpuTexture.USAGE_CUBEMAP_COMPATIBLE,
			com.mojang.blaze3d.GpuFormat.RGBA8_UNORM,
			2,
			2,
			6,
			1
		);
		MetalCommandEncoderBackend encoder = (MetalCommandEncoderBackend) backend.createCommandEncoder();
		NativeImage layerImage = new NativeImage(2, 2, true);

		try {
			layerImage.setPixel(0, 0, 0xFF3366CC);
			layerImage.setPixel(1, 1, 0xFF112233);
			encoder.writeToTexture(cubemap, layerImage, 0, 3, 0, 0, 2, 2, 0, 0);
		} finally {
			layerImage.close();
			cubemap.close();
		}

		assertEquals(6, bridge.lastCreatedDepthOrLayers);
		assertTrue(bridge.lastCreatedCubemapCompatible);
		assertTrue(bridge.sawUploadedLayer3);
		assertEquals(100L, bridge.lastUploadedTextureHandle);
	}

	@Test
	void uploadsNativeImagePixelsToBridgeInRgbaMemoryOrder() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture texture = (MetalTexture) backend.createTexture("upload-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
		MetalCommandEncoderBackend encoder = (MetalCommandEncoderBackend) backend.createCommandEncoder();
		NativeImage image = new NativeImage(1, 1, true);

		try {
			image.setPixel(0, 0, 0xFF3366CC);
			encoder.writeToTexture(texture, image, 0, 0, 0, 0, 1, 1, 0, 0);
		} finally {
			image.close();
			texture.close();
		}

		assertEquals(100L, bridge.lastUploadedTextureHandle);
		assertEquals(0, bridge.lastUploadedLayer);
		assertEquals(1, bridge.lastUploadedWidth);
		assertEquals(1, bridge.lastUploadedHeight);
		assertEquals(0x33, Byte.toUnsignedInt(bridge.lastUploadedPixels[0]));
		assertEquals(0x66, Byte.toUnsignedInt(bridge.lastUploadedPixels[1]));
		assertEquals(0xCC, Byte.toUnsignedInt(bridge.lastUploadedPixels[2]));
		assertEquals(0xFF, Byte.toUnsignedInt(bridge.lastUploadedPixels[3]));
	}

	@Test
	void uploadsByteBufferPixelsToRequestedTextureLayer() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture cubemap = (MetalTexture) backend.createTexture(
			"bytebuffer-cube",
			com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST
				| com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING
				| com.mojang.blaze3d.textures.GpuTexture.USAGE_CUBEMAP_COMPATIBLE,
			com.mojang.blaze3d.GpuFormat.RGBA8_UNORM,
			2,
			2,
			6,
			1
		);
		MetalCommandEncoderBackend encoder = (MetalCommandEncoderBackend) backend.createCommandEncoder();
		ByteBuffer pixels = ByteBuffer.allocateDirect(2 * 2 * 4).order(ByteOrder.nativeOrder());

		putRgba(pixels, 0x10, 0x20, 0x30, 0xFF);
		putRgba(pixels, 0x40, 0x50, 0x60, 0xFF);
		putRgba(pixels, 0x70, 0x80, 0x90, 0xFF);
		putRgba(pixels, 0xA0, 0xB0, 0xC0, 0xFF);
		pixels.flip();

		try {
			encoder.writeToTexture(cubemap, pixels, NativeImage.Format.RGBA, 0, 3, 0, 0, 2, 2);
		} finally {
			cubemap.close();
		}

		assertEquals(100L, bridge.lastUploadedTextureHandle);
		assertTrue(bridge.sawUploadedLayer3);
		assertEquals(2, bridge.lastUploadedWidth);
		assertEquals(2, bridge.lastUploadedHeight);
		assertEquals(0x10, Byte.toUnsignedInt(bridge.layer3UploadedPixels[0]));
		assertEquals(0x20, Byte.toUnsignedInt(bridge.layer3UploadedPixels[1]));
		assertEquals(0x30, Byte.toUnsignedInt(bridge.layer3UploadedPixels[2]));
		assertEquals(0xFF, Byte.toUnsignedInt(bridge.layer3UploadedPixels[3]));
	}

	@Test
	void delegatesPanoramaDrawToNativeBridge() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture sourceTexture = (MetalTexture) backend.createTexture(
			"panorama-source",
			com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST
				| com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING
				| com.mojang.blaze3d.textures.GpuTexture.USAGE_CUBEMAP_COMPATIBLE,
			com.mojang.blaze3d.GpuFormat.RGBA8_UNORM,
			2,
			2,
			6,
			1
		);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("panorama-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalTextureView sourceView = (MetalTextureView) backend.createTextureView(sourceTexture);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.LINEAR,
			FilterMode.LINEAR,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "panorama-vertices", GpuBuffer.USAGE_VERTEX, panoramaVertices());
		MetalBuffer indexBuffer = (MetalBuffer) backend.createBuffer(() -> "panorama-indices", GpuBuffer.USAGE_INDEX, quadIndices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(() -> "dynamic", GpuBuffer.USAGE_UNIFORM, dynamicTransformsBytes());
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.PANORAMA);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.bindTexture("Sampler0", sourceView, sampler);
			renderPass.setIndexBuffer(indexBuffer, com.mojang.blaze3d.vertex.VertexFormat.IndexType.SHORT);
			renderPass.drawIndexed(0, 0, 6, 1);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(3, bridge.lastPipelineKind);
		assertEquals(colorTexture.nativeTextureHandle(), bridge.lastDrawTargetTextureHandle);
		assertEquals(sourceTexture.nativeTextureHandle(), bridge.lastSamplerTextureHandle);
		assertEquals(12, bridge.lastVertexStride);
		assertTrue(bridge.lastLinearFiltering);
	}

	@Test
	void delegatesDynamicTextureMatrixUniformToTexturedGuiDraw() {
		SurfaceTrackingBridge bridge = new SurfaceTrackingBridge();
		MetalDeviceBackend backend = new MetalDeviceBackend(
			bridge,
			new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D)
		);
		MetalTexture sourceTexture = (MetalTexture) backend.createTexture("gui-source", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 2, 2, 1, 1);
		sourceTexture.writeRegion(texturedSourcePixels(), 4, 2, 0, 0, 0, 2, 2, 0, 0);
		MetalTexture colorTexture = (MetalTexture) backend.createTexture("gui-textured-target", 15, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, 8, 8, 1, 1);
		MetalTextureView colorView = (MetalTextureView) backend.createTextureView(colorTexture);
		MetalTextureView sourceView = (MetalTextureView) backend.createTextureView(sourceTexture);
		MetalSampler sampler = (MetalSampler) backend.createSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.NEAREST,
			FilterMode.NEAREST,
			1,
			java.util.OptionalDouble.empty()
		);
		MetalBuffer vertexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-textured-vertices", GpuBuffer.USAGE_VERTEX, texturedGuiQuadVertices());
		MetalBuffer indexBuffer = (MetalBuffer) backend.createBuffer(() -> "gui-textured-indices", GpuBuffer.USAGE_INDEX, quadIndices());
		MetalBuffer projectionBuffer = (MetalBuffer) backend.createBuffer(() -> "projection", GpuBuffer.USAGE_UNIFORM, projectionUniformBytes());
		MetalBuffer dynamicBuffer = (MetalBuffer) backend.createBuffer(() -> "dynamic", GpuBuffer.USAGE_UNIFORM, dynamicTransformsBytesWithTextureScaleAndOffset(0.5F, 0.25F, 0.125F, 0.75F));
		RenderPass renderPass = new RenderPass(
			new MetalRenderPassBackend(colorView),
			backend,
			() -> {
			}
		);

		try {
			renderPass.setPipeline(RenderPipelines.GUI_TEXTURED);
			renderPass.setUniform("Projection", projectionBuffer.slice());
			renderPass.setUniform("DynamicTransforms", dynamicBuffer.slice());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.bindTexture("Sampler0", sourceView, sampler);
			renderPass.setIndexBuffer(indexBuffer, com.mojang.blaze3d.vertex.VertexFormat.IndexType.SHORT);
			renderPass.drawIndexed(0, 0, 6, 1);
		} finally {
			renderPass.close();
		}

		assertTrue(bridge.guiDrawCalled.get());
		assertEquals(160, bridge.lastDynamicTransformsUniform.remaining());
		assertEquals(0.5F, bridge.lastDynamicTransformsUniform.getFloat(96), 0.0001F);
		assertEquals(0.25F, bridge.lastDynamicTransformsUniform.getFloat(96 + 20), 0.0001F);
		assertEquals(0.125F, bridge.lastDynamicTransformsUniform.getFloat(96 + 48), 0.0001F);
		assertEquals(0.75F, bridge.lastDynamicTransformsUniform.getFloat(96 + 52), 0.0001F);
	}

	private static void resetRenderSystemState() throws Exception {
		setRenderThread(null);
		setRenderSystemField("DEVICE", null);
		setRenderSystemField("dynamicUniforms", null);
	}

	private static void setRenderThread(Thread thread) throws Exception {
		setRenderSystemField("renderThread", thread);
	}

	private static void setRenderSystemField(String fieldName, Object value) throws Exception {
		Field field = RenderSystem.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}

	private static boolean isAllZero(ByteBuffer byteBuffer) {
		ByteBuffer copy = byteBuffer.duplicate();

		while (copy.hasRemaining()) {
			if (copy.get() != 0) {
				return false;
			}
		}

		return true;
	}

	private static ByteBuffer coloredGuiQuadVertices() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 16).order(ByteOrder.nativeOrder());
		putColoredVertex(buffer, 0.0F, 0.0F, 255, 0, 0, 255);
		putColoredVertex(buffer, 8.0F, 0.0F, 255, 0, 0, 255);
		putColoredVertex(buffer, 8.0F, 8.0F, 255, 0, 0, 255);
		putColoredVertex(buffer, 0.0F, 8.0F, 255, 0, 0, 255);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer texturedGuiQuadVertices() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 24).order(ByteOrder.nativeOrder());
		putTexturedVertex(buffer, 0.0F, 0.0F, 0.0F, 0.0F, 255, 255, 255, 255);
		putTexturedVertex(buffer, 8.0F, 0.0F, 1.0F, 0.0F, 255, 255, 255, 255);
		putTexturedVertex(buffer, 8.0F, 8.0F, 1.0F, 1.0F, 255, 255, 255, 255);
		putTexturedVertex(buffer, 0.0F, 8.0F, 0.0F, 1.0F, 255, 255, 255, 255);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer clipSpaceTexturedGuiQuadVertices() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 24).order(ByteOrder.nativeOrder());
		putTexturedVertex(buffer, -1.0F, -1.0F, 0.0F, 0.0F, 255, 255, 255, 255);
		putTexturedVertex(buffer, 1.0F, -1.0F, 1.0F, 0.0F, 255, 255, 255, 255);
		putTexturedVertex(buffer, 1.0F, 1.0F, 1.0F, 1.0F, 255, 255, 255, 255);
		putTexturedVertex(buffer, -1.0F, 1.0F, 0.0F, 1.0F, 255, 255, 255, 255);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer panoramaVertices() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 12).order(ByteOrder.nativeOrder());
		putPositionVertex(buffer, -1.0F, -1.0F, 1.0F);
		putPositionVertex(buffer, 1.0F, -1.0F, 1.0F);
		putPositionVertex(buffer, 1.0F, 1.0F, 1.0F);
		putPositionVertex(buffer, -1.0F, 1.0F, 1.0F);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer positionTexQuadVertices() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 20).order(ByteOrder.nativeOrder());
		putPositionTexVertex(buffer, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F);
		putPositionTexVertex(buffer, 1.0F, -1.0F, 0.0F, 1.0F, 0.0F);
		putPositionTexVertex(buffer, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F);
		putPositionTexVertex(buffer, -1.0F, 1.0F, 0.0F, 0.0F, 1.0F);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer quadIndices() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(6 * Short.BYTES).order(ByteOrder.nativeOrder());
		buffer.putShort((short) 0);
		buffer.putShort((short) 1);
		buffer.putShort((short) 2);
		buffer.putShort((short) 0);
		buffer.putShort((short) 2);
		buffer.putShort((short) 3);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer triangleIndices() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(3 * Short.BYTES).order(ByteOrder.nativeOrder());
		buffer.putShort((short) 0);
		buffer.putShort((short) 1);
		buffer.putShort((short) 2);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer texturedSourcePixels() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(2 * 2 * 4).order(ByteOrder.nativeOrder());
		putRgba(buffer, 255, 0, 0, 255);
		putRgba(buffer, 0, 255, 0, 255);
		putRgba(buffer, 0, 0, 255, 255);
		putRgba(buffer, 255, 255, 255, 255);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer solidPixels(int width, int height, int red, int green, int blue, int alpha) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
		for (int index = 0; index < width * height; index++) {
			putRgba(buffer, red, green, blue, alpha);
		}
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer projectionUniformBytes() {
		return ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
	}

	private static ByteBuffer identityProjectionUniformBytes() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
		new Matrix4f().identity().get(0, buffer);
		return buffer;
	}

	private static ByteBuffer dynamicTransformsBytes() {
		return ByteBuffer.allocateDirect(160).order(ByteOrder.nativeOrder());
	}

	private static ByteBuffer identityDynamicTransformsBytes() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(160).order(ByteOrder.nativeOrder());
		new Matrix4f().identity().get(0, buffer);
		buffer.putFloat(64, 1.0F);
		buffer.putFloat(68, 1.0F);
		buffer.putFloat(72, 1.0F);
		buffer.putFloat(76, 1.0F);
		new Matrix4f().identity().get(96, buffer);
		return buffer;
	}

	private static ByteBuffer dynamicTransformsBytesWithTextureScaleAndOffset(float scaleU, float scaleV, float offsetU, float offsetV) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(160).order(ByteOrder.nativeOrder());
		for (int index = 0; index < 4; index++) {
			buffer.putFloat(index * 20, 1.0F);
			buffer.putFloat(96 + index * 20, 1.0F);
		}

		buffer.putFloat(96, scaleU);
		buffer.putFloat(96 + 20, scaleV);
		buffer.putFloat(96 + 48, offsetU);
		buffer.putFloat(96 + 52, offsetV);
		return buffer;
	}

	private static ByteBuffer dynamicTransformsBytesWithColorModulator(float red, float green, float blue, float alpha) {
		ByteBuffer buffer = dynamicTransformsBytes();
		buffer.putFloat(64, red);
		buffer.putFloat(68, green);
		buffer.putFloat(72, blue);
		buffer.putFloat(76, alpha);
		return buffer;
	}

	private static ByteBuffer lightmapInfoBytes() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());
		buffer.putFloat(0, 0.0F);
		buffer.putFloat(4, 1.0F);
		buffer.putFloat(8, 0.0F);
		buffer.putFloat(12, 0.0F);
		buffer.putFloat(16, 0.0F);
		buffer.putFloat(20, 0.0F);
		buffer.putFloat(32, 1.0F);
		buffer.putFloat(36, 1.0F);
		buffer.putFloat(40, 1.0F);
		return buffer;
	}

	private static ByteBuffer blitConfigBytes() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
		buffer.putFloat(0, 1.0F);
		buffer.putFloat(4, 1.0F);
		buffer.putFloat(8, 1.0F);
		buffer.putFloat(12, 1.0F);
		return buffer;
	}

	private static ByteBuffer spriteAnimationInfoBytes(float x, float y, float width, float height, float uPadding, float vPadding, int mipLevel) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(144).order(ByteOrder.nativeOrder());
		buffer.putFloat(0, 1.0F);
		buffer.putFloat(20, 1.0F);
		buffer.putFloat(40, 1.0F);
		buffer.putFloat(60, 1.0F);
		buffer.putFloat(64, width);
		buffer.putFloat(64 + 20, height);
		buffer.putFloat(64 + 40, 1.0F);
		buffer.putFloat(64 + 48, x);
		buffer.putFloat(64 + 52, y);
		buffer.putFloat(64 + 60, 1.0F);
		buffer.putFloat(128, uPadding);
		buffer.putFloat(132, vPadding);
		buffer.putInt(136, mipLevel);
		return buffer;
	}

	private static void putColoredVertex(ByteBuffer buffer, float x, float y, int red, int green, int blue, int alpha) {
		buffer.putFloat(x);
		buffer.putFloat(y);
		buffer.putFloat(0.0F);
		putRgba(buffer, red, green, blue, alpha);
	}

	private static void putPositionVertex(ByteBuffer buffer, float x, float y, float z) {
		buffer.putFloat(x);
		buffer.putFloat(y);
		buffer.putFloat(z);
	}

	private static void putPositionTexVertex(ByteBuffer buffer, float x, float y, float z, float u, float v) {
		putPositionVertex(buffer, x, y, z);
		buffer.putFloat(u);
		buffer.putFloat(v);
	}

	private static ByteBuffer positionTriangleVertices() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(3 * 12).order(ByteOrder.nativeOrder());
		putPositionVertex(buffer, -1.0F, -1.0F, 0.0F);
		putPositionVertex(buffer, 1.0F, -1.0F, 0.0F);
		putPositionVertex(buffer, 0.0F, 1.0F, 0.0F);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer blockTriangleVertices() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(3 * 28).order(ByteOrder.nativeOrder());
		putBlockVertex(buffer, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 255, 255, 255, 255, 0, 0);
		putBlockVertex(buffer, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 255, 255, 255, 255, 0, 0);
		putBlockVertex(buffer, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 255, 255, 255, 255, 0, 0);
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer chunkSectionBytes() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());
		buffer.putFloat(0, 1.0F);
		buffer.putFloat(20, 1.0F);
		buffer.putFloat(40, 1.0F);
		buffer.putFloat(60, 1.0F);
		buffer.putInt(72, 2);
		buffer.putInt(76, 2);
		buffer.putInt(80, 0);
		buffer.putInt(84, 0);
		buffer.putInt(88, 0);
		buffer.putFloat(64, 1.0F);
		return buffer;
	}

	private static ByteBuffer globalsBytes() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
		buffer.putInt(0, 0);
		buffer.putInt(4, 0);
		buffer.putInt(8, 0);
		buffer.putFloat(16, 0.0F);
		buffer.putFloat(20, 0.0F);
		buffer.putFloat(24, 0.0F);
		buffer.putInt(52, 0);
		return buffer;
	}

	private static ByteBuffer fogBytes() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
		buffer.putFloat(0, 0.0F);
		buffer.putFloat(4, 0.0F);
		buffer.putFloat(8, 0.0F);
		buffer.putFloat(12, 1.0F);
		buffer.putFloat(16, 0.0F);
		buffer.putFloat(20, 1.0F);
		buffer.putFloat(24, 0.0F);
		buffer.putFloat(28, 1.0F);
		return buffer;
	}

	private static void putTexturedVertex(ByteBuffer buffer, float x, float y, float u, float v, int red, int green, int blue, int alpha) {
		buffer.putFloat(x);
		buffer.putFloat(y);
		buffer.putFloat(0.0F);
		buffer.putFloat(u);
		buffer.putFloat(v);
		putRgba(buffer, red, green, blue, alpha);
	}

	private static void putRgba(ByteBuffer buffer, int red, int green, int blue, int alpha) {
		buffer.put((byte) red);
		buffer.put((byte) green);
		buffer.put((byte) blue);
		buffer.put((byte) alpha);
	}

	private static void putBlockVertex(ByteBuffer buffer, float x, float y, float z, float u, float v, int red, int green, int blue, int alpha, int lightU, int lightV) {
		buffer.putFloat(x);
		buffer.putFloat(y);
		buffer.putFloat(z);
		putRgba(buffer, red, green, blue, alpha);
		buffer.putFloat(u);
		buffer.putFloat(v);
		buffer.putShort((short) lightU);
		buffer.putShort((short) lightV);
	}

	private static void assertPixelRgba(ByteBuffer buffer, int width, int x, int y, int red, int green, int blue, int alpha) {
		int offset = (y * width + x) * 4;
		assertEquals(red, Byte.toUnsignedInt(buffer.get(offset)));
		assertEquals(green, Byte.toUnsignedInt(buffer.get(offset + 1)));
		assertEquals(blue, Byte.toUnsignedInt(buffer.get(offset + 2)));
		assertEquals(alpha, Byte.toUnsignedInt(buffer.get(offset + 3)));
	}

	private static byte[] copyBytes(ByteBuffer source) {
		ByteBuffer copy = source.duplicate().order(ByteOrder.nativeOrder());
		byte[] bytes = new byte[copy.remaining()];
		copy.get(bytes);
		return bytes;
	}

	private static int[] readIntArray(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
		int[] values = new int[bytes.length / Integer.BYTES];
		for (int index = 0; index < values.length; index++) {
			values[index] = buffer.getInt();
		}
		return values;
	}

	private static final class SurfaceTrackingBridge implements MetalBridge {
		private final AtomicLong configuredHandle = new AtomicLong(-1L);
		private final AtomicLong releasedHandle = new AtomicLong(-1L);
		private final AtomicLong nextBufferHandle = new AtomicLong(10_000L);
		private final AtomicLong nextTextureHandle = new AtomicLong(100L);
		private final AtomicLong nextCommandContextHandle = new AtomicLong(1000L);
		private final AtomicBoolean acquired = new AtomicBoolean();
		private final AtomicBoolean blitCalled = new AtomicBoolean();
		private final AtomicBoolean animatedSpriteBlitCalled = new AtomicBoolean();
		private final AtomicBoolean guiDrawCalled = new AtomicBoolean();
		private final AtomicBoolean presented = new AtomicBoolean();
		private int nativeWorldDrawCount;
		private int commandContextCreateCount;
		private int commandContextSubmitCount;
		private int commandContextWaitCount;
		private int commandContextReleaseCount;
		private int configuredWidth;
		private int configuredHeight;
		private boolean configuredVsync;
		private int blitWidth;
		private int blitHeight;
		private long lastBlitTextureHandle;
			private long lastDrawTargetTextureHandle;
			private long lastSamplerTextureHandle;
			private long lastRasterDepthTextureHandle;
			private long lastWorldSampler0TextureHandle;
		private long lastWorldSampler2TextureHandle;
		private long lastWorldVertexBufferHandle;
		private long lastWorldIndexBufferHandle;
			private long lastUploadedTextureHandle;
			private int lastPipelineKind;
			private int lastPrimitiveKind;
			private int lastRasterShaderFamily;
			private int lastRasterVertexLayoutFamily;
			private int lastRasterFlags;
			private int lastRasterDepthCompare;
			private int lastIndexCount;
			private int lastVertexStride;
		private int lastCreatedDepthOrLayers;
		private int lastUploadedLayer;
		private int lastUploadedWidth;
		private int lastUploadedHeight;
		private boolean lastCreatedCubemapCompatible;
		private boolean lastLinearFiltering;
		private boolean lastAnimatedSpriteLinearFiltering;
		private boolean lastAnimatedSpriteRepeatU;
		private boolean lastAnimatedSpriteRepeatV;
		private boolean sawUploadedLayer3;
		private boolean commandContextsComplete = true;
		private boolean disableNativeTextureCreation;
		private byte[] layer3UploadedPixels = new byte[0];
		private byte[] lastUploadedPixels = new byte[0];
		private byte[] lastIndexBytes = new byte[0];
		private ByteBuffer lastDynamicTransformsUniform = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
		private ByteBuffer lastWorldUniform = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
		private long lastAnimatedSpriteTargetTextureHandle;
		private long lastAnimatedSpriteSourceTextureHandle;
		private int lastAnimatedSpriteTargetMipLevel;
		private int lastAnimatedSpriteSourceMipLevel;
		private int lastAnimatedSpriteDstX;
		private int lastAnimatedSpriteDstY;
		private int lastAnimatedSpriteDstWidth;
		private int lastAnimatedSpriteDstHeight;
		private float lastAnimatedSpriteLocalUMin;
		private float lastAnimatedSpriteLocalVMin;
		private float lastAnimatedSpriteLocalUMax;
		private float lastAnimatedSpriteLocalVMax;
		private RuntimeException acquireFailure;

		@Override
		public MetalBridgeProbe probe() {
			return new MetalBridgeProbe(MetalBridgeProbeStatus.READY, "ready", List.of(), true, true);
		}

		@Override
		public MetalBridgeProbe probeSurface(long cocoaWindowHandle, long cocoaViewHandle) {
			return new MetalBridgeProbe(MetalBridgeProbeStatus.READY, "surface-ready", List.of(), true, true);
		}

		@Override
		public MetalHostSurfaceBootstrap bootstrapSurface(long cocoaWindowHandle, long cocoaViewHandle) {
			return new MetalHostSurfaceBootstrap(33L, 1280, 720, 2.0D, "bootstrapped", List.of(), true, true);
		}

		@Override
		public void configureSurface(long nativeSurfaceHandle, int width, int height, boolean vsync) {
			this.configuredHandle.set(nativeSurfaceHandle);
			this.configuredWidth = width;
			this.configuredHeight = height;
			this.configuredVsync = vsync;
		}

		@Override
		public void acquireSurface(long nativeSurfaceHandle) {
			if (this.acquireFailure != null) {
				throw this.acquireFailure;
			}

			this.acquired.set(true);
		}

		@Override
		public long createCommandContext() {
			this.commandContextCreateCount++;
			return this.nextCommandContextHandle.getAndIncrement();
		}

		@Override
		public void submitCommandContext(long nativeCommandContextHandle) {
			this.commandContextSubmitCount++;
		}

		@Override
		public boolean isCommandContextComplete(long nativeCommandContextHandle) {
			return this.commandContextsComplete;
		}

		@Override
		public void waitForCommandContext(long nativeCommandContextHandle) {
			this.commandContextWaitCount++;
			this.commandContextsComplete = true;
		}

		@Override
		public void releaseCommandContext(long nativeCommandContextHandle) {
			this.commandContextReleaseCount++;
		}

		@Override
		public void blitSurfaceRgba8(long nativeSurfaceHandle, ByteBuffer rgbaPixels, int width, int height) {
			this.blitCalled.set(true);
			this.blitWidth = width;
			this.blitHeight = height;
		}

		@Override
		public long createTexture(int width, int height, int depthOrLayers, int mipLevels, boolean renderAttachment, boolean shaderRead, boolean cubemapCompatible, boolean depthTexture) {
			if (this.disableNativeTextureCreation) {
				throw new UnsupportedOperationException("native textures disabled for test");
			}

			this.lastCreatedDepthOrLayers = depthOrLayers;
			this.lastCreatedCubemapCompatible = cubemapCompatible;
			return this.nextTextureHandle.getAndIncrement();
		}

		@Override
		public long createBuffer(long size) {
			return this.nextBufferHandle.getAndIncrement();
		}

		@Override
		public void uploadBuffer(long nativeBufferHandle, long offset, ByteBuffer data) {
		}

		@Override
		public void uploadTextureRgba8(long nativeTextureHandle, int mipLevel, ByteBuffer rgbaPixels, int width, int height) {
		}

		@Override
		public void uploadTextureRgba8(long nativeTextureHandle, int mipLevel, int layer, ByteBuffer rgbaPixels, int width, int height) {
			this.lastUploadedTextureHandle = nativeTextureHandle;
			this.lastUploadedLayer = layer;
			this.lastUploadedWidth = width;
			this.lastUploadedHeight = height;
			this.lastUploadedPixels = copyBytes(rgbaPixels);
			if (layer == 3) {
				this.sawUploadedLayer3 = true;
				this.layer3UploadedPixels = this.lastUploadedPixels;
			}
		}

		@Override
			public void drawGuiPass(
			long nativeCommandContextHandle,
			long nativeTargetTextureHandle,
			int pipelineKind,
			int primitiveKind,
			ByteBuffer vertexData,
			int vertexStride,
			int baseVertex,
			ByteBuffer indexData,
			int indexTypeBytes,
			int firstIndex,
			int indexCount,
			ByteBuffer projectionUniform,
			ByteBuffer dynamicTransformsUniform,
			long nativeSampler0TextureHandle,
			boolean linearFiltering,
			boolean repeatU,
			boolean repeatV,
			boolean scissorEnabled,
			int scissorX,
			int scissorY,
			int scissorWidth,
			int scissorHeight
		) {
			this.guiDrawCalled.set(true);
			this.lastDrawTargetTextureHandle = nativeTargetTextureHandle;
			this.lastSamplerTextureHandle = nativeSampler0TextureHandle;
			this.lastPipelineKind = pipelineKind;
			this.lastPrimitiveKind = primitiveKind;
			this.lastIndexCount = indexCount;
			this.lastIndexBytes = copyBytes(indexData);
			this.lastVertexStride = vertexStride;
				this.lastLinearFiltering = linearFiltering;
				this.lastDynamicTransformsUniform = copyBuffer(dynamicTransformsUniform);
			}

			@Override
			public void drawRasterPass(
				long nativeCommandContextHandle,
				long nativeColorTextureHandle,
				long nativeDepthTextureHandle,
				int shaderFamily,
				int vertexLayoutFamily,
				int primitiveTopology,
				int colorWriteMask,
				int flags,
				int depthCompare,
				float depthBiasScaleFactor,
				float depthBiasConstant,
				int colorBlendOperation,
				int alphaBlendOperation,
				int sourceColorFactor,
				int destinationColorFactor,
				int sourceAlphaFactor,
				int destinationAlphaFactor,
				ByteBuffer vertexData,
				int vertexStride,
				int baseVertex,
				ByteBuffer indexData,
				int indexTypeBytes,
				int firstIndex,
				int indexCount,
				ByteBuffer projectionUniform,
				ByteBuffer dynamicTransformsUniform,
				long nativeSampler0TextureHandle,
				boolean linearFiltering,
				boolean repeatU,
				boolean repeatV,
				boolean scissorEnabled,
				int scissorX,
				int scissorY,
				int scissorWidth,
				int scissorHeight
			) {
				this.guiDrawCalled.set(true);
				this.lastDrawTargetTextureHandle = nativeColorTextureHandle;
				this.lastRasterDepthTextureHandle = nativeDepthTextureHandle;
				this.lastSamplerTextureHandle = nativeSampler0TextureHandle;
				this.lastPrimitiveKind = primitiveTopology;
				this.lastRasterShaderFamily = shaderFamily;
				this.lastRasterVertexLayoutFamily = vertexLayoutFamily;
				this.lastRasterFlags = flags;
				this.lastRasterDepthCompare = depthCompare;
				this.lastIndexCount = indexCount;
				this.lastIndexBytes = copyBytes(indexData);
				this.lastVertexStride = vertexStride;
				this.lastLinearFiltering = linearFiltering;
				this.lastDynamicTransformsUniform = copyBuffer(dynamicTransformsUniform);
				this.lastPipelineKind = approximateLegacyPipelineKind(
					shaderFamily,
					(flags & 1) != 0,
					colorBlendOperation,
					alphaBlendOperation,
					sourceColorFactor,
					destinationColorFactor,
					sourceAlphaFactor,
					destinationAlphaFactor
				);
			}

		@Override
		public void drawWorldPass(
			long nativeCommandContextHandle,
			long nativeColorTextureHandle,
			long nativeDepthTextureHandle,
			boolean clearDepth,
			float clearDepthValue,
			int pipelineKind,
			long nativeVertexBufferHandle,
			int vertexStride,
			int baseVertex,
			long nativeIndexBufferHandle,
			int indexTypeBytes,
			int firstIndex,
			int indexCount,
			ByteBuffer projectionUniform,
			ByteBuffer worldUniform,
			long nativeSampler0TextureHandle,
			long nativeSampler2TextureHandle,
			boolean linearFiltering,
			boolean repeatU,
			boolean repeatV,
			boolean sampler2LinearFiltering,
			boolean sampler2RepeatU,
			boolean sampler2RepeatV
		) {
			this.nativeWorldDrawCount++;
			this.lastPipelineKind = pipelineKind;
			this.lastWorldVertexBufferHandle = nativeVertexBufferHandle;
			this.lastWorldIndexBufferHandle = nativeIndexBufferHandle;
			this.lastWorldSampler0TextureHandle = nativeSampler0TextureHandle;
			this.lastWorldSampler2TextureHandle = nativeSampler2TextureHandle;
			this.lastWorldUniform = copyBuffer(worldUniform);
		}

		@Override
		public void blitAnimatedSprite(
			long nativeCommandContextHandle,
			long nativeTargetTextureHandle,
			int targetMipLevel,
			long nativeSourceTextureHandle,
			int sourceMipLevel,
			int dstX,
			int dstY,
			int dstWidth,
			int dstHeight,
			float localUMin,
			float localVMin,
			float localUMax,
			float localVMax,
			float uPadding,
			float vPadding,
			boolean linearFiltering,
			boolean repeatU,
			boolean repeatV
		) {
			this.animatedSpriteBlitCalled.set(true);
			this.lastAnimatedSpriteTargetTextureHandle = nativeTargetTextureHandle;
			this.lastAnimatedSpriteTargetMipLevel = targetMipLevel;
			this.lastAnimatedSpriteSourceTextureHandle = nativeSourceTextureHandle;
			this.lastAnimatedSpriteSourceMipLevel = sourceMipLevel;
			this.lastAnimatedSpriteDstX = dstX;
			this.lastAnimatedSpriteDstY = dstY;
			this.lastAnimatedSpriteDstWidth = dstWidth;
			this.lastAnimatedSpriteDstHeight = dstHeight;
			this.lastAnimatedSpriteLocalUMin = localUMin;
			this.lastAnimatedSpriteLocalVMin = localVMin;
			this.lastAnimatedSpriteLocalUMax = localUMax;
			this.lastAnimatedSpriteLocalVMax = localVMax;
			this.lastAnimatedSpriteLinearFiltering = linearFiltering;
			this.lastAnimatedSpriteRepeatU = repeatU;
			this.lastAnimatedSpriteRepeatV = repeatV;
		}

		@Override
		public void blitSurfaceTexture(long nativeCommandContextHandle, long nativeSurfaceHandle, long nativeTextureHandle) {
			this.blitCalled.set(true);
			this.lastBlitTextureHandle = nativeTextureHandle;
		}

		@Override
		public void presentSurface(long nativeSurfaceHandle) {
			this.presented.set(true);
		}

		@Override
		public void releaseSurface(long nativeSurfaceHandle) {
			this.releasedHandle.set(nativeSurfaceHandle);
		}

			private static ByteBuffer copyBuffer(ByteBuffer source) {
				ByteBuffer copy = ByteBuffer.allocateDirect(source.remaining()).order(ByteOrder.nativeOrder());
				ByteBuffer duplicate = source.duplicate().order(ByteOrder.nativeOrder());
				copy.put(duplicate);
				copy.flip();
				return copy;
			}

			private static int approximateLegacyPipelineKind(
				int shaderFamily,
				boolean blendEnabled,
				int colorBlendOperation,
				int alphaBlendOperation,
				int sourceColorFactor,
				int destinationColorFactor,
				int sourceAlphaFactor,
				int destinationAlphaFactor
			) {
				if (shaderFamily == 3) {
					return 3;
				}
				if (shaderFamily == 4) {
					return 7;
				}
				if (shaderFamily == 5) {
					return 16;
				}
				if (shaderFamily == 7) {
					return 15;
				}
				if (shaderFamily == 6) {
					return 14;
				}
				if (!blendEnabled) {
					return 4;
				}
				if (colorBlendOperation == 1
					&& alphaBlendOperation == 1
					&& sourceColorFactor == 15
					&& destinationColorFactor == 11
					&& sourceAlphaFactor == 15
					&& destinationAlphaFactor == 5) {
					return 12;
				}
				if (colorBlendOperation == 1
					&& alphaBlendOperation == 1
					&& sourceColorFactor == 9
					&& destinationColorFactor == 11
					&& sourceAlphaFactor == 5
					&& destinationAlphaFactor == 15) {
					return 13;
				}
				if (shaderFamily == 2) {
					return 2;
				}
				if (shaderFamily == 1) {
					return 1;
				}
				return 0;
			}
		}

	private static final class DummyGpuBuffer extends GpuBuffer {
		private boolean closed;

		private DummyGpuBuffer(int usage, long size) {
			super(usage, size);
		}

		@Override
		public boolean isClosed() {
			return this.closed;
		}

		@Override
		public void close() {
			this.closed = true;
		}
	}
}
