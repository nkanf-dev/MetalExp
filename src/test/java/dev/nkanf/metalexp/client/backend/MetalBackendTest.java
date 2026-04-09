package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.BackendCreationException;
import dev.nkanf.metalexp.bridge.MetalBridge;
import dev.nkanf.metalexp.bridge.MetalHostSurfaceBootstrap;
import dev.nkanf.metalexp.bridge.MetalBridgeProbe;
import dev.nkanf.metalexp.bridge.MetalBridgeProbeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetalBackendTest {
	@Test
	void createDevicePropagatesBridgeProbeFailure() {
		MetalBackend backend = new MetalBackend(
			new FixedProbeBridge(
				new MetalBridgeProbe(
					MetalBridgeProbeStatus.LIBRARY_MISSING,
					"Metal native library is missing.",
					List.of("native_metal_bridge_library"),
					false,
					false
				),
				readyProbe("Surface probe should not run after a system probe failure."),
				readyBootstrap("Surface bootstrap should not run after a system probe failure.", 7L)
			),
			window -> new CocoaHostSurface(1L, 2L)
		);

		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> backend.createDevice(123L, null, null)
		);

		assertEquals("Metal native library is missing.", exception.getMessage());
		assertEquals(List.of("native_metal_bridge_library"), exception.getMissingCapabilities());
	}

	@Test
	void createDevicePropagatesSurfaceProbeFailureAfterReadySystemProbe() {
		MetalBackend backend = new MetalBackend(
			new FixedProbeBridge(
				readyProbe("Metal probe succeeded."),
				new MetalBridgeProbe(
					MetalBridgeProbeStatus.NATIVE_ERROR,
					"GLFW Cocoa view is unavailable.",
					List.of("cocoa_view_handle"),
					true,
					true
				),
				readyBootstrap("Surface bootstrap should not run after a surface probe failure.", 7L)
			),
			window -> new CocoaHostSurface(1L, 0L)
		);

		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> backend.createDevice(123L, null, null)
		);

		assertEquals("GLFW Cocoa view is unavailable.", exception.getMessage());
		assertEquals(List.of("cocoa_view_handle"), exception.getMissingCapabilities());
	}

	@Test
	void createDeviceStillFailsUntilJavaDeviceBackendExistsAfterReadySurfaceProbe() {
		AtomicLong releasedHandle = new AtomicLong(-1L);
		MetalBackend backend = new MetalBackend(
			new FixedProbeBridge(
				readyProbe("Metal probe succeeded."),
				readyProbe("Metal surface probe succeeded."),
				readyBootstrap("Metal surface bootstrap succeeded.", 42L),
				releasedHandle
			),
			window -> new CocoaHostSurface(1L, 2L)
		);

		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> backend.createDevice(123L, null, null)
		);

		assertEquals(List.of("metal_device_backend"), exception.getMissingCapabilities());
		assertEquals(42L, releasedHandle.get());
	}

	@Test
	void createDeviceRejectsMissingWindowHandleBeforeBridgeProbe() {
		MetalBackend backend = new MetalBackend(
			new FixedProbeBridge(
				readyProbe("Metal probe succeeded."),
				readyProbe("Metal surface probe succeeded."),
				readyBootstrap("Metal surface bootstrap succeeded.", 9L)
			),
			window -> new CocoaHostSurface(1L, 2L)
		);

		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> backend.createDevice(0L, null, null)
		);

		assertEquals("Metal backend requires a live GLFW window handle.", exception.getMessage());
		assertEquals(List.of("glfw_window_handle"), exception.getMissingCapabilities());
	}

	@Test
	void createDevicePropagatesSurfaceBootstrapFailureAfterReadyProbe() {
		MetalBackend backend = new MetalBackend(
			new FixedProbeBridge(
				readyProbe("Metal probe succeeded."),
				readyProbe("Metal surface probe succeeded."),
				new MetalHostSurfaceBootstrap(
					0L,
					0,
					0,
					0.0D,
					"CAMetalLayer bootstrap failed.",
					List.of("ca_metal_layer_attach"),
					true,
					true
				)
			),
			window -> new CocoaHostSurface(1L, 2L)
		);

		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> backend.createDevice(123L, null, null)
		);

		assertEquals("CAMetalLayer bootstrap failed.", exception.getMessage());
		assertEquals(List.of("ca_metal_layer_attach"), exception.getMissingCapabilities());
	}

	private static MetalBridgeProbe readyProbe(String detail) {
		return new MetalBridgeProbe(
			MetalBridgeProbeStatus.READY,
			detail,
			List.of(),
			true,
			true
		);
	}

	private static MetalHostSurfaceBootstrap readyBootstrap(String detail, long nativeSurfaceHandle) {
		return new MetalHostSurfaceBootstrap(
			nativeSurfaceHandle,
			1280,
			720,
			2.0D,
			detail,
			List.of(),
			true,
			true
		);
	}

	private record FixedProbeBridge(
		MetalBridgeProbe probe,
		MetalBridgeProbe surfaceProbe,
		MetalHostSurfaceBootstrap surfaceBootstrap,
		AtomicLong releasedHandle
	) implements MetalBridge {
		private FixedProbeBridge(MetalBridgeProbe probe, MetalBridgeProbe surfaceProbe, MetalHostSurfaceBootstrap surfaceBootstrap) {
			this(probe, surfaceProbe, surfaceBootstrap, new AtomicLong(-1L));
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
		}
	}
}
