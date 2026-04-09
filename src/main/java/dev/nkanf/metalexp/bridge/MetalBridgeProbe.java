package dev.nkanf.metalexp.bridge;

import java.util.List;

public record MetalBridgeProbe(
	MetalBridgeProbeStatus status,
	String detail,
	List<String> missingCapabilities,
	boolean libraryLoaded,
	boolean nativeEntryPointReached
) {
	public MetalBridgeProbe {
		missingCapabilities = missingCapabilities == null ? List.of() : List.copyOf(missingCapabilities);
	}

	public boolean isReady() {
		return this.status == MetalBridgeProbeStatus.READY;
	}
}
