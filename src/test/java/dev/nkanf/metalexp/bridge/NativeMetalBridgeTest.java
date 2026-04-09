package dev.nkanf.metalexp.bridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeMetalBridgeTest {
	private String originalOsName = System.getProperty("os.name");
	private String originalLibraryPath = System.getProperty("metalexp.nativeLibraryPath");

	@AfterEach
	void restoreSystemProperties() {
		restoreProperty("os.name", this.originalOsName);
		restoreProperty("metalexp.nativeLibraryPath", this.originalLibraryPath);
		NativeMetalBridgeLoader.resetForTests();
	}

	@Test
	void probeReportsUnsupportedOsBeforeLibraryLoading() {
		System.setProperty("os.name", "Linux");
		clearProperty("metalexp.nativeLibraryPath");
		NativeMetalBridgeLoader.resetForTests();

		MetalBridgeProbe probe = NativeMetalBridge.getInstance().probe();

		assertEquals(MetalBridgeProbeStatus.UNSUPPORTED_OS, probe.status());
		assertEquals(List.of("macos_host"), probe.missingCapabilities());
		assertFalse(probe.libraryLoaded());
		assertFalse(probe.nativeEntryPointReached());
	}

	@Test
	void probeReportsStructuredLoadFailureForExplicitMissingLibrary() {
		System.setProperty("os.name", "Mac OS X");
		System.setProperty("metalexp.nativeLibraryPath", "/tmp/metalexp-definitely-missing.dylib");
		NativeMetalBridgeLoader.resetForTests();

		MetalBridgeProbe probe = NativeMetalBridge.getInstance().probe();

		assertEquals(MetalBridgeProbeStatus.LIBRARY_LOAD_FAILED, probe.status());
		assertEquals(List.of("native_metal_bridge_library"), probe.missingCapabilities());
		assertFalse(probe.libraryLoaded());
		assertFalse(probe.nativeEntryPointReached());
		assertTrue(probe.detail() != null && !probe.detail().isBlank());
	}

	private static void restoreProperty(String name, String value) {
		if (value == null) {
			System.clearProperty(name);
		} else {
			System.setProperty(name, value);
		}
	}

	private static void clearProperty(String name) {
		System.clearProperty(name);
	}
}
