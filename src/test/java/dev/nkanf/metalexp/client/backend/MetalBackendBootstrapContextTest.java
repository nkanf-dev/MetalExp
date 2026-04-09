package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.BackendCreationException;
import dev.nkanf.metalexp.bridge.MetalBridge;
import dev.nkanf.metalexp.bridge.MetalBridgeProbe;
import dev.nkanf.metalexp.bridge.MetalBridgeProbeStatus;
import dev.nkanf.metalexp.bridge.MetalHostSurfaceBootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetalBackendBootstrapContextTest {
	@Test
	void bootstrapRejectsMissingWindowHandle() {
		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> MetalBackendBootstrapContext.bootstrap(
				new FixedBridge(readyProbe(), readyProbe(), readyBootstrap(9L)),
				window -> new CocoaHostSurface(1L, 2L),
				0L
			)
		);

		assertEquals("Metal backend requires a live GLFW window handle.", exception.getMessage());
		assertEquals(List.of("glfw_window_handle"), exception.getMissingCapabilities());
	}

	@Test
	void bootstrapPropagatesSurfaceBootstrapFailure() {
		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> MetalBackendBootstrapContext.bootstrap(
				new FixedBridge(
					readyProbe(),
					readyProbe(),
					new MetalHostSurfaceBootstrap(
						0L,
						0,
						0,
						0.0D,
						"Persistent CAMetalLayer attachment did not stick.",
						List.of("ca_metal_layer_attach"),
						true,
						true
					)
				),
				window -> new CocoaHostSurface(1L, 2L),
				123L
			)
		);

		assertEquals("Persistent CAMetalLayer attachment did not stick.", exception.getMessage());
		assertEquals(List.of("ca_metal_layer_attach"), exception.getMissingCapabilities());
	}

	@Test
	void closeReleasesNativeSurfaceOnlyOnce() throws BackendCreationException {
		AtomicLong releasedHandle = new AtomicLong(-1L);
		AtomicInteger releaseCount = new AtomicInteger();
		FixedBridge bridge = new FixedBridge(readyProbe(), readyProbe(), readyBootstrap(42L), releasedHandle, releaseCount);

		MetalBackendBootstrapContext context = MetalBackendBootstrapContext.bootstrap(
			bridge,
			window -> new CocoaHostSurface(1L, 2L),
			123L
		);

		assertEquals(42L, context.nativeSurfaceHandle());
		assertEquals(1L, context.cocoaHostSurface().cocoaWindowHandle());
		assertEquals(2L, context.cocoaHostSurface().cocoaViewHandle());
		assertEquals(1280, context.drawableWidth());
		assertEquals(720, context.drawableHeight());
		assertEquals(2.0D, context.contentsScale());
		assertEquals(new MetalSurfaceDescriptor(1L, 2L, 42L, 1280, 720, 2.0D), context.surfaceDescriptor());
		context.close();
		context.close();

		assertEquals(42L, releasedHandle.get());
		assertEquals(1, releaseCount.get());
	}

	private static MetalBridgeProbe readyProbe() {
		return new MetalBridgeProbe(
			MetalBridgeProbeStatus.READY,
			"ready",
			List.of(),
			true,
			true
		);
	}

	private static MetalHostSurfaceBootstrap readyBootstrap(long handle) {
		return new MetalHostSurfaceBootstrap(
			handle,
			1280,
			720,
			2.0D,
			"bootstrapped",
			List.of(),
			true,
			true
		);
	}

	private record FixedBridge(
		MetalBridgeProbe probe,
		MetalBridgeProbe surfaceProbe,
		MetalHostSurfaceBootstrap surfaceBootstrap,
		AtomicLong releasedHandle,
		AtomicInteger releaseCount
	) implements MetalBridge {
		private FixedBridge(MetalBridgeProbe probe, MetalBridgeProbe surfaceProbe, MetalHostSurfaceBootstrap surfaceBootstrap) {
			this(probe, surfaceProbe, surfaceBootstrap, new AtomicLong(-1L), new AtomicInteger());
		}

		@Override
		public MetalBridgeProbe probe() {
			return this.probe;
		}

		@Override
		public MetalBridgeProbe probeSurface(long cocoaWindowHandle, long cocoaViewHandle) {
			return this.surfaceProbe;
		}

		@Override
		public MetalHostSurfaceBootstrap bootstrapSurface(long cocoaWindowHandle, long cocoaViewHandle) {
			return this.surfaceBootstrap;
		}

		@Override
		public void releaseSurface(long nativeSurfaceHandle) {
			this.releasedHandle.set(nativeSurfaceHandle);
			this.releaseCount.incrementAndGet();
		}

		@Override
		public void configureSurface(long nativeSurfaceHandle, int width, int height, boolean vsync) {
		}

		@Override
		public void acquireSurface(long nativeSurfaceHandle) {
		}

		@Override
		public void presentSurface(long nativeSurfaceHandle) {
		}
	}
}
