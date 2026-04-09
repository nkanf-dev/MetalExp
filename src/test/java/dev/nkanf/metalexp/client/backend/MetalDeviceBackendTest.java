package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import dev.nkanf.metalexp.bridge.MetalBridge;
import dev.nkanf.metalexp.bridge.MetalBridgeProbe;
import dev.nkanf.metalexp.bridge.MetalBridgeProbeStatus;
import dev.nkanf.metalexp.bridge.MetalHostSurfaceBootstrap;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Field;
import java.util.List;
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

	private static final class SurfaceTrackingBridge implements MetalBridge {
		private final AtomicLong configuredHandle = new AtomicLong(-1L);
		private final AtomicLong releasedHandle = new AtomicLong(-1L);
		private final AtomicBoolean acquired = new AtomicBoolean();
		private final AtomicBoolean presented = new AtomicBoolean();
		private int configuredWidth;
		private int configuredHeight;
		private boolean configuredVsync;
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
		public void presentSurface(long nativeSurfaceHandle) {
			this.presented.set(true);
		}

		@Override
		public void releaseSurface(long nativeSurfaceHandle) {
			this.releasedHandle.set(nativeSurfaceHandle);
		}
	}
}
