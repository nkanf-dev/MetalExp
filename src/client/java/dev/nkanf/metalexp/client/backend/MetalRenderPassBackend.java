package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.nkanf.metalexp.MetalExpMod;
import dev.nkanf.metalexp.bridge.MetalBridge;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class MetalRenderPassBackend implements RenderPassBackend {
	private static final int PIPELINE_KIND_GUI_COLOR = 1;
	private static final int PIPELINE_KIND_GUI_TEXTURED = 2;
	private static final int PIPELINE_KIND_PANORAMA = 3;
	private static final int PIPELINE_KIND_GUI_TEXTURED_OPAQUE_BACKGROUND = 4;
	private static final int PIPELINE_KIND_GUI_TEXTURED_PREMULTIPLIED_ALPHA = 5;
	private static final int PIPELINE_KIND_WORLD_OPAQUE = 6;
	private static final int PIPELINE_KIND_SKY = 7;
	private static final int PIPELINE_KIND_POST_BLIT = 8;
	private static final int PIPELINE_KIND_WORLD_TERRAIN_SOLID = 9;
	private static final int PIPELINE_KIND_WORLD_TERRAIN_CUTOUT = 10;
	private static final int PIPELINE_KIND_WORLD_TERRAIN_TRANSLUCENT = 11;
	private static final int PIPELINE_KIND_VIGNETTE = 12;
	private static final int PIPELINE_KIND_CROSSHAIR = 13;
	private static final int PIPELINE_KIND_SUNRISE_SUNSET = 14;
	private static final int PIPELINE_KIND_CELESTIAL = 15;
	private static final int PIPELINE_KIND_STARS = 16;
	private static final int PRIMITIVE_KIND_TRIANGLE = 1;
	private static final int PRIMITIVE_KIND_TRIANGLE_STRIP = 2;
	private static final int PRIMITIVE_KIND_LINE = 3;
	private static final int PRIMITIVE_KIND_LINE_STRIP = 4;
	private static final int PRIMITIVE_KIND_POINT = 5;
	private static final int RASTER_FLAG_BLEND_ENABLED = 1 << 0;
	private static final int RASTER_FLAG_SAMPLER0_REQUIRED = 1 << 1;
	private static final int RASTER_FLAG_SAMPLER2_REQUIRED = 1 << 2;
	private static final int RASTER_FLAG_WANTS_DEPTH_TEXTURE = 1 << 3;
	private static final int RASTER_FLAG_CULL = 1 << 4;
	private static final int RASTER_FLAG_DEPTH_WRITE = 1 << 5;
	private static final String GUI_LOCATION = "minecraft:pipeline/gui";
	private static final String GUI_TEXTURED_LOCATION = "minecraft:pipeline/gui_textured";
	private static final String SKY_LOCATION = "minecraft:pipeline/sky";
	private static final String STARS_LOCATION = "minecraft:pipeline/stars";
	private static final String CELESTIAL_LOCATION = "minecraft:pipeline/celestial";
	private static final String CLOUDS_LOCATION = "minecraft:pipeline/clouds";
	private static final String ENTITY_OUTLINE_BLIT_LOCATION = "minecraft:pipeline/entity_outline_blit";
	private static final String BLUR_PIPELINE_PREFIX = "minecraft:blur/";
	private static final String LIGHTMAP_LOCATION = "minecraft:pipeline/lightmap";
	private static final String ANIMATE_SPRITE_BLIT_LOCATION = "minecraft:pipeline/animate_sprite_blit";
	private static final String ANIMATE_SPRITE_INTERPOLATE_LOCATION = "minecraft:pipeline/animate_sprite_interpolate";
	private static final String PANORAMA_LOCATION = "minecraft:pipeline/panorama";
	private static final String GUI_TEXTURED_OPAQUE_BACKGROUND_LOCATION = "minecraft:pipeline/gui_opaque_textured_background";
	private static final String GUI_TEXTURED_PREMULTIPLIED_ALPHA_LOCATION = "minecraft:pipeline/gui_textured_premultiplied_alpha";
	private static final String VIGNETTE_LOCATION = "minecraft:pipeline/vignette";
	private static final String CROSSHAIR_LOCATION = "minecraft:pipeline/crosshair";
	private static final String SUNRISE_SUNSET_LOCATION = "minecraft:pipeline/sunrise_sunset";
	private static final String SOLID_TERRAIN_LOCATION = "minecraft:pipeline/solid_terrain";
	private static final String CUTOUT_TERRAIN_LOCATION = "minecraft:pipeline/cutout_terrain";
	private static final String TRANSLUCENT_TERRAIN_LOCATION = "minecraft:pipeline/translucent_terrain";
	private static final Set<String> WORLD_OPAQUE_LOCATIONS = Set.of(
		"minecraft:pipeline/entity_solid",
		SOLID_TERRAIN_LOCATION,
		CUTOUT_TERRAIN_LOCATION,
		TRANSLUCENT_TERRAIN_LOCATION
	);
	private static final Set<String> WORLD_TERRAIN_LOCATIONS = Set.of(
		SOLID_TERRAIN_LOCATION,
		CUTOUT_TERRAIN_LOCATION,
		TRANSLUCENT_TERRAIN_LOCATION
	);
	private static final Set<String> LOGGED_WORLD_PASS_CONTEXTS = ConcurrentHashMap.newKeySet();
	private static final Set<String> LOGGED_WORLD_RESOURCE_SAMPLES = ConcurrentHashMap.newKeySet();
	private static final Set<String> LOGGED_WORLD_DRAW_INPUTS = ConcurrentHashMap.newKeySet();
	private static final Set<String> LOGGED_MAIN_COLOR_PASSES = ConcurrentHashMap.newKeySet();
	private static final Set<String> LOGGED_NONCRITICAL_SKIPS = ConcurrentHashMap.newKeySet();
	private static final Map<SyntheticIndexKey, ByteBuffer> SYNTHETIC_INDEX_CACHE = new ConcurrentHashMap<>();
	private static final float EPSILON = 0.0001F;
	private static final ByteBuffer DEFAULT_BLIT_CONFIG = directFloatBuffer(1.0F, 1.0F, 1.0F, 1.0F);
	private static final ByteBuffer EMPTY_PROJECTION_UNIFORM = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
	private static final ByteBuffer DUMMY_VERTEX_DATA = ByteBuffer.allocateDirect(1).order(ByteOrder.nativeOrder());
	private static final ByteBuffer FULLSCREEN_TRIANGLE_INDICES = directIntBuffer(0, 1, 2);

	private final MetalTexture colorTarget;
	private final int colorTargetMipLevel;
	private final MetalCommandEncoderBackend commandEncoderBackend;
	private final String passLabel;
	private final MetalPassAttachment colorAttachment;
	private final MetalPassAttachment depthAttachment;
	private final MetalTexture depthTarget;
	private final Map<String, BoundTexture> boundTextures = new HashMap<>();
	private final Map<String, GpuBufferSlice> uniformSlices = new HashMap<>();

	private RenderPipeline pipeline;
	private MetalBuffer vertexBuffer;
	private MetalBuffer indexBuffer;
	private VertexFormat.IndexType indexType;
	private boolean scissorEnabled;
	private int scissorX;
	private int scissorY;
	private int scissorWidth;
	private int scissorHeight;

	MetalRenderPassBackend(GpuTextureView colorTextureView) {
		this(null, "<direct-render-pass>", colorTextureView, null);
	}

	MetalRenderPassBackend(MetalCommandEncoderBackend commandEncoderBackend, String passLabel, GpuTextureView colorTextureView, GpuTextureView depthTextureView) {
		if (colorTextureView == null || colorTextureView.isClosed()) {
			throw new IllegalArgumentException("Metal render pass requires a live color texture view.");
		}

		if (!(colorTextureView.texture() instanceof MetalTexture metalTexture)) {
			throw new IllegalArgumentException("Metal render pass requires a Metal texture view.");
		}

		if (metalTexture.getFormat() != GpuFormat.RGBA8_UNORM) {
			throw new IllegalArgumentException("Metal render pass currently only supports RGBA8_UNORM color targets.");
		}

		if (depthTextureView != null) {
			if (depthTextureView.isClosed()) {
				throw new IllegalArgumentException("Metal render pass requires a live depth texture view.");
			}
			if (!(depthTextureView.texture() instanceof MetalTexture)) {
				throw new IllegalArgumentException("Metal render pass requires a Metal depth texture view.");
			}
		}

		this.commandEncoderBackend = commandEncoderBackend;
		this.passLabel = passLabel;
		this.colorTarget = metalTexture;
		this.colorTargetMipLevel = colorTextureView.baseMipLevel();
		this.colorAttachment = MetalPassAttachment.from(colorTextureView);
		this.depthAttachment = depthTextureView == null ? null : MetalPassAttachment.from(depthTextureView);
		this.depthTarget = depthTextureView == null ? null : (MetalTexture) depthTextureView.texture();
	}

	@Override
	public void pushDebugGroup(Supplier<String> supplier) {
	}

	@Override
	public void popDebugGroup() {
	}

	@Override
	public void setPipeline(RenderPipeline renderPipeline) {
		this.pipeline = renderPipeline;
	}

	@Override
	public void bindTexture(String name, GpuTextureView textureView, GpuSampler sampler) {
		if (textureView == null || sampler == null) {
			this.boundTextures.remove(name);
			return;
		}

		if (!(textureView.texture() instanceof MetalTexture metalTexture)) {
			throw new IllegalArgumentException("Metal render pass only supports Metal textures.");
		}

		this.boundTextures.put(name, new BoundTexture(metalTexture, textureView.baseMipLevel(), sampler));
	}

	@Override
	public void setUniform(String name, GpuBuffer gpuBuffer) {
		setUniform(name, gpuBuffer.slice());
	}

	@Override
	public void setUniform(String name, GpuBufferSlice gpuBufferSlice) {
		this.uniformSlices.put(name, gpuBufferSlice);
	}

	@Override
	public void enableScissor(int x, int y, int width, int height) {
		this.scissorEnabled = true;
		this.scissorX = x;
		this.scissorY = y;
		this.scissorWidth = width;
		this.scissorHeight = height;
	}

	@Override
	public void disableScissor() {
		this.scissorEnabled = false;
	}

	@Override
	public void setVertexBuffer(int slot, GpuBuffer gpuBuffer) {
		if (slot != 0) {
			return;
		}

		if (!(gpuBuffer instanceof MetalBuffer metalBuffer)) {
			throw new IllegalArgumentException("Metal render pass only supports Metal vertex buffers.");
		}

		this.vertexBuffer = metalBuffer;
	}

	@Override
	public void setIndexBuffer(GpuBuffer gpuBuffer, VertexFormat.IndexType indexType) {
		if (!(gpuBuffer instanceof MetalBuffer metalBuffer)) {
			throw new IllegalArgumentException("Metal render pass only supports Metal index buffers.");
		}

		this.indexBuffer = metalBuffer;
		this.indexType = indexType;
	}

	@Override
	public void drawIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount) {
		if (this.pipeline == null) {
			return;
		}
		MetalRasterPipelineSpec rasterSpec = rasterSpec(true);
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.CLOUDS_SKIP) {
			return;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.NONCRITICAL_SKIP) {
			logNonCriticalSkip("drawIndexed");
			return;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.WORLD_TERRAIN) {
			drawWorldTerrainIndexed(baseVertex, firstIndex, indexCount, instanceCount);
			return;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.WORLD_OPAQUE_UNSUPPORTED) {
			throw unsupportedPass("Metal world opaque indexed draw is not implemented yet.", "drawIndexed");
		}

		logMainColorPass("drawIndexed");
		if (instanceCount <= 0 || indexCount <= 0) {
			return;
		}

		requireDrawState();
		ByteBuffer indexStorage = this.indexBuffer.sliceStorage(0L, this.indexBuffer.size()).order(ByteOrder.nativeOrder());
		RasterDrawState drawState = prepareRasterDrawState(rasterSpec, "drawIndexed");

		withCommandContext(nativeCommandContextHandle -> drawState.metalBridge().drawRasterPass(
			nativeCommandContextHandle,
			drawState.colorTextureHandle(),
			drawState.depthTextureHandle(),
			resolveRasterShaderFamily(rasterSpec.shaderFamily()),
			resolveRasterVertexLayoutFamily(rasterSpec.vertexLayoutFamily()),
			resolveRasterPrimitiveTopology(rasterSpec.primitiveTopology()),
			rasterSpec.colorWriteMask(),
			resolveRasterFlags(rasterSpec),
			resolveRasterCompareFunction(rasterSpec.depthCompare()),
			rasterSpec.depthBiasScaleFactor(),
			rasterSpec.depthBiasConstant(),
			resolveRasterBlendOperation(rasterSpec.blendState().colorOperation()),
			resolveRasterBlendOperation(rasterSpec.blendState().alphaOperation()),
			resolveRasterBlendFactor(rasterSpec.blendState().sourceColorFactor()),
			resolveRasterBlendFactor(rasterSpec.blendState().destinationColorFactor()),
			resolveRasterBlendFactor(rasterSpec.blendState().sourceAlphaFactor()),
			resolveRasterBlendFactor(rasterSpec.blendState().destinationAlphaFactor()),
			drawState.vertexData(),
			drawState.vertexStride(),
			baseVertex,
			indexStorage,
			this.indexType.bytes,
			firstIndex,
			indexCount,
			drawState.projectionData(),
			drawState.dynamicTransformsData(),
			drawState.sampler0TextureHandle(),
			drawState.linearFiltering(),
			drawState.repeatU(),
			drawState.repeatV(),
			this.scissorEnabled,
			this.scissorX,
			this.scissorY,
			this.scissorWidth,
			this.scissorHeight
		));
	}

	@Override
	public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, Collection<String> collection, T object) {
		GpuBuffer sharedIndexBuffer = gpuBuffer;
		VertexFormat.IndexType sharedIndexType = indexType;
		if (sharedIndexBuffer != null && sharedIndexType != null) {
			setIndexBuffer(sharedIndexBuffer, sharedIndexType);
		}

		for (RenderPass.Draw<T> draw : draws) {
			setVertexBuffer(draw.slot(), draw.vertexBuffer());
			if (sharedIndexBuffer == null || sharedIndexType == null) {
				setIndexBuffer(draw.indexBuffer(), draw.indexType());
			}
			if (draw.uniformUploaderConsumer() != null) {
				draw.uniformUploaderConsumer().accept(object, this::setUniform);
			}
			if (isWorldTerrainPipeline()) {
				drawWorldTerrainIndexed(draw.baseVertex(), draw.firstIndex(), draw.indexCount(), 1);
			} else {
				drawIndexed(draw.baseVertex(), draw.firstIndex(), draw.indexCount(), 1);
			}
		}
	}

	@Override
	public void draw(int firstVertex, int vertexCount) {
		if (this.pipeline == null) {
			return;
		}

		String pipelineLocation = this.pipeline.getLocation().toString();
		MetalRasterPipelineSpec rasterSpec = rasterSpec(false);
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.ANIMATE_SPRITE) {
			drawAnimateSpriteBlit();
			return;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.CLOUDS_SKIP) {
			return;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.NONCRITICAL_SKIP) {
			logNonCriticalSkip("draw");
			return;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.BLUR) {
			drawBlurPostPass();
			return;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.LIGHTMAP) {
			drawLightmapPass();
			return;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.POST_BLIT) {
			drawPostBlitPass();
			return;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.WORLD_OPAQUE_UNSUPPORTED) {
			throw unsupportedPass("Metal world draw without indices is not implemented yet.", "draw");
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.WORLD_TERRAIN) {
			throw unsupportedPass("Metal world terrain currently requires indexed draws.", "draw");
		}

		logMainColorPass("draw");

		if (vertexCount <= 0) {
			return;
		}

		if (this.vertexBuffer == null || this.vertexBuffer.size() == 0L) {
			return;
		}

		RasterDrawState drawState = prepareRasterDrawState(rasterSpec, "draw");
		ByteBuffer syntheticIndices = syntheticIndices(vertexCount, this.pipeline.getVertexFormatMode());
		if (syntheticIndices == null || !syntheticIndices.hasRemaining()) {
			return;
		}
		int syntheticIndexCount = syntheticIndices.remaining() / Integer.BYTES;

		withCommandContext(nativeCommandContextHandle -> drawState.metalBridge().drawRasterPass(
			nativeCommandContextHandle,
			drawState.colorTextureHandle(),
			drawState.depthTextureHandle(),
			resolveRasterShaderFamily(rasterSpec.shaderFamily()),
			resolveRasterVertexLayoutFamily(rasterSpec.vertexLayoutFamily()),
			resolveRasterPrimitiveTopology(rasterSpec.primitiveTopology()),
			rasterSpec.colorWriteMask(),
			resolveRasterFlags(rasterSpec),
			resolveRasterCompareFunction(rasterSpec.depthCompare()),
			rasterSpec.depthBiasScaleFactor(),
			rasterSpec.depthBiasConstant(),
			resolveRasterBlendOperation(rasterSpec.blendState().colorOperation()),
			resolveRasterBlendOperation(rasterSpec.blendState().alphaOperation()),
			resolveRasterBlendFactor(rasterSpec.blendState().sourceColorFactor()),
			resolveRasterBlendFactor(rasterSpec.blendState().destinationColorFactor()),
			resolveRasterBlendFactor(rasterSpec.blendState().sourceAlphaFactor()),
			resolveRasterBlendFactor(rasterSpec.blendState().destinationAlphaFactor()),
			drawState.vertexData(),
			drawState.vertexStride(),
			Math.max(0, firstVertex),
			syntheticIndices,
			Integer.BYTES,
			0,
			syntheticIndexCount,
			drawState.projectionData(),
			drawState.dynamicTransformsData(),
			drawState.sampler0TextureHandle(),
			drawState.linearFiltering(),
			drawState.repeatU(),
			drawState.repeatV(),
			this.scissorEnabled,
			this.scissorX,
			this.scissorY,
			this.scissorWidth,
			this.scissorHeight
		));
	}

	private void drawAnimateSpriteBlit() {
		BoundTexture sprite = this.boundTextures.get("Sprite");
		if (sprite == null) {
			return;
		}

		GpuBufferSlice spriteAnimationInfo = this.uniformSlices.get("SpriteAnimationInfo");
		if (spriteAnimationInfo == null) {
			return;
		}

		ByteBuffer uniformData = sliceUniformBuffer(spriteAnimationInfo);
		int sourceMipLevel = uniformData.getInt(136);
		float uPadding = uniformData.getFloat(128);
		float vPadding = uniformData.getFloat(132);
		float targetScaleX = uniformData.getFloat(64);
		float targetScaleY = uniformData.getFloat(64 + (5 * Float.BYTES));
		float targetX = uniformData.getFloat(64 + (12 * Float.BYTES));
		float targetY = uniformData.getFloat(64 + (13 * Float.BYTES));
		int targetWidth = Math.max(1, Math.round(targetScaleX));
		int targetHeight = Math.max(1, Math.round(targetScaleY));
		int unclippedStartX = Math.round(targetX);
		int unclippedStartY = Math.round(targetY);
		int atlasWidth = this.colorTarget.getWidth(this.colorTargetMipLevel);
		int atlasHeight = this.colorTarget.getHeight(this.colorTargetMipLevel);
		int startX = Math.max(0, unclippedStartX);
		int startY = Math.max(0, unclippedStartY);
		int endX = Math.min(atlasWidth, unclippedStartX + targetWidth);
		int endY = Math.min(atlasHeight, unclippedStartY + targetHeight);
		if (startX >= endX || startY >= endY) {
			return;
		}

		if (!this.colorTarget.hasNativeTextureHandle()) {
			throw new IllegalStateException("Metal animated sprite blit requires a native-backed atlas target texture.");
		}
		if (!sprite.texture.hasNativeTextureHandle()) {
			throw new IllegalStateException("Metal animated sprite blit requires a native-backed sprite source texture.");
		}

		MetalBridge metalBridge = this.colorTarget.metalBridge();
		if (metalBridge == null) {
			throw new IllegalStateException("Metal animated sprite blit requires an active native bridge.");
		}

		float localUMin = (float) (startX - unclippedStartX) / (float) targetWidth;
		float localVMin = (float) (startY - unclippedStartY) / (float) targetHeight;
		float localUMax = (float) (endX - unclippedStartX) / (float) targetWidth;
		float localVMax = (float) (endY - unclippedStartY) / (float) targetHeight;
		MetalSampler sampler = (MetalSampler) sprite.sampler;
		withCommandContext(nativeCommandContextHandle -> metalBridge.blitAnimatedSprite(
			nativeCommandContextHandle,
			this.colorTarget.nativeTextureHandle(),
			this.colorTargetMipLevel,
			sprite.texture.nativeTextureHandle(),
			sourceMipLevel,
			startX,
			startY,
			endX - startX,
			endY - startY,
			localUMin,
			localVMin,
			localUMax,
			localVMax,
			uPadding,
			vPadding,
			sampler.getMagFilter() == FilterMode.LINEAR || sampler.getMinFilter() == FilterMode.LINEAR,
			sampler.getAddressModeU() == AddressMode.REPEAT,
			sampler.getAddressModeV() == AddressMode.REPEAT
		));
	}

	private void drawBlurPostPass() {
		BoundTexture input = this.boundTextures.get("InSampler");
		if (input == null) {
			throw unsupportedPass("Metal blur post pass requires InSampler.", "draw");
		}
		if (input.mipLevel != 0 || this.colorTargetMipLevel != 0) {
			throw unsupportedPass("Metal blur post pass currently requires mip level 0 textures.", "draw");
		}

		MetalTexture source = input.texture;
		int width = source.getWidth(input.mipLevel);
		int height = source.getHeight(input.mipLevel);
		if (width != this.colorTarget.getWidth(this.colorTargetMipLevel) || height != this.colorTarget.getHeight(this.colorTargetMipLevel)) {
			throw unsupportedPass("Metal blur post pass currently requires matching source and target sizes.", "draw");
		}

		ByteBuffer sourcePixels = source.readRegion(input.mipLevel, 0, 0, width, height).order(ByteOrder.nativeOrder());
		this.colorTarget.writeRegion(sourcePixels, source.getFormat().pixelSize(), width, this.colorTargetMipLevel, 0, 0, width, height, 0, 0);
	}

	private void drawLightmapPass() {
		GpuBufferSlice lightmapInfo = this.uniformSlices.get("LightmapInfo");
		if (lightmapInfo == null) {
			throw unsupportedPass("Metal lightmap pass requires LightmapInfo.", "draw");
		}
		if (this.colorTargetMipLevel != 0) {
			throw unsupportedPass("Metal lightmap pass currently requires mip level 0 target.", "draw");
		}

		ByteBuffer lightmapData = sliceUniformBuffer(lightmapInfo);
		float skyFactor = lightmapData.getFloat(0);
		float blockFactor = lightmapData.getFloat(4);
		float nightVisionFactor = lightmapData.getFloat(8);
		float darknessScale = lightmapData.getFloat(12);
		float bossOverlayDarkeningFactor = lightmapData.getFloat(16);
		float brightnessFactor = lightmapData.getFloat(20);
		float blockLightTintX = lightmapData.getFloat(32);
		float blockLightTintY = lightmapData.getFloat(36);
		float blockLightTintZ = lightmapData.getFloat(40);
		float skyLightColorX = lightmapData.getFloat(48);
		float skyLightColorY = lightmapData.getFloat(52);
		float skyLightColorZ = lightmapData.getFloat(56);
		float ambientColorX = lightmapData.getFloat(64);
		float ambientColorY = lightmapData.getFloat(68);
		float ambientColorZ = lightmapData.getFloat(72);
		float nightVisionColorX = lightmapData.getFloat(80);
		float nightVisionColorY = lightmapData.getFloat(84);
		float nightVisionColorZ = lightmapData.getFloat(88);
		int width = this.colorTarget.getWidth(this.colorTargetMipLevel);
		int height = this.colorTarget.getHeight(this.colorTargetMipLevel);
		ByteBuffer output = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());

		for (int y = 0; y < height; y++) {
			float skyLevel = (float) y / 15.0F;
			float skyBrightness = lightmapBrightness(skyLevel) * skyFactor;
			for (int x = 0; x < width; x++) {
				float blockLevel = (float) x / 15.0F;
				float blockBrightness = lightmapBrightness(blockLevel) * blockFactor;
				float nightVisionRed = nightVisionColorX * nightVisionFactor;
				float nightVisionGreen = nightVisionColorY * nightVisionFactor;
				float nightVisionBlue = nightVisionColorZ * nightVisionFactor;
				float colorRed = Math.max(ambientColorX, nightVisionRed);
				float colorGreen = Math.max(ambientColorY, nightVisionGreen);
				float colorBlue = Math.max(ambientColorZ, nightVisionBlue);

				colorRed += skyLightColorX * skyBrightness;
				colorGreen += skyLightColorY * skyBrightness;
				colorBlue += skyLightColorZ * skyBrightness;

				float parabolicMixFactor = lightmapParabolicMixFactor(blockLevel);
				float blockLightColorRed = lerp(0.9F * parabolicMixFactor, blockLightTintX, 1.0F);
				float blockLightColorGreen = lerp(0.9F * parabolicMixFactor, blockLightTintY, 1.0F);
				float blockLightColorBlue = lerp(0.9F * parabolicMixFactor, blockLightTintZ, 1.0F);
				colorRed += blockLightColorRed * blockBrightness;
				colorGreen += blockLightColorGreen * blockBrightness;
				colorBlue += blockLightColorBlue * blockBrightness;

				colorRed = lerp(bossOverlayDarkeningFactor, colorRed, colorRed * 0.7F) - darknessScale;
				colorGreen = lerp(bossOverlayDarkeningFactor, colorGreen, colorGreen * 0.6F) - darknessScale;
				colorBlue = lerp(bossOverlayDarkeningFactor, colorBlue, colorBlue * 0.6F) - darknessScale;

				colorRed = clamp(colorRed, 0.0F, 1.0F);
				colorGreen = clamp(colorGreen, 0.0F, 1.0F);
				colorBlue = clamp(colorBlue, 0.0F, 1.0F);

				float notGammaRed = lightmapNotGamma(colorRed, colorGreen, colorBlue, colorRed);
				float notGammaGreen = lightmapNotGamma(colorRed, colorGreen, colorBlue, colorGreen);
				float notGammaBlue = lightmapNotGamma(colorRed, colorGreen, colorBlue, colorBlue);
				colorRed = lerp(brightnessFactor, colorRed, notGammaRed);
				colorGreen = lerp(brightnessFactor, colorGreen, notGammaGreen);
				colorBlue = lerp(brightnessFactor, colorBlue, notGammaBlue);

				int pixelOffset = ((y * width) + x) * 4;
				output.put(pixelOffset, packColor(colorRed));
				output.put(pixelOffset + 1, packColor(colorGreen));
				output.put(pixelOffset + 2, packColor(colorBlue));
				output.put(pixelOffset + 3, (byte) 0xFF);
			}
		}

		this.colorTarget.writeRegion(output, 4, width, this.colorTargetMipLevel, 0, 0, width, height, 0, 0);
	}

	private void drawPostBlitPass() {
		BoundTexture boundInput = this.boundTextures.get("InSampler");
		if (boundInput == null) {
			boundInput = this.boundTextures.get("In");
		}
		if (boundInput == null) {
			throw unsupportedPass("Metal post blit pass requires an input sampler.", "draw");
		}

		MetalBridge metalBridge = this.colorTarget.metalBridge();
		if (metalBridge == null || !this.colorTarget.hasNativeTextureHandle()) {
			throw unsupportedPass("Metal post blit pass requires a native-backed color target.", "draw");
		}
		if (!boundInput.texture.hasNativeTextureHandle()) {
			throw unsupportedPass("Metal post blit pass requires a native-backed input texture.", "draw");
		}

		GpuBufferSlice blitConfig = this.uniformSlices.get("BlitConfig");
		ByteBuffer blitConfigData = blitConfig == null
			? duplicateDirectBuffer(DEFAULT_BLIT_CONFIG)
			: sliceUniformBuffer(blitConfig);
		BoundTexture input = boundInput;

		withCommandContext(nativeCommandContextHandle -> metalBridge.drawGuiPass(
			nativeCommandContextHandle,
			this.colorTarget.nativeTextureHandle(),
			PIPELINE_KIND_POST_BLIT,
			PRIMITIVE_KIND_TRIANGLE,
			duplicateDirectBuffer(DUMMY_VERTEX_DATA),
			1,
			0,
			duplicateDirectBuffer(FULLSCREEN_TRIANGLE_INDICES),
			Integer.BYTES,
			0,
			3,
			duplicateDirectBuffer(EMPTY_PROJECTION_UNIFORM),
			blitConfigData,
			input.nativeTextureHandle(),
			input.linearFiltering(),
			input.repeatU(),
			input.repeatV(),
			false,
			0,
			0,
			0,
			0
		));
	}

	@Override
	public void writeTimestamp(GpuQueryPool gpuQueryPool, int index) {
	}

	String passLabel() {
		return this.passLabel;
	}

	MetalPassAttachment colorAttachment() {
		return this.colorAttachment;
	}

	MetalPassAttachment depthAttachment() {
		return this.depthAttachment;
	}

	private boolean isWorldOpaquePipeline() {
		return this.pipeline != null && WORLD_OPAQUE_LOCATIONS.contains(this.pipeline.getLocation().toString());
	}

	private boolean isWorldTerrainPipeline() {
		return this.pipeline != null && WORLD_TERRAIN_LOCATIONS.contains(this.pipeline.getLocation().toString());
	}

	private void drawWorldTerrainIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount) {
		if (instanceCount <= 0 || indexCount <= 0) {
			return;
		}
		MetalPassContext context = passContext("drawIndexed");
		logWorldPass(context);
		if (this.depthAttachment == null || this.depthTarget == null) {
			throw unsupportedPass("Metal world terrain draw requires a depth attachment.", "drawIndexed");
		}
		if (this.vertexBuffer == null || this.vertexBuffer.size() == 0L) {
			throw unsupportedPass("Metal world terrain draw requires a vertex buffer before drawing.", "drawIndexed");
		}
		if (this.indexBuffer == null || this.indexType == null) {
			throw unsupportedPass("Metal world terrain draw requires an index buffer before drawing.", "drawIndexed");
		}
		if (!this.colorTarget.hasNativeTextureHandle()) {
			throw unsupportedPass("Metal world terrain draw requires a native-backed color target.", "drawIndexed");
		}
		if (!this.depthTarget.hasNativeTextureHandle()) {
			throw unsupportedPass("Metal world terrain draw requires a native-backed depth target.", "drawIndexed");
		}
		BoundTexture sampler0 = this.boundTextures.get("Sampler0");
		if (sampler0 == null || !sampler0.texture.hasNativeTextureHandle()) {
			throw unsupportedPass("Metal world terrain draw requires a native-backed Sampler0 texture.", "drawIndexed");
		}
		BoundTexture sampler2 = this.boundTextures.get("Sampler2");
		if (sampler2 == null || !sampler2.texture.hasNativeTextureHandle()) {
			throw unsupportedPass("Metal world terrain draw requires a native-backed Sampler2 lightmap texture.", "drawIndexed");
		}

		ByteBuffer projectionData = sliceUniformBuffer(requireUniform("Projection"));
		ByteBuffer worldData = buildWorldTerrainUniformData();
		MetalBridge metalBridge = this.colorTarget.metalBridge();
		if (metalBridge == null) {
			throw unsupportedPass("Metal world terrain draw requires an active native bridge.", "drawIndexed");
		}
		if (!this.vertexBuffer.hasNativeBufferHandle()) {
			throw unsupportedPass("Metal world terrain draw requires a native-backed vertex buffer.", "drawIndexed");
		}
		if (!this.indexBuffer.hasNativeBufferHandle()) {
			throw unsupportedPass("Metal world terrain draw requires a native-backed index buffer.", "drawIndexed");
		}
		logWorldResources(context, sampler0, sampler2);
		logWorldTerrainInputs(context, sampler0, projectionData, worldData, baseVertex, firstIndex, indexCount);
		MetalSampler sampler = (MetalSampler) sampler0.sampler;
		MetalSampler lightmapSampler = (MetalSampler) sampler2.sampler;
		float depthClearValue = this.depthTarget.consumePendingDepthClearValue(this.depthAttachment.mipLevel());
		boolean clearDepth = !Float.isNaN(depthClearValue);

		try {
			withCommandContext(nativeCommandContextHandle -> metalBridge.drawWorldPass(
				nativeCommandContextHandle,
					this.colorTarget.nativeTextureHandle(),
					this.depthTarget.nativeTextureHandle(),
					clearDepth,
					depthClearValue,
					resolveWorldTerrainPipelineKind(),
				this.vertexBuffer.nativeBufferHandle(),
				this.pipeline.getVertexFormat().getVertexSize(),
				baseVertex,
				this.indexBuffer.nativeBufferHandle(),
				this.indexType.bytes,
				firstIndex,
				indexCount,
				projectionData,
				worldData,
				sampler0.texture.nativeTextureHandle(),
				sampler2.texture.nativeTextureHandle(),
				sampler.getMagFilter() == FilterMode.LINEAR || sampler.getMinFilter() == FilterMode.LINEAR,
				sampler.getAddressModeU() == AddressMode.REPEAT,
				sampler.getAddressModeV() == AddressMode.REPEAT,
				lightmapSampler.getMagFilter() == FilterMode.LINEAR || lightmapSampler.getMinFilter() == FilterMode.LINEAR,
				lightmapSampler.getAddressModeU() == AddressMode.REPEAT,
				lightmapSampler.getAddressModeV() == AddressMode.REPEAT
			));
		} catch (MetalUnsupportedPassException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			String reason = exception.getMessage();
			throw new MetalUnsupportedPassException(
				reason == null ? "Metal world terrain draw failed." : reason,
				context
			);
		}
	}

	private void rasterizeTriangle(ByteBuffer targetStorage, VertexLayout layout, Vertex vertex0, Vertex vertex1, Vertex vertex2) {
		float area = edgeFunction(vertex0.x, vertex0.y, vertex1.x, vertex1.y, vertex2.x, vertex2.y);
		if (Math.abs(area) <= EPSILON) {
			return;
		}

		int targetWidth = this.colorTarget.getWidth(this.colorTargetMipLevel);
		int targetHeight = this.colorTarget.getHeight(this.colorTargetMipLevel);
		int minX = clamp((int) Math.floor(Math.min(vertex0.x, Math.min(vertex1.x, vertex2.x))), 0, targetWidth - 1);
		int maxX = clamp((int) Math.ceil(Math.max(vertex0.x, Math.max(vertex1.x, vertex2.x))), 0, targetWidth - 1);
		int minY = clamp((int) Math.floor(Math.min(vertex0.y, Math.min(vertex1.y, vertex2.y))), 0, targetHeight - 1);
		int maxY = clamp((int) Math.ceil(Math.max(vertex0.y, Math.max(vertex1.y, vertex2.y))), 0, targetHeight - 1);

		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				if (!isInsideScissor(x, y)) {
					continue;
				}

				float sampleX = x + 0.5F;
				float sampleY = y + 0.5F;
				float weight0 = edgeFunction(vertex1.x, vertex1.y, vertex2.x, vertex2.y, sampleX, sampleY) / area;
				float weight1 = edgeFunction(vertex2.x, vertex2.y, vertex0.x, vertex0.y, sampleX, sampleY) / area;
				float weight2 = edgeFunction(vertex0.x, vertex0.y, vertex1.x, vertex1.y, sampleX, sampleY) / area;

				if (!contains(weight0, weight1, weight2)) {
					continue;
				}

				float red = interpolate(weight0, weight1, weight2, vertex0.red, vertex1.red, vertex2.red);
				float green = interpolate(weight0, weight1, weight2, vertex0.green, vertex1.green, vertex2.green);
				float blue = interpolate(weight0, weight1, weight2, vertex0.blue, vertex1.blue, vertex2.blue);
				float alpha = interpolate(weight0, weight1, weight2, vertex0.alpha, vertex1.alpha, vertex2.alpha);

				if (layout.hasTexture) {
					float u = interpolate(weight0, weight1, weight2, vertex0.u, vertex1.u, vertex2.u);
					float v = interpolate(weight0, weight1, weight2, vertex0.v, vertex1.v, vertex2.v);
					SampledColor sampledColor = sampleTexture(this.boundTextures.get("Sampler0"), u, v);
					red *= sampledColor.red;
					green *= sampledColor.green;
					blue *= sampledColor.blue;
					alpha *= sampledColor.alpha;
				}

				blendPixel(targetStorage, targetWidth, x, y, red, green, blue, alpha);
			}
		}
	}

	private Vertex readVertex(ByteBuffer vertexStorage, VertexLayout layout, int vertexIndex) {
		int vertexOffset = vertexIndex * layout.stride;
		float x = vertexStorage.getFloat(vertexOffset + layout.positionOffset);
		float y = vertexStorage.getFloat(vertexOffset + layout.positionOffset + Float.BYTES);
		float u = layout.uvOffset >= 0 ? vertexStorage.getFloat(vertexOffset + layout.uvOffset) : 0.0F;
		float v = layout.uvOffset >= 0 ? vertexStorage.getFloat(vertexOffset + layout.uvOffset + Float.BYTES) : 0.0F;
		float red = 1.0F;
		float green = 1.0F;
		float blue = 1.0F;
		float alpha = 1.0F;

		if (layout.colorOffset >= 0) {
			red = unpackColor(vertexStorage.get(vertexOffset + layout.colorOffset));
			green = unpackColor(vertexStorage.get(vertexOffset + layout.colorOffset + 1));
			blue = unpackColor(vertexStorage.get(vertexOffset + layout.colorOffset + 2));
			alpha = unpackColor(vertexStorage.get(vertexOffset + layout.colorOffset + 3));
		}

		return new Vertex(x, y, u, v, red, green, blue, alpha);
	}

	private SampledColor sampleTexture(BoundTexture boundTexture, float u, float v) {
		if (boundTexture == null) {
			return SampledColor.WHITE;
		}

		return sampleTexture(boundTexture.texture, boundTexture.mipLevel, (MetalSampler) boundTexture.sampler, u, v);
	}

	private SampledColor sampleTexture(MetalTexture texture, int mipLevel, MetalSampler sampler, float u, float v) {
		int textureWidth = texture.getWidth(mipLevel);
		int textureHeight = texture.getHeight(mipLevel);
		ByteBuffer textureStorage = texture.snapshotStorage(mipLevel).order(ByteOrder.nativeOrder());
		float wrappedU = wrap(u, sampler.getAddressModeU());
		float wrappedV = wrap(v, sampler.getAddressModeV());

		if (sampler.getMagFilter() == FilterMode.LINEAR || sampler.getMinFilter() == FilterMode.LINEAR) {
			return sampleLinear(textureStorage, textureWidth, textureHeight, wrappedU, wrappedV);
		}

		int x = clamp(Math.round(wrappedU * (textureWidth - 1)), 0, textureWidth - 1);
		int y = clamp(Math.round(wrappedV * (textureHeight - 1)), 0, textureHeight - 1);
		return readTexel(textureStorage, textureWidth, x, y);
	}

	private SampledColor sampleLinear(ByteBuffer textureStorage, int textureWidth, int textureHeight, float u, float v) {
		float texelX = u * (textureWidth - 1);
		float texelY = v * (textureHeight - 1);
		int x0 = clamp((int) Math.floor(texelX), 0, textureWidth - 1);
		int y0 = clamp((int) Math.floor(texelY), 0, textureHeight - 1);
		int x1 = clamp(x0 + 1, 0, textureWidth - 1);
		int y1 = clamp(y0 + 1, 0, textureHeight - 1);
		float tx = texelX - x0;
		float ty = texelY - y0;

		SampledColor c00 = readTexel(textureStorage, textureWidth, x0, y0);
		SampledColor c10 = readTexel(textureStorage, textureWidth, x1, y0);
		SampledColor c01 = readTexel(textureStorage, textureWidth, x0, y1);
		SampledColor c11 = readTexel(textureStorage, textureWidth, x1, y1);

		return new SampledColor(
			lerp(ty, lerp(tx, c00.red, c10.red), lerp(tx, c01.red, c11.red)),
			lerp(ty, lerp(tx, c00.green, c10.green), lerp(tx, c01.green, c11.green)),
			lerp(ty, lerp(tx, c00.blue, c10.blue), lerp(tx, c01.blue, c11.blue)),
			lerp(ty, lerp(tx, c00.alpha, c10.alpha), lerp(tx, c01.alpha, c11.alpha))
		);
	}

	private SampledColor readTexel(ByteBuffer textureStorage, int textureWidth, int x, int y) {
		int pixelOffset = ((y * textureWidth) + x) * 4;
		return new SampledColor(
			unpackColor(textureStorage.get(pixelOffset)),
			unpackColor(textureStorage.get(pixelOffset + 1)),
			unpackColor(textureStorage.get(pixelOffset + 2)),
			unpackColor(textureStorage.get(pixelOffset + 3))
		);
	}

	private void blendPixel(ByteBuffer targetStorage, int targetWidth, int x, int y, float red, float green, float blue, float alpha) {
		if (alpha <= 0.0F) {
			return;
		}

		int pixelOffset = ((y * targetWidth) + x) * 4;
		float destinationRed = unpackColor(targetStorage.get(pixelOffset));
		float destinationGreen = unpackColor(targetStorage.get(pixelOffset + 1));
		float destinationBlue = unpackColor(targetStorage.get(pixelOffset + 2));
		float destinationAlpha = unpackColor(targetStorage.get(pixelOffset + 3));
		float inverseAlpha = 1.0F - alpha;
		float outputAlpha = alpha + (destinationAlpha * inverseAlpha);
		float outputRed = red * alpha + destinationRed * inverseAlpha;
		float outputGreen = green * alpha + destinationGreen * inverseAlpha;
		float outputBlue = blue * alpha + destinationBlue * inverseAlpha;

		targetStorage.put(pixelOffset, packColor(outputRed));
		targetStorage.put(pixelOffset + 1, packColor(outputGreen));
		targetStorage.put(pixelOffset + 2, packColor(outputBlue));
		targetStorage.put(pixelOffset + 3, packColor(outputAlpha));
	}

	private boolean isInsideScissor(int x, int y) {
		if (!this.scissorEnabled) {
			return true;
		}

		return x >= this.scissorX
			&& y >= this.scissorY
			&& x < this.scissorX + this.scissorWidth
			&& y < this.scissorY + this.scissorHeight;
	}

	private void requireDrawState() {
		if (this.pipeline == null) {
			throw new IllegalStateException("Metal render pass requires a pipeline before drawing.");
		}

		if (this.vertexBuffer == null) {
			throw new IllegalStateException("Metal render pass requires a vertex buffer before drawing.");
		}

		if (this.indexBuffer == null || this.indexType == null) {
			throw new IllegalStateException("Metal render pass requires an index buffer before drawing.");
		}
	}

	private MetalPassContext passContext(String drawType) {
		return new MetalPassContext(
			this.passLabel,
			this.pipeline == null ? "<unset>" : this.pipeline.getLocation().toString(),
			this.colorAttachment,
			this.depthAttachment,
			drawType,
			this.pipeline == null ? null : this.pipeline.getVertexFormat(),
			this.indexType,
			this.colorTarget.hasNativeTextureHandle()
		);
	}

	private void logWorldPass(MetalPassContext context) {
		if (!WORLD_OPAQUE_LOCATIONS.contains(context.pipelineLocation())) {
			return;
		}

		String description = context.describe();
		if (LOGGED_WORLD_PASS_CONTEXTS.add(description)) {
			MetalExpMod.LOGGER.info("[MetalExp][world-pass] {}", description);
		}
	}

	private void logMainColorPass(String drawType) {
		if (!"Main / Color".equals(this.colorTarget.getLabel()) || this.pipeline == null) {
			return;
		}

		String description = this.passLabel + "|" + this.pipeline.getLocation() + "|" + drawType;
		if (!LOGGED_MAIN_COLOR_PASSES.add(description)) {
			return;
		}

		MetalExpMod.LOGGER.info(
			"[MetalExp][main-color-pass] passLabel={}, pipeline={}, drawType={}, colorAttachment={}, depthAttachment={}",
			this.passLabel,
			this.pipeline.getLocation(),
			drawType,
			this.colorAttachment.describe(),
			this.depthAttachment == null ? "NONE" : this.depthAttachment.describe()
		);
	}

	private void logNonCriticalSkip(String drawType) {
		MetalPassContext context = passContext(drawType);
		String description = context.describe();
		if (!LOGGED_NONCRITICAL_SKIPS.add(description)) {
			return;
		}

		MetalExpMod.LOGGER.warn("[MetalExp][skip] non-critical pipeline temporarily gated on Metal: {}", description);
	}

	private void logWorldResources(MetalPassContext context, BoundTexture sampler0, BoundTexture sampler2) {
		String description = sampler0.texture.getLabel() + "|" + sampler2.texture.getLabel();
		if (!LOGGED_WORLD_RESOURCE_SAMPLES.add(description)) {
			return;
		}

		DebugPixel atlasPixel = findDebugPixel(sampler0.texture, sampler0.mipLevel);
		DebugPixel lightmapPixel = findDebugPixel(sampler2.texture, sampler2.mipLevel);
		MetalExpMod.LOGGER.info(
			"[MetalExp][world-inputs] passLabel={}, pipeline={}, sampler0CpuMirror={} {}, sampler2CpuMirror={} {}",
			context.passLabel(),
			context.pipelineLocation(),
			sampler0.texture.getLabel(),
			atlasPixel,
			sampler2.texture.getLabel(),
			lightmapPixel
		);
	}

	private void logWorldTerrainInputs(MetalPassContext context, BoundTexture sampler0, ByteBuffer projectionData, ByteBuffer worldData, int baseVertex, int firstIndex, int indexCount) {
		String description = context.pipelineLocation();
		if (!LOGGED_WORLD_DRAW_INPUTS.add(description)) {
			return;
		}

		IndexedWorldVertex firstVertex = firstIndexedWorldVertex(baseVertex, firstIndex);
		DebugPixel nativeSampler0Pixel = sampleNativeTextureAtUv(sampler0.texture, sampler0.mipLevel, firstVertex);
		ClipSpacePosition clipSpacePosition = projectWorldVertex(projectionData, worldData, firstVertex);
		ClipSpacePosition transposedModelViewClip = projectWorldVertex(projectionData, worldData, firstVertex, true, false);
		ClipSpacePosition transposedBothClip = projectWorldVertex(projectionData, worldData, firstVertex, true, true);
		MetalExpMod.LOGGER.info(
			"[MetalExp][world-draw] passLabel={}, pipeline={}, indexCount={}, chunkVisibility={}, fogColor=({},{},{},{}), fogRange=({},{},{},{}), textureSize=({},{}) useRgss={}, chunkPos=({},{},{}), cameraBlockPos=({},{},{}), cameraOffset=({},{},{}), firstVertex={}, firstVertexClip={}, firstVertexClipModelViewT={}, firstVertexClipBothT={}, sampler0NativeAtVertexUv={}",
			context.passLabel(),
			context.pipelineLocation(),
			indexCount,
			worldData.getFloat(92),
			worldData.getFloat(64),
			worldData.getFloat(68),
			worldData.getFloat(72),
			worldData.getFloat(76),
			worldData.getFloat(128),
			worldData.getFloat(132),
			worldData.getFloat(136),
			worldData.getFloat(140),
			worldData.getFloat(144),
			worldData.getFloat(148),
			worldData.getFloat(152),
			worldData.getFloat(80),
			worldData.getFloat(84),
			worldData.getFloat(88),
			worldData.getFloat(96),
			worldData.getFloat(100),
			worldData.getFloat(104),
			worldData.getFloat(112),
			worldData.getFloat(116),
			worldData.getFloat(120),
			firstVertex,
			clipSpacePosition,
			transposedModelViewClip,
			transposedBothClip,
			nativeSampler0Pixel == null ? "unavailable" : nativeSampler0Pixel
		);
	}

	private MetalUnsupportedPassException unsupportedPass(String reason, String drawType) {
		MetalPassContext context = passContext(drawType);
		logWorldPass(context);
		return new MetalUnsupportedPassException(reason, context);
	}

	private long nativeRasterDepthTextureHandle(MetalRasterPipelineSpec rasterSpec, String drawType) {
		if (!usesRasterDepthAttachment(rasterSpec)) {
			return 0L;
		}
		if (this.depthTarget == null) {
			return 0L;
		}
		if (!this.depthTarget.hasNativeTextureHandle()) {
			if (rasterSpec.wantsDepthTexture() || rasterSpec.depthWrite()) {
				throw unsupportedPass("Metal raster draw requires a native-backed depth texture.", drawType);
			}
			return 0L;
		}
		return this.depthTarget.nativeTextureHandle();
	}

	private static boolean usesRasterDepthAttachment(MetalRasterPipelineSpec rasterSpec) {
		return rasterSpec.shaderFamily() == MetalRasterPipelineSpec.ShaderFamily.LINE
			|| rasterSpec.wantsDepthTexture()
			|| rasterSpec.depthWrite();
	}

	private static int resolveRasterShaderFamily(MetalRasterPipelineSpec.ShaderFamily shaderFamily) {
		return switch (shaderFamily) {
			case GUI_COLOR -> 1;
			case GUI_TEXTURED -> 2;
			case PANORAMA -> 3;
			case SKY -> 4;
			case STARS -> 5;
			case POSITION_COLOR -> 6;
			case POSITION_TEX -> 7;
			case LINE -> 8;
			case UNKNOWN -> 0;
		};
	}

	private static int resolveRasterVertexLayoutFamily(MetalRasterPipelineSpec.VertexLayoutFamily vertexLayoutFamily) {
		return switch (vertexLayoutFamily) {
			case POSITION -> 1;
			case POSITION_COLOR -> 2;
			case POSITION_TEX -> 3;
			case POSITION_TEX_COLOR -> 4;
			case POSITION_COLOR_NORMAL_LINE_WIDTH -> 5;
			case BLOCK -> 6;
			case UNKNOWN -> 0;
		};
	}

	private static int resolveRasterPrimitiveTopology(MetalRasterPipelineSpec.PrimitiveTopology primitiveTopology) {
		return switch (primitiveTopology) {
			case TRIANGLE -> 1;
			case TRIANGLE_STRIP -> 2;
			case LINE -> 3;
			case LINE_STRIP -> 4;
			case POINT -> 5;
		};
	}

	private static int resolveRasterFlags(MetalRasterPipelineSpec rasterSpec) {
		int flags = 0;
		if (rasterSpec.blendState().enabled()) {
			flags |= RASTER_FLAG_BLEND_ENABLED;
		}
		if (rasterSpec.sampler0Required()) {
			flags |= RASTER_FLAG_SAMPLER0_REQUIRED;
		}
		if (rasterSpec.sampler2Required()) {
			flags |= RASTER_FLAG_SAMPLER2_REQUIRED;
		}
		if (rasterSpec.wantsDepthTexture()) {
			flags |= RASTER_FLAG_WANTS_DEPTH_TEXTURE;
		}
		if (rasterSpec.cull()) {
			flags |= RASTER_FLAG_CULL;
		}
		if (rasterSpec.depthWrite()) {
			flags |= RASTER_FLAG_DEPTH_WRITE;
		}
		return flags;
	}

	private static int resolveRasterCompareFunction(MetalRasterPipelineSpec.CompareFunction compareFunction) {
		return switch (compareFunction) {
			case ALWAYS_PASS -> 1;
			case LESS_THAN -> 2;
			case LESS_THAN_OR_EQUAL -> 3;
			case EQUAL -> 4;
			case NOT_EQUAL -> 5;
			case GREATER_THAN_OR_EQUAL -> 6;
			case GREATER_THAN -> 7;
			case NEVER_PASS -> 8;
		};
	}

	private static int resolveRasterBlendOperation(MetalRasterPipelineSpec.BlendOperation blendOperation) {
		return switch (blendOperation) {
			case ADD -> 1;
			case SUBTRACT -> 2;
			case REVERSE_SUBTRACT -> 3;
			case MIN -> 4;
			case MAX -> 5;
		};
	}

	private static int resolveRasterBlendFactor(MetalRasterPipelineSpec.BlendFactor blendFactor) {
		return switch (blendFactor) {
			case CONSTANT_ALPHA -> 1;
			case CONSTANT_COLOR -> 2;
			case DST_ALPHA -> 3;
			case DST_COLOR -> 4;
			case ONE -> 5;
			case ONE_MINUS_CONSTANT_ALPHA -> 6;
			case ONE_MINUS_CONSTANT_COLOR -> 7;
			case ONE_MINUS_DST_ALPHA -> 8;
			case ONE_MINUS_DST_COLOR -> 9;
			case ONE_MINUS_SRC_ALPHA -> 10;
			case ONE_MINUS_SRC_COLOR -> 11;
			case SRC_ALPHA -> 12;
			case SRC_ALPHA_SATURATE -> 13;
			case SRC_COLOR -> 14;
			case ZERO -> 15;
		};
	}

	private int resolveLegacyPipelineKind(MetalRasterPipelineSpec rasterSpec, String drawType) {
		String pipelineLocation = this.pipeline.getLocation().toString();
		if (rasterSpec.shaderFamily() == MetalRasterPipelineSpec.ShaderFamily.PANORAMA) {
			return PIPELINE_KIND_PANORAMA;
		}
		if (rasterSpec.shaderFamily() == MetalRasterPipelineSpec.ShaderFamily.SKY) {
			return PIPELINE_KIND_SKY;
		}
		if (rasterSpec.shaderFamily() == MetalRasterPipelineSpec.ShaderFamily.STARS) {
			return PIPELINE_KIND_STARS;
		}
		if (rasterSpec.shaderFamily() == MetalRasterPipelineSpec.ShaderFamily.POSITION_TEX) {
			return PIPELINE_KIND_CELESTIAL;
		}
		if (rasterSpec.shaderFamily() == MetalRasterPipelineSpec.ShaderFamily.POSITION_COLOR) {
			return PIPELINE_KIND_SUNRISE_SUNSET;
		}
		if (GUI_LOCATION.equals(pipelineLocation) || rasterSpec.shaderFamily() == MetalRasterPipelineSpec.ShaderFamily.GUI_COLOR) {
			return PIPELINE_KIND_GUI_COLOR;
		}
		if (rasterSpec.specialCase() == MetalRasterPipelineSpecialCase.POST_BLIT) {
			return PIPELINE_KIND_POST_BLIT;
		}
		if (GUI_TEXTURED_OPAQUE_BACKGROUND_LOCATION.equals(pipelineLocation) || !rasterSpec.blendState().enabled()) {
			return PIPELINE_KIND_GUI_TEXTURED_OPAQUE_BACKGROUND;
		}
		if (GUI_TEXTURED_PREMULTIPLIED_ALPHA_LOCATION.equals(pipelineLocation)) {
			return PIPELINE_KIND_GUI_TEXTURED_PREMULTIPLIED_ALPHA;
		}
		if (VIGNETTE_LOCATION.equals(pipelineLocation) || matchesBlendState(
			rasterSpec.blendState(),
			MetalRasterPipelineSpec.BlendOperation.ADD,
			MetalRasterPipelineSpec.BlendOperation.ADD,
			MetalRasterPipelineSpec.BlendFactor.ZERO,
			MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_SRC_COLOR,
			MetalRasterPipelineSpec.BlendFactor.ZERO,
			MetalRasterPipelineSpec.BlendFactor.ONE
		)) {
			return PIPELINE_KIND_VIGNETTE;
		}
		if (CROSSHAIR_LOCATION.equals(pipelineLocation) || matchesBlendState(
			rasterSpec.blendState(),
			MetalRasterPipelineSpec.BlendOperation.ADD,
			MetalRasterPipelineSpec.BlendOperation.ADD,
			MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_DST_COLOR,
			MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_SRC_COLOR,
			MetalRasterPipelineSpec.BlendFactor.ONE,
			MetalRasterPipelineSpec.BlendFactor.ZERO
		)) {
			return PIPELINE_KIND_CROSSHAIR;
		}
		if (GUI_TEXTURED_LOCATION.equals(pipelineLocation) || rasterSpec.shaderFamily() == MetalRasterPipelineSpec.ShaderFamily.GUI_TEXTURED) {
			return PIPELINE_KIND_GUI_TEXTURED;
		}
		throw unsupportedPass("Metal native render path does not support pipeline " + this.pipeline.getLocation() + " yet.", drawType);
	}

	private int resolveWorldTerrainPipelineKind() {
		String pipelineLocation = this.pipeline.getLocation().toString();
		if (SOLID_TERRAIN_LOCATION.equals(pipelineLocation)) {
			return PIPELINE_KIND_WORLD_TERRAIN_SOLID;
		}
		if (CUTOUT_TERRAIN_LOCATION.equals(pipelineLocation)) {
			return PIPELINE_KIND_WORLD_TERRAIN_CUTOUT;
		}
		if (TRANSLUCENT_TERRAIN_LOCATION.equals(pipelineLocation)) {
			return PIPELINE_KIND_WORLD_TERRAIN_TRANSLUCENT;
		}

		throw unsupportedPass("Metal native world path does not support pipeline " + this.pipeline.getLocation() + " yet.", "drawIndexed");
	}

	private MetalRasterPipelineSpec rasterSpec(boolean indexed) {
		if (this.pipeline == null) {
			throw new IllegalStateException("Metal render pass requires a pipeline before creating a raster spec.");
		}
		return MetalRasterPipelineSpecs.describe(this.pipeline, indexed);
	}

	private RasterDrawState prepareRasterDrawState(MetalRasterPipelineSpec rasterSpec, String drawType) {
		BoundTexture sampler0 = this.boundTextures.get("Sampler0");
		long sampler0TextureHandle = requireRasterSampler0TextureHandle(rasterSpec, sampler0);
		return new RasterDrawState(
			requireNativeRasterBridge(),
			this.colorTarget.nativeTextureHandle(),
			nativeRasterDepthTextureHandle(rasterSpec, drawType),
			this.vertexBuffer.sliceStorage(0L, this.vertexBuffer.size()).order(ByteOrder.nativeOrder()),
			this.pipeline.getVertexFormat().getVertexSize(),
			sliceRequiredUniformBuffer("Projection"),
			sliceRequiredUniformBuffer("DynamicTransforms"),
			sampler0TextureHandle,
			sampler0 != null && sampler0.linearFiltering(),
			sampler0 != null && sampler0.repeatU(),
			sampler0 != null && sampler0.repeatV()
		);
	}

	private MetalBridge requireNativeRasterBridge() {
		MetalBridge metalBridge = this.colorTarget.metalBridge();
		if (metalBridge == null || !this.colorTarget.hasNativeTextureHandle()) {
			throw new IllegalStateException("Metal raster draw requires a native-backed RGBA8 render target.");
		}
		return metalBridge;
	}

	private long requireRasterSampler0TextureHandle(MetalRasterPipelineSpec rasterSpec, BoundTexture sampler0) {
		long sampler0TextureHandle = sampler0 == null ? 0L : sampler0.nativeTextureHandle();
		if (rasterSpec.sampler0Required() && sampler0TextureHandle == 0L) {
			throw new IllegalStateException("Textured Metal GUI draw requires a native Sampler0 texture.");
		}
		if (rasterSpec.shaderFamily() == MetalRasterPipelineSpec.ShaderFamily.PANORAMA && sampler0TextureHandle == 0L) {
			throw new IllegalStateException("Metal panorama draw requires a native cubemap Sampler0 texture.");
		}
		return sampler0TextureHandle;
	}

	private static int resolvePrimitiveKind(MetalRasterPipelineSpec.PrimitiveTopology primitiveTopology) {
		if (primitiveTopology == MetalRasterPipelineSpec.PrimitiveTopology.TRIANGLE_STRIP) {
			return PRIMITIVE_KIND_TRIANGLE_STRIP;
		}
		if (primitiveTopology == MetalRasterPipelineSpec.PrimitiveTopology.LINE) {
			return PRIMITIVE_KIND_LINE;
		}
		if (primitiveTopology == MetalRasterPipelineSpec.PrimitiveTopology.LINE_STRIP) {
			return PRIMITIVE_KIND_LINE_STRIP;
		}
		if (primitiveTopology == MetalRasterPipelineSpec.PrimitiveTopology.POINT) {
			return PRIMITIVE_KIND_POINT;
		}

		return PRIMITIVE_KIND_TRIANGLE;
	}

	private static boolean matchesBlendState(
		MetalRasterPipelineSpec.BlendState blendState,
		MetalRasterPipelineSpec.BlendOperation colorOperation,
		MetalRasterPipelineSpec.BlendOperation alphaOperation,
		MetalRasterPipelineSpec.BlendFactor sourceColorFactor,
		MetalRasterPipelineSpec.BlendFactor destinationColorFactor,
		MetalRasterPipelineSpec.BlendFactor sourceAlphaFactor,
		MetalRasterPipelineSpec.BlendFactor destinationAlphaFactor
	) {
		return blendState.enabled()
			&& blendState.colorOperation() == colorOperation
			&& blendState.alphaOperation() == alphaOperation
			&& blendState.sourceColorFactor() == sourceColorFactor
			&& blendState.destinationColorFactor() == destinationColorFactor
			&& blendState.sourceAlphaFactor() == sourceAlphaFactor
			&& blendState.destinationAlphaFactor() == destinationAlphaFactor;
	}

	private GpuBufferSlice requireUniform(String name) {
		GpuBufferSlice slice = this.uniformSlices.get(name);
		if (slice == null) {
			throw new IllegalStateException("Metal render pass requires uniform " + name + ".");
		}
		return slice;
	}

	private ByteBuffer sliceUniformBuffer(GpuBufferSlice slice) {
		if (!(slice.buffer() instanceof MetalBuffer metalBuffer)) {
			throw new IllegalArgumentException("Metal render pass only supports Metal uniform buffers.");
		}

		return metalBuffer.sliceStorage(slice.offset(), slice.length()).order(ByteOrder.nativeOrder());
	}

	private ByteBuffer sliceRequiredUniformBuffer(String name) {
		return sliceUniformBuffer(requireUniform(name));
	}

	private ByteBuffer buildWorldTerrainUniformData() {
		ByteBuffer chunkSection = sliceUniformBuffer(requireUniform("ChunkSection"));
		ByteBuffer globals = sliceUniformBuffer(requireUniform("Globals"));
		ByteBuffer fog = sliceUniformBuffer(requireUniform("Fog"));
		ByteBuffer buffer = ByteBuffer.allocateDirect(160).order(ByteOrder.nativeOrder());

		for (int offset = 0; offset < 64; offset += Integer.BYTES) {
			buffer.putFloat(offset, chunkSection.getFloat(offset));
		}

		for (int offset = 0; offset < 16; offset += Integer.BYTES) {
			buffer.putFloat(64 + offset, fog.getFloat(offset));
		}

		buffer.putFloat(80, chunkSection.getInt(80));
		buffer.putFloat(84, chunkSection.getInt(84));
		buffer.putFloat(88, chunkSection.getInt(88));
		buffer.putFloat(92, chunkSection.getFloat(64));

		buffer.putFloat(96, globals.getInt(0));
		buffer.putFloat(100, globals.getInt(4));
		buffer.putFloat(104, globals.getInt(8));
		buffer.putFloat(112, globals.getFloat(16));
		buffer.putFloat(116, globals.getFloat(20));
		buffer.putFloat(120, globals.getFloat(24));

		buffer.putFloat(128, fog.getFloat(16));
		buffer.putFloat(132, fog.getFloat(20));
		buffer.putFloat(136, fog.getFloat(24));
		buffer.putFloat(140, fog.getFloat(28));
		buffer.putFloat(144, chunkSection.getInt(72));
		buffer.putFloat(148, chunkSection.getInt(76));
		buffer.putFloat(152, globals.getInt(52));
		return buffer;
	}

	private void withCommandContext(LongConsumer consumer) {
		MetalBridge metalBridge = this.colorTarget.metalBridge();
		if (metalBridge == null) {
			throw new IllegalStateException("Metal render pass requires an active native bridge.");
		}

		if (this.commandEncoderBackend != null) {
			consumer.accept(this.commandEncoderBackend.commandContextHandle());
			return;
		}

		long nativeCommandContextHandle = metalBridge.createCommandContext();
		try {
			consumer.accept(nativeCommandContextHandle);
			metalBridge.submitCommandContext(nativeCommandContextHandle);
		} finally {
			metalBridge.releaseCommandContext(nativeCommandContextHandle);
		}
	}

	private static int readIndex(ByteBuffer indexStorage, int index, VertexFormat.IndexType indexType) {
		int offset = index * indexType.bytes;
		return indexType == VertexFormat.IndexType.SHORT
			? Short.toUnsignedInt(indexStorage.getShort(offset))
			: indexStorage.getInt(offset);
	}

	private static float wrap(float value, AddressMode addressMode) {
		if (addressMode == AddressMode.REPEAT) {
			float wrapped = value - (float) Math.floor(value);
			return wrapped < 0.0F ? wrapped + 1.0F : wrapped;
		}

		return clamp(value, 0.0F, 1.0F);
	}

	private static float unpackColor(byte value) {
		return Byte.toUnsignedInt(value) / 255.0F;
	}

	private IndexedWorldVertex firstIndexedWorldVertex(int baseVertex, int firstIndex) {
		if (this.vertexBuffer == null || this.indexBuffer == null || this.indexType == null) {
			return IndexedWorldVertex.createMissing();
		}

		ByteBuffer indexStorage = this.indexBuffer.sliceStorage(0L, this.indexBuffer.size()).order(ByteOrder.nativeOrder());
		if ((long) (firstIndex + 1) * this.indexType.bytes > indexStorage.capacity()) {
			return IndexedWorldVertex.createMissing();
		}

		int resolvedIndex = readIndex(indexStorage, firstIndex, this.indexType) + baseVertex;
		ByteBuffer vertexStorage = this.vertexBuffer.sliceStorage(0L, this.vertexBuffer.size()).order(ByteOrder.nativeOrder());
		int vertexStride = this.pipeline == null ? 0 : this.pipeline.getVertexFormat().getVertexSize();
		if (vertexStride <= 0) {
			return IndexedWorldVertex.createMissing();
		}

		int offset = resolvedIndex * vertexStride;
		if (offset < 0 || offset + 28 > vertexStorage.capacity()) {
			return IndexedWorldVertex.createOutOfBounds(resolvedIndex);
		}

		return new IndexedWorldVertex(
			resolvedIndex,
			vertexStorage.getFloat(offset),
			vertexStorage.getFloat(offset + 4),
			vertexStorage.getFloat(offset + 8),
			Byte.toUnsignedInt(vertexStorage.get(offset + 12)),
			Byte.toUnsignedInt(vertexStorage.get(offset + 13)),
			Byte.toUnsignedInt(vertexStorage.get(offset + 14)),
			Byte.toUnsignedInt(vertexStorage.get(offset + 15)),
			vertexStorage.getFloat(offset + 16),
			vertexStorage.getFloat(offset + 20),
			Short.toUnsignedInt(vertexStorage.getShort(offset + 24)),
			Short.toUnsignedInt(vertexStorage.getShort(offset + 26)),
			false
		);
	}

	private static DebugPixel sampleNativeTextureAtUv(MetalTexture texture, int mipLevel, IndexedWorldVertex vertex) {
		if (vertex.missing() || !texture.hasNativeTextureHandle()) {
			return null;
		}

		MetalBridge metalBridge = texture.metalBridge();
		if (metalBridge == null) {
			return null;
		}

		int width = texture.getWidth(mipLevel);
		int height = texture.getHeight(mipLevel);
		int x = clamp((int) Math.floor(vertex.u() * width), 0, width - 1);
		int y = clamp((int) Math.floor(vertex.v() * height), 0, height - 1);
		ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
		try {
			metalBridge.readTextureRgba8(texture.nativeTextureHandle(), mipLevel, 0, x, y, 1, 1, pixel);
			return readDebugPixel(pixel, 1, 4, 0, 0).withCoordinates(x, y);
		} catch (UnsupportedOperationException | IllegalStateException exception) {
			return null;
		}
	}

	private static ClipSpacePosition projectWorldVertex(ByteBuffer projectionData, ByteBuffer worldData, IndexedWorldVertex vertex) {
		return projectWorldVertex(projectionData, worldData, vertex, false, false);
	}

	private static ClipSpacePosition projectWorldVertex(ByteBuffer projectionData, ByteBuffer worldData, IndexedWorldVertex vertex, boolean transposeModelView, boolean transposeProjection) {
		if (vertex.missing()) {
			return ClipSpacePosition.unavailable();
		}

		Matrix4f projection = new Matrix4f().set(projectionData.duplicate().order(ByteOrder.nativeOrder()));
		Matrix4f modelView = new Matrix4f().set(worldData.duplicate().order(ByteOrder.nativeOrder()));
		if (transposeProjection) {
			projection.transpose();
		}
		if (transposeModelView) {
			modelView.transpose();
		}
		Vector4f worldPosition = new Vector4f(
			vertex.x() + (worldData.getFloat(80) - worldData.getFloat(96)) + worldData.getFloat(112),
			vertex.y() + (worldData.getFloat(84) - worldData.getFloat(100)) + worldData.getFloat(116),
			vertex.z() + (worldData.getFloat(88) - worldData.getFloat(104)) + worldData.getFloat(120),
			1.0F
		);
		Vector4f clip = projection.transform(modelView.transform(worldPosition, new Vector4f()), new Vector4f());
		if (Math.abs(clip.w) <= EPSILON) {
			return new ClipSpacePosition(clip.x, clip.y, clip.z, clip.w, Float.NaN, Float.NaN, Float.NaN, false);
		}

		return new ClipSpacePosition(
			clip.x,
			clip.y,
			clip.z,
			clip.w,
			clip.x / clip.w,
			clip.y / clip.w,
			clip.z / clip.w,
			true
		);
	}

	private static DebugPixel findDebugPixel(MetalTexture texture, int mipLevel) {
		ByteBuffer storage = texture.snapshotStorage(mipLevel);
		int width = texture.getWidth(mipLevel);
		int height = texture.getHeight(mipLevel);
		int bytesPerPixel = texture.getFormat().pixelSize();
		int stepX = Math.max(1, width / 8);
		int stepY = Math.max(1, height / 8);

		for (int y = 0; y < height; y += stepY) {
			for (int x = 0; x < width; x += stepX) {
				DebugPixel pixel = readDebugPixel(storage, width, bytesPerPixel, x, y);
				if (pixel.alpha() != 0 || pixel.red() != 0 || pixel.green() != 0 || pixel.blue() != 0) {
					return pixel;
				}
			}
		}

		return readDebugPixel(storage, width, bytesPerPixel, width / 2, height / 2);
	}

	private static DebugPixel readDebugPixel(ByteBuffer storage, int width, int bytesPerPixel, int x, int y) {
		int offset = (y * width + x) * bytesPerPixel;
		int red = bytesPerPixel >= 1 ? Byte.toUnsignedInt(storage.get(offset)) : 0;
		int green = bytesPerPixel >= 2 ? Byte.toUnsignedInt(storage.get(offset + 1)) : 0;
		int blue = bytesPerPixel >= 3 ? Byte.toUnsignedInt(storage.get(offset + 2)) : 0;
		int alpha = bytesPerPixel >= 4 ? Byte.toUnsignedInt(storage.get(offset + 3)) : 255;
		return new DebugPixel(x, y, red, green, blue, alpha);
	}

	private static byte packColor(float value) {
		return (byte) Math.round(clamp(value, 0.0F, 1.0F) * 255.0F);
	}

	private static boolean contains(float weight0, float weight1, float weight2) {
		return weight0 >= -EPSILON && weight1 >= -EPSILON && weight2 >= -EPSILON;
	}

	private static float edgeFunction(float ax, float ay, float bx, float by, float px, float py) {
		return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
	}

	private static float interpolate(float weight0, float weight1, float weight2, float value0, float value1, float value2) {
		return (value0 * weight0) + (value1 * weight1) + (value2 * weight2);
	}

	private static float lightmapBrightness(float level) {
		return level / (4.0F - (3.0F * level));
	}

	private static float lightmapParabolicMixFactor(float level) {
		float centered = (2.0F * level) - 1.0F;
		return centered * centered;
	}

	private static float lightmapNotGamma(float red, float green, float blue, float component) {
		float maxComponent = Math.max(red, Math.max(green, blue));
		if (maxComponent <= EPSILON) {
			return 0.0F;
		}

		float maxInverted = 1.0F - maxComponent;
		float maxScaled = 1.0F - (maxInverted * maxInverted * maxInverted * maxInverted);
		return component * (maxScaled / maxComponent);
	}

	private static float lerp(float delta, float start, float end) {
		return start + delta * (end - start);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static ByteBuffer syntheticIndices(int vertexCount, VertexFormat.Mode mode) {
		if (vertexCount <= 0) {
			return null;
		}
		if (mode == VertexFormat.Mode.QUADS && vertexCount < 4) {
			return null;
		}
		if (mode == VertexFormat.Mode.TRIANGLE_FAN && vertexCount < 3) {
			return null;
		}

		ByteBuffer cached = SYNTHETIC_INDEX_CACHE.computeIfAbsent(
			new SyntheticIndexKey(vertexCount, mode),
			key -> createSyntheticIndices(vertexCount, mode)
		);
		return duplicateDirectBuffer(cached);
	}

	private static ByteBuffer createSyntheticIndices(int vertexCount, VertexFormat.Mode mode) {
		if (vertexCount <= 0) {
			return null;
		}

		if (mode == VertexFormat.Mode.QUADS) {
			int quadCount = vertexCount / 4;
			if (quadCount <= 0) {
				return null;
			}

			ByteBuffer buffer = ByteBuffer.allocateDirect(quadCount * 6 * Integer.BYTES).order(ByteOrder.nativeOrder());
			for (int quad = 0; quad < quadCount; quad++) {
				int base = quad * 4;
				buffer.putInt(base);
				buffer.putInt(base + 1);
				buffer.putInt(base + 2);
				buffer.putInt(base);
				buffer.putInt(base + 2);
				buffer.putInt(base + 3);
			}
			buffer.flip();
			return buffer;
		}

		if (mode == VertexFormat.Mode.TRIANGLE_FAN) {
			int triangleCount = vertexCount - 2;
			if (triangleCount <= 0) {
				return null;
			}

			ByteBuffer buffer = ByteBuffer.allocateDirect(triangleCount * 3 * Integer.BYTES).order(ByteOrder.nativeOrder());
			for (int triangle = 0; triangle < triangleCount; triangle++) {
				buffer.putInt(0);
				buffer.putInt(triangle + 1);
				buffer.putInt(triangle + 2);
			}
			buffer.flip();
			return buffer;
		}

		ByteBuffer buffer = ByteBuffer.allocateDirect(vertexCount * Integer.BYTES).order(ByteOrder.nativeOrder());
		for (int index = 0; index < vertexCount; index++) {
			buffer.putInt(index);
		}
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer directFloatBuffer(float... values) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(values.length * Float.BYTES).order(ByteOrder.nativeOrder());
		for (float value : values) {
			buffer.putFloat(value);
		}
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer directIntBuffer(int... values) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(values.length * Integer.BYTES).order(ByteOrder.nativeOrder());
		for (int value : values) {
			buffer.putInt(value);
		}
		buffer.flip();
		return buffer;
	}

	private static ByteBuffer duplicateDirectBuffer(ByteBuffer buffer) {
		return buffer.duplicate().order(ByteOrder.nativeOrder());
	}

	private record BoundTexture(MetalTexture texture, int mipLevel, GpuSampler sampler) {
		private long nativeTextureHandle() {
			return this.texture.nativeTextureHandle();
		}

		private boolean linearFiltering() {
			MetalSampler metalSampler = this.metalSampler();
			return metalSampler.getMagFilter() == FilterMode.LINEAR || metalSampler.getMinFilter() == FilterMode.LINEAR;
		}

		private boolean repeatU() {
			return this.metalSampler().getAddressModeU() == AddressMode.REPEAT;
		}

		private boolean repeatV() {
			return this.metalSampler().getAddressModeV() == AddressMode.REPEAT;
		}

		private MetalSampler metalSampler() {
			return (MetalSampler) this.sampler;
		}
	}

	private record RasterDrawState(
		MetalBridge metalBridge,
		long colorTextureHandle,
		long depthTextureHandle,
		ByteBuffer vertexData,
		int vertexStride,
		ByteBuffer projectionData,
		ByteBuffer dynamicTransformsData,
		long sampler0TextureHandle,
		boolean linearFiltering,
		boolean repeatU,
		boolean repeatV
	) {
	}

	private record SyntheticIndexKey(int vertexCount, VertexFormat.Mode mode) {
	}

	private record DebugPixel(int x, int y, int red, int green, int blue, int alpha) {
		private DebugPixel withCoordinates(int x, int y) {
			return new DebugPixel(x, y, this.red, this.green, this.blue, this.alpha);
		}

		@Override
		public String toString() {
			return "sample@(" + this.x + "," + this.y + ")=(" + this.red + "," + this.green + "," + this.blue + "," + this.alpha + ")";
		}
	}

	private record Vertex(float x, float y, float u, float v, float red, float green, float blue, float alpha) {
	}

	private record IndexedWorldVertex(
		int index,
		float x,
		float y,
		float z,
		int red,
		int green,
		int blue,
		int alpha,
		float u,
		float v,
		int lightU,
		int lightV,
		boolean missing
	) {
		private static IndexedWorldVertex createMissing() {
			return new IndexedWorldVertex(-1, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0, 0.0F, 0.0F, 0, 0, true);
		}

		private static IndexedWorldVertex createOutOfBounds(int index) {
			return new IndexedWorldVertex(index, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0, 0.0F, 0.0F, 0, 0, true);
		}

		@Override
		public String toString() {
			if (this.missing) {
				return "missing";
			}

			return "index=" + this.index
				+ " pos=(" + this.x + "," + this.y + "," + this.z + ")"
				+ " color=(" + this.red + "," + this.green + "," + this.blue + "," + this.alpha + ")"
				+ " uv0=(" + this.u + "," + this.v + ")"
				+ " uv2=(" + this.lightU + "," + this.lightV + ")";
		}
	}

	private record ClipSpacePosition(float clipX, float clipY, float clipZ, float clipW, float ndcX, float ndcY, float ndcZ, boolean hasNdc) {
		private static ClipSpacePosition unavailable() {
			return new ClipSpacePosition(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, false);
		}

		@Override
		public String toString() {
			if (Float.isNaN(this.clipX)) {
				return "unavailable";
			}
			if (!this.hasNdc) {
				return "clip=(" + this.clipX + "," + this.clipY + "," + this.clipZ + "," + this.clipW + ")";
			}

			return "clip=(" + this.clipX + "," + this.clipY + "," + this.clipZ + "," + this.clipW + ") ndc=(" + this.ndcX + "," + this.ndcY + "," + this.ndcZ + ")";
		}
	}

	private record SampledColor(float red, float green, float blue, float alpha) {
		private static final SampledColor WHITE = new SampledColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private record VertexLayout(int stride, int positionOffset, int colorOffset, int uvOffset, boolean hasTexture) {
		private static VertexLayout from(VertexFormat vertexFormat) {
			int positionOffset = vertexFormat.getOffset(VertexFormatElement.POSITION);
			int colorOffset = vertexFormat.contains(VertexFormatElement.COLOR)
				? vertexFormat.getOffset(VertexFormatElement.COLOR)
				: -1;
			int uvOffset = vertexFormat.contains(VertexFormatElement.UV0)
				? vertexFormat.getOffset(VertexFormatElement.UV0)
				: -1;

			return new VertexLayout(
				vertexFormat.getVertexSize(),
				positionOffset,
				colorOffset,
				uvOffset,
				uvOffset >= 0
			);
		}
	}
}
