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

	default boolean isCommandContextComplete(long nativeCommandContextHandle) {
		return true;
	}

	default void waitForCommandContext(long nativeCommandContextHandle) {
	}

	default void releaseCommandContext(long nativeCommandContextHandle) {
	}

	default void blitSurfaceRgba8(long nativeSurfaceHandle, ByteBuffer rgbaPixels, int width, int height) {
		throw new UnsupportedOperationException("Metal surface blit is not implemented by this bridge.");
	}

	default long createTexture2D(int width, int height, int mipLevels) {
		return createTexture(width, height, 1, mipLevels, true, true, false, false);
	}

	default long createTexture(int width, int height, int depthOrLayers, int mipLevels, boolean renderAttachment, boolean shaderRead, boolean cubemapCompatible) {
		return createTexture(width, height, depthOrLayers, mipLevels, renderAttachment, shaderRead, cubemapCompatible, false);
	}

	default long createTexture(int width, int height, int depthOrLayers, int mipLevels, boolean renderAttachment, boolean shaderRead, boolean cubemapCompatible, boolean depthTexture) {
		if (depthOrLayers == 1 && !cubemapCompatible && !depthTexture) {
			return createTexture2D(width, height, mipLevels);
		}

		throw new UnsupportedOperationException("Metal layered texture creation is not implemented by this bridge.");
	}

	default long createBuffer(long size) {
		throw new UnsupportedOperationException("Metal buffer creation is not implemented by this bridge.");
	}

	default void uploadBuffer(long nativeBufferHandle, long offset, ByteBuffer data) {
		throw new UnsupportedOperationException("Metal buffer upload is not implemented by this bridge.");
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

	default void readTextureRgba8(long nativeTextureHandle, int mipLevel, int layer, int srcX, int srcY, int width, int height, ByteBuffer rgbaPixels) {
		throw new UnsupportedOperationException("Metal texture readback is not implemented by this bridge.");
	}

	default void releaseTexture(long nativeTextureHandle) {
	}

	default void releaseBuffer(long nativeBufferHandle) {
	}

	default void drawGuiPass(
		long nativeCommandContextHandle,
		long nativeTargetTextureHandle,
		int pipelineKind,
		int primitiveKind,
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

	default void drawRasterPass(
		long nativeCommandContextHandle,
		long nativeColorTextureHandle,
		long nativeDepthTextureHandle,
		int shaderFamily,
		int vertexLayoutFamily,
		int primitiveTopology,
		int colorWriteMask,
		int flags,
		int depthCompare,
		float depthBiasScaleFactor,
		float depthBiasConstant,
		int colorBlendOperation,
		int alphaBlendOperation,
		int sourceColorFactor,
		int destinationColorFactor,
		int sourceAlphaFactor,
		int destinationAlphaFactor,
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
		throw new UnsupportedOperationException("Metal raster draw is not implemented by this bridge.");
	}

	default void drawWorldPass(
		long nativeCommandContextHandle,
		long nativeColorTextureHandle,
		long nativeDepthTextureHandle,
		boolean clearDepth,
		float clearDepthValue,
		int pipelineKind,
		long nativeVertexBufferHandle,
		int vertexStride,
		int baseVertex,
		long nativeIndexBufferHandle,
		int indexTypeBytes,
		int firstIndex,
		int indexCount,
		ByteBuffer projectionUniform,
		ByteBuffer worldUniform,
		long nativeSampler0TextureHandle,
		long nativeSampler2TextureHandle,
		boolean linearFiltering,
		boolean repeatU,
		boolean repeatV,
		boolean sampler2LinearFiltering,
		boolean sampler2RepeatU,
		boolean sampler2RepeatV
	) {
		throw new UnsupportedOperationException("Metal world draw is not implemented by this bridge.");
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
