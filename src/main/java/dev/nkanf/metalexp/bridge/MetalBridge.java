package dev.nkanf.metalexp.bridge;

import java.nio.ByteBuffer;

public interface MetalBridge {
	MetalBridgeProbe probe();

	MetalBridgeProbe probeSurface(long cocoaWindowHandle, long cocoaViewHandle);

	MetalHostSurfaceBootstrap bootstrapSurface(long cocoaWindowHandle, long cocoaViewHandle);

	void configureSurface(long nativeSurfaceHandle, int width, int height, boolean vsync);

	void acquireSurface(long nativeSurfaceHandle);

	default void blitSurfaceRgba8(long nativeSurfaceHandle, ByteBuffer rgbaPixels, int width, int height) {
		throw new UnsupportedOperationException("Metal surface blit is not implemented by this bridge.");
	}

	void presentSurface(long nativeSurfaceHandle);

	void releaseSurface(long nativeSurfaceHandle);
}
