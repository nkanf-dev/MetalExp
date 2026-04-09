package dev.nkanf.metalexp.bridge;

public record MetalBridgeLoadResult(
	MetalBridgeLoadStatus status,
	String detail,
	String source
) {
	public boolean isLoaded() {
		return this.status == MetalBridgeLoadStatus.LOADED;
	}
}
