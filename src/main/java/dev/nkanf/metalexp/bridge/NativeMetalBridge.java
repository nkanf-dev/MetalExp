package dev.nkanf.metalexp.bridge;

import java.util.Arrays;
import java.util.List;
import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;

public final class NativeMetalBridge implements MetalBridge {
	private static final NativeMetalBridge INSTANCE = new NativeMetalBridge();
	private static final int PIPELINE_KIND_POST_BLIT = 8;
	private static final int RASTER_FLAG_BLEND_ENABLED = 1 << 0;
	private static final int RASTER_FLAG_SAMPLER0_REQUIRED = 1 << 1;
	private static final int RASTER_FLAG_SAMPLER2_REQUIRED = 1 << 2;
	private static final int RASTER_FLAG_WANTS_DEPTH_TEXTURE = 1 << 3;
	private static final int RASTER_FLAG_CULL = 1 << 4;
	private static final int RASTER_FLAG_DEPTH_WRITE = 1 << 5;
	private static final long PROJECTION_UNIFORM_BYTES = 64L;
	private static final long GUI_TRANSFORMS_UNIFORM_BYTES = 160L;
	private static final long BLIT_CONFIG_UNIFORM_BYTES = 16L;
	private static final long WORLD_UNIFORM_BYTES = 160L;

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
	public boolean isCommandContextComplete(long nativeCommandContextHandle) {
		return runCommandContextQuery(nativeCommandContextHandle, () -> isCommandContextComplete0(nativeCommandContextHandle), "command context poll");
	}

	@Override
	public void waitForCommandContext(long nativeCommandContextHandle) {
		runCommandContextOperation(nativeCommandContextHandle, () -> waitForCommandContext0(nativeCommandContextHandle), "command context wait");
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
		return createTexture(width, height, 1, mipLevels, true, true, false, false);
	}

