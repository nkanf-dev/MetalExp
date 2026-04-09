package dev.nkanf.metalexp.bridge;

public record NativeMetalBridgeSurfaceBootstrapResult(
	int outcomeCode,
	int drawableWidth,
	int drawableHeight,
	double contentsScale,
	String detail,
	String[] missingCapabilities,
	long nativeSurfaceHandle
) {
	public static final int OUTCOME_READY = 0;
	public static final int OUTCOME_ERROR = 2;
}
