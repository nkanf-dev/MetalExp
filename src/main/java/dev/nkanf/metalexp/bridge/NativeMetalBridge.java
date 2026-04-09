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

	private static native NativeMetalBridgeProbeResult probe0();
}
