package dev.nkanf.metalexp.bridge;

public record NativeMetalBridgeProbeResult(
	int outcomeCode,
	String detail,
	String[] missingCapabilities
) {
	public static final int OUTCOME_READY = 0;
	public static final int OUTCOME_STUB_UNIMPLEMENTED = 1;
	public static final int OUTCOME_ERROR = 2;
}
