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
		MetalBackend backend = new MetalBackend(new FixedProbeBridge(new MetalBridgeProbe(
			MetalBridgeProbeStatus.LIBRARY_MISSING,
			"Metal native library is missing.",
			List.of("native_metal_bridge_library"),
			false,
			false
		)));

		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> backend.createDevice(0L, null, null)
		);

		assertEquals("Metal native library is missing.", exception.getMessage());
		assertEquals(List.of("native_metal_bridge_library"), exception.getMissingCapabilities());
	}

	@Test
	void createDeviceStillFailsUntilJavaDeviceBackendExistsAfterReadyProbe() {
		MetalBackend backend = new MetalBackend(new FixedProbeBridge(new MetalBridgeProbe(
			MetalBridgeProbeStatus.READY,
			"Metal probe succeeded.",
			List.of(),
			true,
			true
		)));

		BackendCreationException exception = assertThrows(
			BackendCreationException.class,
			() -> backend.createDevice(0L, null, null)
		);

		assertEquals(List.of("metal_device_backend"), exception.getMissingCapabilities());
	}

	private record FixedProbeBridge(MetalBridgeProbe probe) implements MetalBridge {
		@Override
		public MetalBridgeProbe probe() {
			return this.probe;
		}
	}
}
