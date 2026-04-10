package dev.nkanf.metalexp.bridge;

import java.util.Arrays;
import java.util.List;
import java.nio.ByteBuffer;

public final class NativeMetalBridge implements MetalBridge {
	private static final NativeMetalBridge INSTANCE = new NativeMetalBridge();

	private NativeMetalBridge() {
	}

	public static NativeMetalBridge getInstance() {
		return INSTANCE;
	}

	@Override
	public MetalBridgeProbe probe() {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			return fromLoadResult(loadResult);
		}

		try {
			NativeMetalBridgeProbeResult nativeResult = probe0();
			return fromNativeResult(nativeResult);
		} catch (UnsatisfiedLinkError error) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				error.getMessage() == null ? "Metal bridge native probe entrypoint is missing." : error.getMessage(),
				List.of("native_probe_entrypoint"),
				true,
				false
			);
		} catch (RuntimeException error) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				error.getMessage() == null ? "Metal bridge native probe failed." : error.getMessage(),
				List.of("native_probe_runtime"),
				true,
				true
			);
		}
	}

	@Override
	public MetalBridgeProbe probeSurface(long cocoaWindowHandle, long cocoaViewHandle) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			return fromLoadResult(loadResult);
		}

		try {
			NativeMetalBridgeProbeResult nativeResult = probeSurface0(cocoaWindowHandle, cocoaViewHandle);
			return fromNativeResult(nativeResult);
		} catch (UnsatisfiedLinkError error) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				error.getMessage() == null ? "Metal bridge native surface probe entrypoint is missing." : error.getMessage(),
				List.of("native_surface_probe_entrypoint"),
				true,
				false
			);
		} catch (RuntimeException error) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				error.getMessage() == null ? "Metal bridge native surface probe failed." : error.getMessage(),
				List.of("native_surface_probe_runtime"),
				true,
				true
			);
		}
	}

	@Override
	public MetalHostSurfaceBootstrap bootstrapSurface(long cocoaWindowHandle, long cocoaViewHandle) {
		MetalBridgeProbe surfaceProbe = probeSurface(cocoaWindowHandle, cocoaViewHandle);
		if (!surfaceProbe.isReady()) {
			return new MetalHostSurfaceBootstrap(
				0L,
				0,
				0,
				0.0D,
				surfaceProbe.detail(),
				surfaceProbe.missingCapabilities(),
				surfaceProbe.libraryLoaded(),
				surfaceProbe.nativeEntryPointReached()
			);
		}

		try {
			NativeMetalBridgeSurfaceBootstrapResult nativeResult = bootstrapSurface0(cocoaWindowHandle, cocoaViewHandle);
			return fromNativeSurfaceResult(nativeResult);
		} catch (UnsatisfiedLinkError error) {
			return new MetalHostSurfaceBootstrap(
				0L,
				0,
				0,
				0.0D,
				error.getMessage() == null ? "Metal bridge native surface bootstrap entrypoint is missing." : error.getMessage(),
				List.of("native_surface_bootstrap_entrypoint"),
				true,
				false
			);
		} catch (RuntimeException error) {
			return new MetalHostSurfaceBootstrap(
				0L,
				0,
				0,
				0.0D,
				error.getMessage() == null ? "Metal bridge native surface bootstrap failed." : error.getMessage(),
				List.of("native_surface_bootstrap_runtime"),
				true,
				true
			);
		}
	}

	@Override
	public void releaseSurface(long nativeSurfaceHandle) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded() || nativeSurfaceHandle == 0L) {
			return;
		}

		try {
			releaseSurface0(nativeSurfaceHandle);
		} catch (UnsatisfiedLinkError | RuntimeException ignored) {
			// Surface release is best-effort during this bootstrap milestone.
		}
	}

	@Override
	public void configureSurface(long nativeSurfaceHandle, int width, int height, boolean vsync) {
		runSurfaceOperation(nativeSurfaceHandle, () -> configureSurface0(nativeSurfaceHandle, width, height, vsync), "surface configure");
	}

	@Override
	public void acquireSurface(long nativeSurfaceHandle) {
		runSurfaceOperation(nativeSurfaceHandle, () -> acquireSurface0(nativeSurfaceHandle), "surface acquire");
	}

	@Override
	public long createCommandContext() {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			throw new IllegalStateException(loadResult.detail());
		}

		try {
			return createCommandContext0();
		} catch (UnsatisfiedLinkError error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native command context create entrypoint is missing." : error.getMessage(),
				error
			);
		} catch (RuntimeException error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native command context create failed." : error.getMessage(),
				error
			);
		}
	}

	@Override
	public void submitCommandContext(long nativeCommandContextHandle) {
		runCommandContextOperation(nativeCommandContextHandle, () -> submitCommandContext0(nativeCommandContextHandle), "command context submit");
	}

	@Override
	public void releaseCommandContext(long nativeCommandContextHandle) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded() || nativeCommandContextHandle == 0L) {
			return;
		}

		try {
			releaseCommandContext0(nativeCommandContextHandle);
		} catch (UnsatisfiedLinkError | RuntimeException ignored) {
			// Command context release is best-effort during the experimental milestone.
		}
	}

	@Override
	public void blitSurfaceRgba8(long nativeSurfaceHandle, ByteBuffer rgbaPixels, int width, int height) {
		requireDirectBuffer("Metal surface blit pixel data", rgbaPixels, (long) width * (long) height * 4L);

		runSurfaceOperation(
			nativeSurfaceHandle,
			() -> blitSurfaceRgba80(nativeSurfaceHandle, rgbaPixels, width, height),
			"surface blit"
		);
	}

	@Override
	public void presentSurface(long nativeSurfaceHandle) {
		runSurfaceOperation(nativeSurfaceHandle, () -> presentSurface0(nativeSurfaceHandle), "surface present");
	}

	@Override
	public long createTexture2D(int width, int height, int mipLevels) {
		return createTexture(width, height, 1, mipLevels, true, true, false);
	}

	@Override
	public long createTexture(int width, int height, int depthOrLayers, int mipLevels, boolean renderAttachment, boolean shaderRead, boolean cubemapCompatible) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			throw new IllegalStateException(loadResult.detail());
		}

		try {
			return createTexture0(width, height, depthOrLayers, mipLevels, renderAttachment, shaderRead, cubemapCompatible);
		} catch (UnsatisfiedLinkError error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native texture create entrypoint is missing." : error.getMessage(),
				error
			);
		} catch (RuntimeException error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native texture create failed." : error.getMessage(),
				error
			);
		}
	}

	@Override
	public void uploadTextureRgba8(long nativeTextureHandle, int mipLevel, ByteBuffer rgbaPixels, int width, int height) {
		uploadTextureRgba8(nativeTextureHandle, mipLevel, 0, rgbaPixels, width, height);
	}

	@Override
	public void uploadTextureRgba8(long nativeTextureHandle, int mipLevel, int layer, ByteBuffer rgbaPixels, int width, int height) {
		requireDirectBuffer("Metal texture upload pixel data", rgbaPixels, (long) width * (long) height * 4L);

		runTextureOperation(
			nativeTextureHandle,
			() -> uploadTextureRgba80(nativeTextureHandle, mipLevel, layer, rgbaPixels, width, height),
			"texture upload"
		);
	}

	@Override
	public void releaseTexture(long nativeTextureHandle) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded() || nativeTextureHandle == 0L) {
			return;
		}

		try {
			releaseTexture0(nativeTextureHandle);
		} catch (UnsatisfiedLinkError | RuntimeException ignored) {
			// Texture release is best-effort during the experimental milestone.
		}
	}

	@Override
	public void drawGuiPass(
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
		requireDirectBuffer("Metal GUI draw vertex buffer", vertexData, 1L);
		requireDirectBuffer("Metal GUI draw index buffer", indexData, 1L);
		requireDirectBuffer("Metal GUI draw projection buffer", projectionUniform, 64L);
		requireDirectBuffer("Metal GUI draw transform buffer", dynamicTransformsUniform, 160L);

		runCommandContextTextureOperation(
			nativeCommandContextHandle,
			nativeTargetTextureHandle,
			() -> drawGuiPass0(
				nativeCommandContextHandle,
				nativeTargetTextureHandle,
				pipelineKind,
				vertexData,
				vertexStride,
				baseVertex,
				indexData,
				indexTypeBytes,
				firstIndex,
				indexCount,
				projectionUniform,
				dynamicTransformsUniform,
				nativeSampler0TextureHandle,
				linearFiltering,
				repeatU,
				repeatV,
				scissorEnabled,
				scissorX,
				scissorY,
				scissorWidth,
				scissorHeight
			),
			"gui draw"
		);
	}

	@Override
	public void blitAnimatedSprite(
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
		if (nativeSourceTextureHandle == 0L) {
			throw new IllegalArgumentException("Metal animated sprite blit requires a non-zero native source texture handle.");
		}

		runCommandContextTextureOperation(
			nativeCommandContextHandle,
			nativeTargetTextureHandle,
			() -> blitAnimatedSprite0(
				nativeCommandContextHandle,
				nativeTargetTextureHandle,
				targetMipLevel,
				nativeSourceTextureHandle,
				sourceMipLevel,
				dstX,
				dstY,
				dstWidth,
				dstHeight,
				localUMin,
				localVMin,
				localUMax,
				localVMax,
				uPadding,
				vPadding,
				linearFiltering,
				repeatU,
				repeatV
			),
			"animated sprite blit"
		);
	}

	@Override
	public void blitSurfaceTexture(long nativeCommandContextHandle, long nativeSurfaceHandle, long nativeTextureHandle) {
		if (nativeTextureHandle == 0L) {
			throw new IllegalArgumentException("Metal surface texture blit requires a non-zero native texture handle.");
		}

		runCommandContextSurfaceOperation(
			nativeCommandContextHandle,
			nativeSurfaceHandle,
			() -> blitSurfaceTexture0(nativeCommandContextHandle, nativeSurfaceHandle, nativeTextureHandle),
			"surface texture blit"
		);
	}

	private MetalBridgeProbe fromLoadResult(MetalBridgeLoadResult loadResult) {
		MetalBridgeProbeStatus status = switch (loadResult.status()) {
			case UNSUPPORTED_OS -> MetalBridgeProbeStatus.UNSUPPORTED_OS;
			case NOT_FOUND -> MetalBridgeProbeStatus.LIBRARY_MISSING;
			case LOAD_FAILED -> MetalBridgeProbeStatus.LIBRARY_LOAD_FAILED;
			case LOADED -> MetalBridgeProbeStatus.NATIVE_ERROR;
		};

		List<String> missingCapabilities = switch (loadResult.status()) {
			case UNSUPPORTED_OS -> List.of("macos_host");
			case NOT_FOUND, LOAD_FAILED -> List.of("native_metal_bridge_library");
			case LOADED -> List.of("native_metal_bridge");
		};

		return new MetalBridgeProbe(
			status,
			loadResult.detail(),
			missingCapabilities,
			false,
			false
		);
	}

	private MetalBridgeProbe fromNativeResult(NativeMetalBridgeProbeResult nativeResult) {
		if (nativeResult == null) {
			return new MetalBridgeProbe(
				MetalBridgeProbeStatus.NATIVE_ERROR,
				"Metal bridge native probe returned no result.",
				List.of("native_probe_result"),
				true,
				true
			);
		}

		List<String> missingCapabilities = nativeResult.missingCapabilities() == null
			? List.of()
			: Arrays.asList(nativeResult.missingCapabilities());

		MetalBridgeProbeStatus status = switch (nativeResult.outcomeCode()) {
			case NativeMetalBridgeProbeResult.OUTCOME_READY -> MetalBridgeProbeStatus.READY;
			case NativeMetalBridgeProbeResult.OUTCOME_STUB_UNIMPLEMENTED -> MetalBridgeProbeStatus.STUB_UNIMPLEMENTED;
			default -> MetalBridgeProbeStatus.NATIVE_ERROR;
		};

		return new MetalBridgeProbe(
			status,
			nativeResult.detail(),
			missingCapabilities,
			true,
			true
		);
	}

	private MetalHostSurfaceBootstrap fromNativeSurfaceResult(NativeMetalBridgeSurfaceBootstrapResult nativeResult) {
		if (nativeResult == null) {
			return new MetalHostSurfaceBootstrap(
				0L,
				0,
				0,
				0.0D,
				"Metal bridge native surface bootstrap returned no result.",
				List.of("native_surface_bootstrap_result"),
				true,
				true
			);
		}

		List<String> missingCapabilities = nativeResult.missingCapabilities() == null
			? List.of()
			: Arrays.asList(nativeResult.missingCapabilities());

		long nativeSurfaceHandle = nativeResult.outcomeCode() == NativeMetalBridgeSurfaceBootstrapResult.OUTCOME_READY
			? nativeResult.nativeSurfaceHandle()
			: 0L;

		return new MetalHostSurfaceBootstrap(
			nativeSurfaceHandle,
			nativeResult.drawableWidth(),
			nativeResult.drawableHeight(),
			nativeResult.contentsScale(),
			nativeResult.detail(),
			missingCapabilities,
			true,
			true
		);
	}

	private void runSurfaceOperation(long nativeSurfaceHandle, Runnable operation, String operationName) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			throw new IllegalStateException(loadResult.detail());
		}

		if (nativeSurfaceHandle == 0L) {
			throw new IllegalArgumentException("Metal " + operationName + " requires a non-zero native surface handle.");
		}

		try {
			operation.run();
		} catch (UnsatisfiedLinkError error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native " + operationName + " entrypoint is missing." : error.getMessage(),
				error
			);
		} catch (RuntimeException error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native " + operationName + " failed." : error.getMessage(),
				error
			);
		}
	}

	private void runCommandContextOperation(long nativeCommandContextHandle, Runnable operation, String operationName) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			throw new IllegalStateException(loadResult.detail());
		}

		if (nativeCommandContextHandle == 0L) {
			throw new IllegalArgumentException("Metal " + operationName + " requires a non-zero native command context handle.");
		}

		try {
			operation.run();
		} catch (UnsatisfiedLinkError error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native " + operationName + " entrypoint is missing." : error.getMessage(),
				error
			);
		} catch (RuntimeException error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native " + operationName + " failed." : error.getMessage(),
				error
			);
		}
	}

	private void runCommandContextTextureOperation(long nativeCommandContextHandle, long nativeTextureHandle, Runnable operation, String operationName) {
		if (nativeTextureHandle == 0L) {
			throw new IllegalArgumentException("Metal " + operationName + " requires a non-zero native texture handle.");
		}

		runCommandContextOperation(nativeCommandContextHandle, operation, operationName);
	}

	private void runCommandContextSurfaceOperation(long nativeCommandContextHandle, long nativeSurfaceHandle, Runnable operation, String operationName) {
		if (nativeSurfaceHandle == 0L) {
			throw new IllegalArgumentException("Metal " + operationName + " requires a non-zero native surface handle.");
		}

		runCommandContextOperation(nativeCommandContextHandle, operation, operationName);
	}

	private void runTextureOperation(long nativeTextureHandle, Runnable operation, String operationName) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			throw new IllegalStateException(loadResult.detail());
		}

		if (nativeTextureHandle == 0L) {
			throw new IllegalArgumentException("Metal " + operationName + " requires a non-zero native texture handle.");
		}

		try {
			operation.run();
		} catch (UnsatisfiedLinkError error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native " + operationName + " entrypoint is missing." : error.getMessage(),
				error
			);
		} catch (RuntimeException error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native " + operationName + " failed." : error.getMessage(),
				error
			);
		}
	}

	private static void requireDirectBuffer(String description, ByteBuffer buffer, long minimumCapacity) {
		if (buffer == null) {
			throw new IllegalArgumentException(description + " is required.");
		}
		if (!buffer.isDirect()) {
			throw new IllegalArgumentException(description + " must be a direct ByteBuffer.");
		}
		if (minimumCapacity > 0L && buffer.capacity() < minimumCapacity) {
			throw new IllegalArgumentException(description + " is smaller than the required native payload.");
		}
	}

	private static native NativeMetalBridgeProbeResult probe0();

	private static native NativeMetalBridgeProbeResult probeSurface0(long cocoaWindowHandle, long cocoaViewHandle);

	private static native NativeMetalBridgeSurfaceBootstrapResult bootstrapSurface0(long cocoaWindowHandle, long cocoaViewHandle);

	private static native void configureSurface0(long nativeSurfaceHandle, int width, int height, boolean vsync);

	private static native void acquireSurface0(long nativeSurfaceHandle);

	private static native long createCommandContext0();

	private static native void submitCommandContext0(long nativeCommandContextHandle);

	private static native void releaseCommandContext0(long nativeCommandContextHandle);

	private static native void blitSurfaceRgba80(long nativeSurfaceHandle, ByteBuffer rgbaPixels, int width, int height);

	private static native long createTexture0(int width, int height, int depthOrLayers, int mipLevels, boolean renderAttachment, boolean shaderRead, boolean cubemapCompatible);

	private static native void uploadTextureRgba80(long nativeTextureHandle, int mipLevel, int layer, ByteBuffer rgbaPixels, int width, int height);

	private static native void releaseTexture0(long nativeTextureHandle);

	private static native void drawGuiPass0(
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
	);

	private static native void blitAnimatedSprite0(
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
	);

	private static native void blitSurfaceTexture0(long nativeCommandContextHandle, long nativeSurfaceHandle, long nativeTextureHandle);

	private static native void presentSurface0(long nativeSurfaceHandle);

	private static native void releaseSurface0(long nativeSurfaceHandle);
}
