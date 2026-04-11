package dev.nkanf.metalexp.bridge;

import java.nio.ByteBuffer;

public interface MetalBridge {
	MetalBridgeProbe probe();

	MetalBridgeProbe probeSurface(long cocoaWindowHandle, long cocoaViewHandle);

	MetalHostSurfaceBootstrap bootstrapSurface(long cocoaWindowHandle, long cocoaViewHandle);

	void configureSurface(long nativeSurfaceHandle, int width, int height, boolean vsync);

	void acquireSurface(long nativeSurfaceHandle);

	default long createCommandContext() {
		throw new UnsupportedOperationException("Metal command context creation is not implemented by this bridge.");
	}

	default void submitCommandContext(long nativeCommandContextHandle) {
		throw new UnsupportedOperationException("Metal command context submit is not implemented by this bridge.");
	}

	default void releaseCommandContext(long nativeCommandContextHandle) {
	}

	default void blitSurfaceRgba8(long nativeSurfaceHandle, ByteBuffer rgbaPixels, int width, int height) {
		throw new UnsupportedOperationException("Metal surface blit is not implemented by this bridge.");
	}

	default long createTexture2D(int width, int height, int mipLevels) {
		throw new UnsupportedOperationException("Metal texture creation is not implemented by this bridge.");
	}

	default long createTexture(int width, int height, int depthOrLayers, int mipLevels, boolean renderAttachment, boolean shaderRead, boolean cubemapCompatible) {
		if (depthOrLayers == 1 && !cubemapCompatible) {
			return createTexture2D(width, height, mipLevels);
		}

		throw new UnsupportedOperationException("Metal layered texture creation is not implemented by this bridge.");
	}

	default void uploadTextureRgba8(long nativeTextureHandle, int mipLevel, ByteBuffer rgbaPixels, int width, int height) {
		throw new UnsupportedOperationException("Metal texture upload is not implemented by this bridge.");
	}

	default void uploadTextureRgba8(long nativeTextureHandle, int mipLevel, int layer, ByteBuffer rgbaPixels, int width, int height) {
		if (layer == 0) {
			uploadTextureRgba8(nativeTextureHandle, mipLevel, rgbaPixels, width, height);
			return;
		}

		throw new UnsupportedOperationException("Metal layered texture upload is not implemented by this bridge.");
	}

	default void releaseTexture(long nativeTextureHandle) {
	}

	default void drawGuiPass(
		long nativeCommandContextHandle,
		long nativeTargetTextureHandle,
		int pipelineKind,
		ByteBuffer vertexData,
		int vertexStride,
		int baseVertex,
		ByteBuffer indexData,
		int indexTypeBytes,
		int firstIndex,
		int indexCount,
		ByteBuffer projectionUniform,
		ByteBuffer dynamicTransformsUniform,
		long nativeSampler0TextureHandle,
		boolean linearFiltering,
		boolean repeatU,
		boolean repeatV,
		boolean scissorEnabled,
		int scissorX,
		int scissorY,
		int scissorWidth,
		int scissorHeight
	) {
		throw new UnsupportedOperationException("Metal GUI draw is not implemented by this bridge.");
	}

	default void blitAnimatedSprite(
		long nativeCommandContextHandle,
		long nativeTargetTextureHandle,
		int targetMipLevel,
		long nativeSourceTextureHandle,
		int sourceMipLevel,
		int dstX,
		int dstY,
		int dstWidth,
		int dstHeight,
		float localUMin,
		float localVMin,
		float localUMax,
		float localVMax,
		float uPadding,
		float vPadding,
		boolean linearFiltering,
		boolean repeatU,
		boolean repeatV
	) {
		throw new UnsupportedOperationException("Metal animated sprite blit is not implemented by this bridge.");
	}

	default void blitSurfaceTexture(long nativeCommandContextHandle, long nativeSurfaceHandle, long nativeTextureHandle) {
		throw new UnsupportedOperationException("Metal surface texture blit is not implemented by this bridge.");
	}

	void presentSurface(long nativeSurfaceHandle);

	void releaseSurface(long nativeSurfaceHandle);
}
