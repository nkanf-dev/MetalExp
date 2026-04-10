package dev.nkanf.metalexp.config;

public record MetalExpConfig(
	BackendKind backend,
	FailureMode failureMode,
	boolean diagnosticsEnabled
) {
	public static MetalExpConfig defaults() {
		return new MetalExpConfig(BackendKind.METAL, FailureMode.STRICT, true);
	}
}