	@Override
	public long createTexture(int width, int height, int depthOrLayers, int mipLevels, boolean renderAttachment, boolean shaderRead, boolean cubemapCompatible, boolean depthTexture) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			throw new IllegalStateException(loadResult.detail());
		}

		try {
			return createTexture0(width, height, depthOrLayers, mipLevels, renderAttachment, shaderRead, cubemapCompatible, depthTexture);
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
	public long createBuffer(long size) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			throw new IllegalStateException(loadResult.detail());
		}
		if (size <= 0L) {
			throw new IllegalArgumentException("Metal buffer size must be positive.");
		}

		try {
			return createBuffer0(size);
		} catch (UnsatisfiedLinkError error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native buffer create entrypoint is missing." : error.getMessage(),
				error
			);
		} catch (RuntimeException error) {
			throw new IllegalStateException(
				error.getMessage() == null ? "Metal bridge native buffer create failed." : error.getMessage(),
				error
			);
		}
	}

	@Override
	public void uploadBuffer(long nativeBufferHandle, long offset, ByteBuffer data) {
		requireDirectBuffer("Metal buffer upload data", data, 1L);
		if (offset < 0L) {
			throw new IllegalArgumentException("Metal buffer upload offset must be non-negative.");
		}

		runBufferOperation(nativeBufferHandle, () -> uploadBuffer0(nativeBufferHandle, offset, data), "buffer upload");
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
	public void readTextureRgba8(long nativeTextureHandle, int mipLevel, int layer, int srcX, int srcY, int width, int height, ByteBuffer rgbaPixels) {
		requireDirectBuffer("Metal texture readback pixel data", rgbaPixels, (long) width * (long) height * 4L);

		runTextureOperation(
			nativeTextureHandle,
			() -> readTextureRgba80(nativeTextureHandle, mipLevel, layer, srcX, srcY, width, height, rgbaPixels),
			"texture readback"
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
	public void releaseBuffer(long nativeBufferHandle) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded() || nativeBufferHandle == 0L) {
			return;
		}

		try {
			releaseBuffer0(nativeBufferHandle);
		} catch (UnsatisfiedLinkError | RuntimeException ignored) {
			// Buffer release is best-effort during the experimental milestone.
		}
	}

	@Override
	public void drawGuiPass(
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
		requireDirectBuffer("Metal GUI draw vertex buffer", vertexData, 1L);
		requireDirectBuffer("Metal GUI draw index buffer", indexData, 1L);
		requireDirectBuffer("Metal GUI draw projection buffer", projectionUniform, requiredProjectionUniformBytes(pipelineKind));
		requireDirectBuffer("Metal GUI draw transform buffer", dynamicTransformsUniform, requiredDrawUniformBytes(pipelineKind));

		runCommandContextTextureOperation(
			nativeCommandContextHandle,
			nativeTargetTextureHandle,
			() -> drawGuiPass0(
				nativeCommandContextHandle,
				nativeTargetTextureHandle,
				pipelineKind,
				primitiveKind,
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
	public void drawRasterPass(
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
		requireDirectBuffer("Metal raster draw vertex buffer", vertexData, 1L);
		requireDirectBuffer("Metal raster draw index buffer", indexData, 1L);
		requireDirectBuffer("Metal raster draw projection buffer", projectionUniform, PROJECTION_UNIFORM_BYTES);
		requireDirectBuffer("Metal raster draw transform buffer", dynamicTransformsUniform, GUI_TRANSFORMS_UNIFORM_BYTES);
		if ((flags & RASTER_FLAG_SAMPLER0_REQUIRED) != 0 && nativeSampler0TextureHandle == 0L) {
			throw new IllegalArgumentException("Metal raster draw requires a non-zero native Sampler0 texture handle.");
		}
		if ((flags & (RASTER_FLAG_WANTS_DEPTH_TEXTURE | RASTER_FLAG_DEPTH_WRITE)) != 0 && nativeDepthTextureHandle == 0L) {
			throw new IllegalArgumentException("Metal raster draw requires a non-zero native depth texture handle.");
		}
		if ((flags & RASTER_FLAG_SAMPLER2_REQUIRED) != 0) {
			throw new IllegalArgumentException("Metal raster draw does not support Sampler2 in the generic path.");
		}

		runCommandContextTextureOperation(
			nativeCommandContextHandle,
			nativeColorTextureHandle,
			() -> drawRasterPass0(
				nativeCommandContextHandle,
				nativeColorTextureHandle,
				nativeDepthTextureHandle,
				shaderFamily,
				vertexLayoutFamily,
				primitiveTopology,
				colorWriteMask,
				flags,
				depthCompare,
				depthBiasScaleFactor,
				depthBiasConstant,
				colorBlendOperation,
				alphaBlendOperation,
				sourceColorFactor,
				destinationColorFactor,
				sourceAlphaFactor,
				destinationAlphaFactor,
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
			"raster draw"
		);
	}

	@Override
	public void drawWorldPass(
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
		requireDirectBuffer("Metal world draw projection buffer", projectionUniform, PROJECTION_UNIFORM_BYTES);
		requireDirectBuffer("Metal world draw uniform buffer", worldUniform, WORLD_UNIFORM_BYTES);
		if (nativeDepthTextureHandle == 0L) {
			throw new IllegalArgumentException("Metal world draw requires a non-zero native depth texture handle.");
		}
		if (nativeVertexBufferHandle == 0L) {
			throw new IllegalArgumentException("Metal world draw requires a non-zero native vertex buffer handle.");
		}
		if (nativeIndexBufferHandle == 0L) {
			throw new IllegalArgumentException("Metal world draw requires a non-zero native index buffer handle.");
		}
		if (nativeSampler0TextureHandle == 0L) {
			throw new IllegalArgumentException("Metal world draw requires a non-zero native Sampler0 texture handle.");
		}
		if (nativeSampler2TextureHandle == 0L) {
			throw new IllegalArgumentException("Metal world draw requires a non-zero native Sampler2 texture handle.");
		}

		runCommandContextTextureOperation(
			nativeCommandContextHandle,
			nativeColorTextureHandle,
			() -> drawWorldPass0(
				nativeCommandContextHandle,
				nativeColorTextureHandle,
				nativeDepthTextureHandle,
				clearDepth,
				clearDepthValue,
				pipelineKind,
				nativeVertexBufferHandle,
				vertexStride,
				baseVertex,
				nativeIndexBufferHandle,
				indexTypeBytes,
				firstIndex,
				indexCount,
				projectionUniform,
				worldUniform,
				nativeSampler0TextureHandle,
				nativeSampler2TextureHandle,
				linearFiltering,
				repeatU,
				repeatV,
				sampler2LinearFiltering,
				sampler2RepeatU,
				sampler2RepeatV
			),
			"world draw"
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

	private boolean runCommandContextQuery(long nativeCommandContextHandle, BooleanSupplier operation, String operationName) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			throw new IllegalStateException(loadResult.detail());
		}

		if (nativeCommandContextHandle == 0L) {
			throw new IllegalArgumentException("Metal " + operationName + " requires a non-zero native command context handle.");
		}

		try {
			return operation.getAsBoolean();
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

	private void runBufferOperation(long nativeBufferHandle, Runnable operation, String operationName) {
		MetalBridgeLoadResult loadResult = NativeMetalBridgeLoader.ensureLoaded();
		if (!loadResult.isLoaded()) {
			throw new IllegalStateException(loadResult.detail());
		}

		if (nativeBufferHandle == 0L) {
			throw new IllegalArgumentException("Metal " + operationName + " requires a non-zero native buffer handle.");
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

	private static long requiredProjectionUniformBytes(int pipelineKind) {
		return pipelineKind == PIPELINE_KIND_POST_BLIT ? 1L : PROJECTION_UNIFORM_BYTES;
	}

	private static long requiredDrawUniformBytes(int pipelineKind) {
		return pipelineKind == PIPELINE_KIND_POST_BLIT ? BLIT_CONFIG_UNIFORM_BYTES : GUI_TRANSFORMS_UNIFORM_BYTES;
	}

	private static native NativeMetalBridgeProbeResult probe0();

	private static native NativeMetalBridgeProbeResult probeSurface0(long cocoaWindowHandle, long cocoaViewHandle);

	private static native NativeMetalBridgeSurfaceBootstrapResult bootstrapSurface0(long cocoaWindowHandle, long cocoaViewHandle);

	private static native void configureSurface0(long nativeSurfaceHandle, int width, int height, boolean vsync);

	private static native void acquireSurface0(long nativeSurfaceHandle);

	private static native long createCommandContext0();

	private static native void submitCommandContext0(long nativeCommandContextHandle);

	private static native boolean isCommandContextComplete0(long nativeCommandContextHandle);

	private static native void waitForCommandContext0(long nativeCommandContextHandle);

	private static native void releaseCommandContext0(long nativeCommandContextHandle);

	private static native void blitSurfaceRgba80(long nativeSurfaceHandle, ByteBuffer rgbaPixels, int width, int height);

	private static native long createBuffer0(long size);

	private static native void uploadBuffer0(long nativeBufferHandle, long offset, ByteBuffer data);

	private static native void releaseBuffer0(long nativeBufferHandle);

	private static native long createTexture0(
		int width,
		int height,
		int depthOrLayers,
		int mipLevels,
		boolean renderAttachment,
		boolean shaderRead,
		boolean cubemapCompatible,
		boolean depthTexture
	);

	private static native void uploadTextureRgba80(long nativeTextureHandle, int mipLevel, int layer, ByteBuffer rgbaPixels, int width, int height);

	private static native void readTextureRgba80(long nativeTextureHandle, int mipLevel, int layer, int srcX, int srcY, int width, int height, ByteBuffer rgbaPixels);

	private static native void releaseTexture0(long nativeTextureHandle);

	private static native void drawGuiPass0(
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
	);

	private static native void drawRasterPass0(
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
	);

	private static native void drawWorldPass0(
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
