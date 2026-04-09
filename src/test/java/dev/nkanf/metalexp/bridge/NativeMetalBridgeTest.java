package dev.nkanf.metalexp.bridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

	@Test
	void probeCanReachBuiltNativeBridgeOnMacOs() {
		assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("mac"));

		Path libraryPath = Path.of("build/native/libmetalexp_native.dylib").toAbsolutePath().normalize();
		assumeTrue(Files.exists(libraryPath));

		System.setProperty("metalexp.nativeLibraryPath", libraryPath.toString());
		NativeMetalBridgeLoader.resetForTests();

		MetalBridgeProbe probe = NativeMetalBridge.getInstance().probe();

		assertTrue(probe.libraryLoaded());
		assertTrue(probe.nativeEntryPointReached());
		assertNotEquals(MetalBridgeProbeStatus.LIBRARY_MISSING, probe.status());
		assertNotEquals(MetalBridgeProbeStatus.LIBRARY_LOAD_FAILED, probe.status());
		assertNotEquals(MetalBridgeProbeStatus.UNSUPPORTED_OS, probe.status());
		assertTrue(probe.detail() != null && !probe.detail().isBlank());
	}

	@Test
	void surfaceProbeReportsMissingCocoaWindowHandleForNullPointers() {
		assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("mac"));

		Path libraryPath = Path.of("build/native/libmetalexp_native.dylib").toAbsolutePath().normalize();
		assumeTrue(Files.exists(libraryPath));

		System.setProperty("metalexp.nativeLibraryPath", libraryPath.toString());
		NativeMetalBridgeLoader.resetForTests();

		MetalBridgeProbe probe = NativeMetalBridge.getInstance().probeSurface(0L, 0L);

		assertEquals(MetalBridgeProbeStatus.NATIVE_ERROR, probe.status());
		assertEquals(List.of("cocoa_window_handle"), probe.missingCapabilities());
		assertTrue(probe.libraryLoaded());
		assertTrue(probe.nativeEntryPointReached());
		assertTrue(probe.detail() != null && !probe.detail().isBlank());
	}

	@Test
	void bootstrapSurfaceReusesStructuredProbeFailureForNullPointers() {
		assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("mac"));

		Path libraryPath = Path.of("build/native/libmetalexp_native.dylib").toAbsolutePath().normalize();
		assumeTrue(Files.exists(libraryPath));

		System.setProperty("metalexp.nativeLibraryPath", libraryPath.toString());
		NativeMetalBridgeLoader.resetForTests();

		MetalHostSurfaceBootstrap bootstrap = NativeMetalBridge.getInstance().bootstrapSurface(0L, 0L);

		assertEquals(0L, bootstrap.nativeSurfaceHandle());
		assertEquals(0, bootstrap.drawableWidth());
		assertEquals(0, bootstrap.drawableHeight());
		assertEquals(0.0D, bootstrap.contentsScale());
		assertEquals(List.of("cocoa_window_handle"), bootstrap.missingCapabilities());
		assertTrue(bootstrap.libraryLoaded());
		assertTrue(bootstrap.nativeEntryPointReached());
		assertTrue(bootstrap.detail() != null && !bootstrap.detail().isBlank());
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
