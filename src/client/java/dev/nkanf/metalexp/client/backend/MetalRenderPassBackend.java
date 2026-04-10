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
import dev.nkanf.metalexp.bridge.MetalBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class MetalRenderPassBackend implements RenderPassBackend {
	private static final int PIPELINE_KIND_GUI_COLOR = 1;
	private static final int PIPELINE_KIND_GUI_TEXTURED = 2;
	private static final int PIPELINE_KIND_PANORAMA = 3;
	private static final int PIPELINE_KIND_GUI_TEXTURED_OPAQUE_BACKGROUND = 4;
	private static final int PIPELINE_KIND_GUI_TEXTURED_PREMULTIPLIED_ALPHA = 5;
	private static final String ANIMATE_SPRITE_BLIT_LOCATION = "minecraft:pipeline/animate_sprite_blit";
	private static final String PANORAMA_LOCATION = "minecraft:pipeline/panorama";
	private static final String GUI_TEXTURED_OPAQUE_BACKGROUND_LOCATION = "minecraft:pipeline/gui_opaque_textured_background";
	private static final String GUI_TEXTURED_PREMULTIPLIED_ALPHA_LOCATION = "minecraft:pipeline/gui_textured_premultiplied_alpha";
	private static final float EPSILON = 0.0001F;

	private final MetalTexture colorTarget;
	private final int colorTargetMipLevel;
	private final MetalCommandEncoderBackend commandEncoderBackend;
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
		this(null, colorTextureView);
	}

	MetalRenderPassBackend(MetalCommandEncoderBackend commandEncoderBackend, GpuTextureView colorTextureView) {
		if (colorTextureView == null || colorTextureView.isClosed()) {
			throw new IllegalArgumentException("Metal render pass requires a live color texture view.");
		}

		if (!(colorTextureView.texture() instanceof MetalTexture metalTexture)) {
			throw new IllegalArgumentException("Metal render pass requires a Metal texture view.");
		}

		if (metalTexture.getFormat() != GpuFormat.RGBA8_UNORM) {
			throw new IllegalArgumentException("Metal render pass currently only supports RGBA8_UNORM color targets.");
		}

		this.commandEncoderBackend = commandEncoderBackend;
		this.colorTarget = metalTexture;
		this.colorTargetMipLevel = colorTextureView.baseMipLevel();
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
		if (instanceCount <= 0 || indexCount <= 0) {
			return;
		}

		requireDrawState();
		ByteBuffer vertexStorage = this.vertexBuffer.sliceStorage(0L, this.vertexBuffer.size()).order(ByteOrder.nativeOrder());
		ByteBuffer indexStorage = this.indexBuffer.sliceStorage(0L, this.indexBuffer.size()).order(ByteOrder.nativeOrder());
		GpuBufferSlice projectionUniform = requireUniform("Projection");
		GpuBufferSlice dynamicUniform = requireUniform("DynamicTransforms");
		ByteBuffer projectionData = sliceUniformBuffer(projectionUniform);
		ByteBuffer dynamicData = sliceUniformBuffer(dynamicUniform);
		int pipelineKind = resolvePipelineKind();
		BoundTexture sampler0 = this.boundTextures.get("Sampler0");
		long sampler0TextureHandle = sampler0 == null ? 0L : sampler0.texture.nativeTextureHandle();
		if (requiresSampler0Texture(pipelineKind) && sampler0TextureHandle == 0L) {
			throw new IllegalStateException("Textured Metal GUI draw requires a native Sampler0 texture.");
		}
		if (pipelineKind == PIPELINE_KIND_PANORAMA && sampler0TextureHandle == 0L) {
			throw new IllegalStateException("Metal panorama draw requires a native cubemap Sampler0 texture.");
		}

		MetalBridge metalBridge = this.colorTarget.metalBridge();
		if (metalBridge == null || !this.colorTarget.hasNativeTextureHandle()) {
			throw new IllegalStateException("Metal GUI draw requires a native-backed RGBA8 render target.");
		}

		withCommandContext(nativeCommandContextHandle -> metalBridge.drawGuiPass(
			nativeCommandContextHandle,
			this.colorTarget.nativeTextureHandle(),
			pipelineKind,
			vertexStorage,
			this.pipeline.getVertexFormat().getVertexSize(),
			baseVertex,
			indexStorage,
			this.indexType.bytes,
			firstIndex,
			indexCount,
			projectionData,
			dynamicData,
			sampler0TextureHandle,
			sampler0 != null && (sampler0.sampler.getMagFilter() == FilterMode.LINEAR || sampler0.sampler.getMinFilter() == FilterMode.LINEAR),
			sampler0 != null && sampler0.sampler.getAddressModeU() == AddressMode.REPEAT,
			sampler0 != null && sampler0.sampler.getAddressModeV() == AddressMode.REPEAT,
			this.scissorEnabled,
			this.scissorX,
			this.scissorY,
			this.scissorWidth,
			this.scissorHeight
		));
	}

	@Override
	public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, Collection<String> collection, T object) {
		for (RenderPass.Draw<T> draw : draws) {
			setVertexBuffer(draw.slot(), draw.vertexBuffer());
			setIndexBuffer(draw.indexBuffer(), draw.indexType());
			if (draw.uniformUploaderConsumer() != null) {
				draw.uniformUploaderConsumer().accept(object, this::setUniform);
			}
			drawIndexed(draw.baseVertex(), draw.firstIndex(), draw.indexCount(), 1);
		}
	}

	@Override
	public void draw(int firstVertex, int vertexCount) {
		if (this.pipeline == null) {
			return;
		}

		String pipelineLocation = this.pipeline.getLocation().toString();
		if (ANIMATE_SPRITE_BLIT_LOCATION.equals(pipelineLocation)) {
			drawAnimateSpriteBlit();
			return;
		}

		if (vertexCount <= 0) {
			return;
		}

		if (this.vertexBuffer == null || this.vertexBuffer.size() == 0L) {
			return;
		}

		GpuBufferSlice projectionUniform = requireUniform("Projection");
		GpuBufferSlice dynamicUniform = requireUniform("DynamicTransforms");
		ByteBuffer projectionData = sliceUniformBuffer(projectionUniform);
		ByteBuffer dynamicData = sliceUniformBuffer(dynamicUniform);
		int pipelineKind = resolvePipelineKind();
		BoundTexture sampler0 = this.boundTextures.get("Sampler0");
		long sampler0TextureHandle = sampler0 == null ? 0L : sampler0.texture.nativeTextureHandle();
		if (requiresSampler0Texture(pipelineKind) && sampler0TextureHandle == 0L) {
			throw new IllegalStateException("Textured Metal GUI draw requires a native Sampler0 texture.");
		}
		if (pipelineKind == PIPELINE_KIND_PANORAMA && sampler0TextureHandle == 0L) {
			throw new IllegalStateException("Metal panorama draw requires a native cubemap Sampler0 texture.");
		}

		MetalBridge metalBridge = this.colorTarget.metalBridge();
		if (metalBridge == null || !this.colorTarget.hasNativeTextureHandle()) {
			throw new IllegalStateException("Metal GUI draw requires a native-backed RGBA8 render target.");
		}

		ByteBuffer vertexStorage = this.vertexBuffer.sliceStorage(0L, this.vertexBuffer.size()).order(ByteOrder.nativeOrder());
		ByteBuffer syntheticIndices = buildSyntheticIndices(vertexCount, this.pipeline.getVertexFormatMode());
		if (syntheticIndices == null || !syntheticIndices.hasRemaining()) {
			return;
		}
		int syntheticIndexCount = syntheticIndices.remaining() / Integer.BYTES;

		withCommandContext(nativeCommandContextHandle -> metalBridge.drawGuiPass(
			nativeCommandContextHandle,
			this.colorTarget.nativeTextureHandle(),
			pipelineKind,
			vertexStorage,
			this.pipeline.getVertexFormat().getVertexSize(),
			Math.max(0, firstVertex),
			syntheticIndices,
			Integer.BYTES,
			0,
			syntheticIndexCount,
			projectionData,
			dynamicData,
			sampler0TextureHandle,
			sampler0 != null && (sampler0.sampler.getMagFilter() == FilterMode.LINEAR || sampler0.sampler.getMinFilter() == FilterMode.LINEAR),
			sampler0 != null && sampler0.sampler.getAddressModeU() == AddressMode.REPEAT,
			sampler0 != null && sampler0.sampler.getAddressModeV() == AddressMode.REPEAT,
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

	@Override
	public void writeTimestamp(GpuQueryPool gpuQueryPool, int index) {
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

	private int resolvePipelineKind() {
		String pipelineLocation = this.pipeline.getLocation().toString();
		if (PANORAMA_LOCATION.equals(pipelineLocation)) {
			return PIPELINE_KIND_PANORAMA;
		}
		if (GUI_TEXTURED_OPAQUE_BACKGROUND_LOCATION.equals(pipelineLocation)) {
			return PIPELINE_KIND_GUI_TEXTURED_OPAQUE_BACKGROUND;
		}
		if (GUI_TEXTURED_PREMULTIPLIED_ALPHA_LOCATION.equals(pipelineLocation)) {
			return PIPELINE_KIND_GUI_TEXTURED_PREMULTIPLIED_ALPHA;
		}

		VertexFormat vertexFormat = this.pipeline.getVertexFormat();
		if (vertexFormat.contains(VertexFormatElement.UV0)) {
			return PIPELINE_KIND_GUI_TEXTURED;
		}

		if (vertexFormat.contains(VertexFormatElement.COLOR)) {
			return PIPELINE_KIND_GUI_COLOR;
		}

		throw new UnsupportedOperationException("Metal hardware GUI path does not support pipeline " + this.pipeline.getLocation() + " yet.");
	}

	private static boolean requiresSampler0Texture(int pipelineKind) {
		return pipelineKind == PIPELINE_KIND_GUI_TEXTURED
			|| pipelineKind == PIPELINE_KIND_GUI_TEXTURED_OPAQUE_BACKGROUND
			|| pipelineKind == PIPELINE_KIND_GUI_TEXTURED_PREMULTIPLIED_ALPHA;
	}

	private GpuBufferSlice requireUniform(String name) {
		GpuBufferSlice slice = this.uniformSlices.get(name);
		if (slice == null) {
			throw new IllegalStateException("Metal GUI draw requires uniform " + name + ".");
		}
		return slice;
	}

	private ByteBuffer sliceUniformBuffer(GpuBufferSlice slice) {
		if (!(slice.buffer() instanceof MetalBuffer metalBuffer)) {
			throw new IllegalArgumentException("Metal GUI draw only supports Metal uniform buffers.");
		}

		return metalBuffer.sliceStorage(slice.offset(), slice.length()).order(ByteOrder.nativeOrder());
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

	private static float lerp(float delta, float start, float end) {
		return start + delta * (end - start);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static ByteBuffer buildSyntheticIndices(int vertexCount, VertexFormat.Mode mode) {
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

		ByteBuffer buffer = ByteBuffer.allocateDirect(vertexCount * Integer.BYTES).order(ByteOrder.nativeOrder());
		for (int index = 0; index < vertexCount; index++) {
			buffer.putInt(index);
		}
		buffer.flip();
		return buffer;
	}

	private record BoundTexture(MetalTexture texture, int mipLevel, GpuSampler sampler) {
	}

	private record Vertex(float x, float y, float u, float v, float red, float green, float blue, float alpha) {
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
