package dev.nkanf.metalexp.bridge;

public interface MetalBridge {
	MetalBridgeProbe probe();

	MetalBridgeProbe probeSurface(long cocoaWindowHandle, long cocoaViewHandle);

	MetalHostSurfaceBootstrap bootstrapSurface(long cocoaWindowHandle, long cocoaViewHandle);

	void releaseSurface(long nativeSurfaceHandle);
}
