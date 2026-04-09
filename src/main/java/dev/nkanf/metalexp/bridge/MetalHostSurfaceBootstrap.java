package dev.nkanf.metalexp.bridge;

import java.util.List;

public record MetalHostSurfaceBootstrap(
	long nativeSurfaceHandle,
	String detail,
	List<String> missingCapabilities,
	boolean libraryLoaded,
	boolean nativeEntryPointReached
) {
	public MetalHostSurfaceBootstrap {
		missingCapabilities = missingCapabilities == null ? List.of() : List.copyOf(missingCapabilities);
	}

	public boolean isReady() {
		return this.nativeSurfaceHandle > 0 && this.missingCapabilities.isEmpty();
	}
}
