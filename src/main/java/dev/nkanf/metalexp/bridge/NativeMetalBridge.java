package dev.nkanf.metalexp.bridge;

import java.util.Arrays;
import java.util.List;

public final class NativeMetalBridge implements MetalBridge {
	private static final NativeMetalBridge INSTANCE = new NativeMetalBridge();

	private NativeMetalBridge() {
	}

	public static NativeMetalBridge getInstance() {
		return INSTANCE;
	}

	@Override
	public MetalBridgeProbe probe() {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			return fromLoadResult(loadResult);
		}

		try {
			NativeMetalBridgeProbeResult nativeResult = probe0();
			return fromNativeResult(nativeResult);
		} catch (UnsatisfiedLinkError error) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				error.getMessage() == null ? "Metal bridge native probe entrypoint is missing." : error.getMessage(),
				List.of("native_probe_entrypoint"),
				true,
				false
			);
		} catch (RuntimeException error) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				error.getMessage() == null ? "Metal bridge native probe failed." : error.getMessage(),
				List.of("native_probe_runtime"),
				true,
				true
			);
		}
	}

	@Override
	public MetalBridgeProbe probeSurface(long cocoaWindowHandle, long cocoaViewHandle) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			return fromLoadResult(loadResult);
		}

		try {
			NativeMetalBridgeProbeResult nativeResult = probeSurface0(cocoaWindowHandle, cocoaViewHandle);
			return fromNativeResult(nativeResult);
		} catch (UnsatisfiedLinkError error) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				error.getMessage() == null ? "Metal bridge native surface probe entrypoint is missing." : error.getMessage(),
				List.of("native_surface_probe_entrypoint"),
				true,
				false
			);
		} catch (RuntimeException error) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				error.getMessage() == null ? "Metal bridge native surface probe failed." : error.getMessage(),
				List.of("native_surface_probe_runtime"),
				true,
				true
			);
		}
	}

	@Override
	public MetalHostSurfaceBootstrap bootstrapSurface(long cocoaWindowHandle, long cocoaViewHandle) {
		MetalBridgeProbe surfaceProbe = probeSurface(cocoaWindowHandle, cocoaViewHandle);
		if (!surfaceProbe.isReady()) {
			return new MetalHostSurfaceBootstrap(
				0L,
				0,
				0,
				0.0D,
				surfaceProbe.detail(),
				surfaceProbe.missingCapabilities(),
				surfaceProbe.libraryLoaded(),
				surfaceProbe.nativeEntryPointReached()
			);
		}

		try {
			NativeMetalBridgeSurfaceBootstrapResult nativeResult = bootstrapSurface0(cocoaWindowHandle, cocoaViewHandle);
			return fromNativeSurfaceResult(nativeResult);
		} catch (UnsatisfiedLinkError error) {
			return new MetalHostSurfaceBootstrap(
				0L,
				0,
				0,
				0.0D,
				error.getMessage() == null ? "Metal bridge native surface bootstrap entrypoint is missing." : error.getMessage(),
				List.of("native_surface_bootstrap_entrypoint"),
				true,
				false
			);
		} catch (RuntimeException error) {
			return new MetalHostSurfaceBootstrap(
				0L,
				0,
				0,
				0.0D,
				error.getMessage() == null ? "Metal bridge native surface bootstrap failed." : error.getMessage(),
				List.of("native_surface_bootstrap_runtime"),
				true,
				true
			);
		}
	}

	@Override
	public void releaseSurface(long nativeSurfaceHandle) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded() || nativeSurfaceHandle == 0L) {
			return;
		}

		try {
			releaseSurface0(nativeSurfaceHandle);
		} catch (UnsatisfiedLinkError | RuntimeException ignored) {
			// Surface release is best-effort during this bootstrap milestone.
		}
	}

	private MetalBridgeProbe fromLoadResult(MetalBridgeLoadResult loadResult) {
		MetalBridgeProbeStatus status = switch (loadResult.status()) {
			case UNSUPPORTED_OS -> MetalBridgeProbeStatus.UNSUPPORTED_OS;
			case NOT_FOUND -> MetalBridgeProbeStatus.LIBRARY_MISSING;
			case LOAD_FAILED -> MetalBridgeProbeStatus.LIBRARY_LOAD_FAILED;
			case LOADED -> MetalBridgeProbeStatus.NATIVE_ERROR;
		};

		List<String> missingCapabilities = switch (loadResult.status()) {
			case UNSUPPORTED_OS -> List.of("macos_host");
			case NOT_FOUND, LOAD_FAILED -> List.of("native_metal_bridge_library");
			case LOADED -> List.of("native_metal_bridge");
		};

		return new MetalBridgeProbe(
			status,
			loadResult.detail(),
			missingCapabilities,
			false,
			false
		);
	}

	private MetalBridgeProbe fromNativeResult(NativeMetalBridgeProbeResult nativeResult) {
		if (nativeResult == null) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				"Metal bridge native probe returned no result.",
				List.of("native_probe_result"),
				true,
				true
			);
		}

		List<String> missingCapabilities = nativeResult.missingCapabilities() == null
			? List.of()
			: Arrays.asList(nativeResult.missingCapabilities());

		MetalBridgeProbeStatus status = switch (nativeResult.outcomeCode()) {
			case NativeMetalBridgeProbeResult.OUTCOME_READY -> MetalBridgeProbeStatus.READY;
			case NativeMetalBridgeProbeResult.OUTCOME_STUB_UNIMPLEMENTED -> MetalBridgeProbeStatus.STUB_UNIMPLEMENTED;
			default -> MetalBridgeProbeStatus.NATIVE_ERROR;
		};

		return new MetalBridgeProbe(
			status,
			nativeResult.detail(),
			missingCapabilities,
			true,
			true
		);
	}

	private MetalHostSurfaceBootstrap fromNativeSurfaceResult(NativeMetalBridgeSurfaceBootstrapResult nativeResult) {
		if (nativeResult == null) {
			return new MetalHostSurfaceBootstrap(
				0L,
				0,
				0,
				0.0D,
				"Metal bridge native surface bootstrap returned no result.",
				List.of("native_surface_bootstrap_result"),
				true,
				true
			);
		}

		List<String> missingCapabilities = nativeResult.missingCapabilities() == null
			? List.of()
			: Arrays.asList(nativeResult.missingCapabilities());

		long nativeSurfaceHandle = nativeResult.outcomeCode() == NativeMetalBridgeSurfaceBootstrapResult.OUTCOME_READY
			? nativeResult.nativeSurfaceHandle()
			: 0L;

		return new MetalHostSurfaceBootstrap(
			nativeSurfaceHandle,
			nativeResult.drawableWidth(),
			nativeResult.drawableHeight(),
			nativeResult.contentsScale(),
			nativeResult.detail(),
			missingCapabilities,
			true,
			true
		);
	}

	private static native NativeMetalBridgeProbeResult probe0();

	private static native NativeMetalBridgeProbeResult probeSurface0(long cocoaWindowHandle, long cocoaViewHandle);

	private static native NativeMetalBridgeSurfaceBootstrapResult bootstrapSurface0(long cocoaWindowHandle, long cocoaViewHandle);

	private static native void releaseSurface0(long nativeSurfaceHandle);
}
