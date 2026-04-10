package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import dev.nkanf.metalexp.bridge.MetalBridge;
import dev.nkanf.metalexp.bridge.MetalBridgeProbe;
import dev.nkanf.metalexp.bridge.MetalBridgeProbeStatus;
import dev.nkanf.metalexp.bridge.MetalHostSurfaceBootstrap;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Field;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
			commandEncoder.submit();
			surface.present();
			assertTrue(bridge.blitCalled.get());
			assertEquals(32, bridge.blitWidth);
			assertEquals(32, bridge.blitHeight);

			colorTextureView.close();
			colorTextureB.close();
			depthTexture.close();
			colorTexture.close();
		} finally {
			if (renderPass != null) {
				renderPass.close();
			}

			if (surface != null && !surface.isAcquired()) {
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

	private static final class SurfaceTrackingBridge implements MetalBridge {
		private final AtomicLong configuredHandle = new AtomicLong(-1L);
		private final AtomicLong releasedHandle = new AtomicLong(-1L);
		private final AtomicBoolean acquired = new AtomicBoolean();
		private final AtomicBoolean blitCalled = new AtomicBoolean();
		private final AtomicBoolean presented = new AtomicBoolean();
		private int configuredWidth;
		private int configuredHeight;
		private boolean configuredVsync;
		private int blitWidth;
		private int blitHeight;
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
		public void blitSurfaceRgba8(long nativeSurfaceHandle, ByteBuffer rgbaPixels, int width, int height) {
			this.blitCalled.set(true);
			this.blitWidth = width;
			this.blitHeight = height;
		}

		@Override
		public void presentSurface(long nativeSurfaceHandle) {
			this.presented.set(true);
		}

		@Override
		public void releaseSurface(long nativeSurfaceHandle) {
			this.releasedHandle.set(nativeSurfaceHandle);
		}
	}
}
