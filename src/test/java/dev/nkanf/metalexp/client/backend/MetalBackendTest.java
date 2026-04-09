package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.BackendCreationException;
import dev.nkanf.metalexp.bridge.MetalBridge;
import dev.nkanf.metalexp.bridge.MetalBridgeProbe;
import dev.nkanf.metalexp.bridge.MetalBridgeProbeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

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
				readyProbe("Surface probe should not run after a system probe failure.")
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
				)
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
		MetalBackend backend = new MetalBackend(
			new FixedProbeBridge(
				readyProbe("Metal probe succeeded."),
				readyProbe("Metal surface probe succeeded.")
			),
			window -> new CocoaHostSurface(1L, 2L)
		);

		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> backend.createDevice(123L, null, null)
		);

		assertEquals(List.of("metal_device_backend"), exception.getMissingCapabilities());
	}

	@Test
	void createDeviceRejectsMissingWindowHandleBeforeBridgeProbe() {
		MetalBackend backend = new MetalBackend(
			new FixedProbeBridge(
				readyProbe("Metal probe succeeded."),
				readyProbe("Metal surface probe succeeded.")
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

	private static MetalBridgeProbe readyProbe(String detail) {
		return new MetalBridgeProbe(
			MetalBridgeProbeStatus.READY,
			detail,
			List.of(),
			true,
			true
		);
	}

	private record FixedProbeBridge(MetalBridgeProbe probe, MetalBridgeProbe surfaceProbe) implements MetalBridge {
		@Override
		public MetalBridgeProbe probe() {
			return this.probe;
		}

		@Override
		public MetalBridgeProbe probeSurface(long cocoaWindowHandle, long cocoaViewHandle) {
			return this.surfaceProbe;
		}
	}
}
