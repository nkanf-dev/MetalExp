package dev.nkanf.metalexp.bridge;

public interface MetalBridge {
	MetalBridgeProbe probe();

	MetalBridgeProbe probeSurface(long cocoaWindowHandle, long cocoaViewHandle);

	MetalHostSurfaceBootstrap bootstrapSurface(long cocoaWindowHandle, long cocoaViewHandle);

	void configureSurface(long nativeSurfaceHandle, int width, int height, boolean vsync);

	void acquireSurface(long nativeSurfaceHandle);

	void presentSurface(long nativeSurfaceHandle);

	void releaseSurface(long nativeSurfaceHandle);
}
