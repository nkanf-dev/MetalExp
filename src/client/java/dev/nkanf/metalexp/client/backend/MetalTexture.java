package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.nkanf.metalexp.bridge.MetalBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class MetalTexture extends GpuTexture {
	private final MetalBridge metalBridge;
	private final ByteBuffer[] mipStorage;
	private final long nativeTextureHandle;
	private boolean closed;

	MetalTexture(MetalBridge metalBridge, int usage, String label, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
		super(usage, label, format, width, height, depthOrLayers, mipLevels);
		this.metalBridge = metalBridge;
		this.mipStorage = new ByteBuffer[mipLevels];
		this.nativeTextureHandle = createNativeTextureHandle(format, width, height, depthOrLayers, mipLevels);

		for (int mipLevel = 0; mipLevel < mipLevels; mipLevel++) {
			int mipWidth = getWidth(mipLevel);
			int mipHeight = getHeight(mipLevel);
			int mipBytes = Math.max(1, mipWidth * mipHeight * depthOrLayers * format.pixelSize());
			this.mipStorage[mipLevel] = ByteBuffer.allocateDirect(mipBytes).order(ByteOrder.nativeOrder());
		}
	}

	void writeRegion(
		ByteBuffer source,
		int sourceBytesPerPixel,
		int sourceRowLength,
		int mipLevel,
		int dstX,
		int dstY,
		int width,
		int height,
		int srcX,
		int srcY
	) {
		writeRegion(source, sourceBytesPerPixel, sourceRowLength, mipLevel, 0, dstX, dstY, width, height, srcX, srcY);
	}

	void writeRegion(
		ByteBuffer source,
		int sourceBytesPerPixel,
		int sourceRowLength,
		int mipLevel,
		int dstLayer,
		int dstX,
		int dstY,
		int width,
		int height,
		int srcX,
		int srcY
	) {
		ByteBuffer destination = this.mipStorage[mipLevel].duplicate().order(ByteOrder.nativeOrder());
		ByteBuffer sourceCopy = source.duplicate().order(ByteOrder.nativeOrder());
		int destinationBytesPerPixel = getFormat().pixelSize();
		int copyBytesPerPixel = Math.min(sourceBytesPerPixel, destinationBytesPerPixel);
		int mipWidth = getWidth(mipLevel);
		int mipHeight = getHeight(mipLevel);
		int layerStride = mipWidth * mipHeight * destinationBytesPerPixel;
		int sourceStride = Math.max(sourceRowLength > 0 ? sourceRowLength : width, srcX + width);

		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				int sourcePixelOffset = ((srcY + row) * sourceStride + (srcX + column)) * sourceBytesPerPixel;
				int destinationPixelOffset = dstLayer * layerStride + ((dstY + row) * mipWidth + (dstX + column)) * destinationBytesPerPixel;

				for (int byteIndex = 0; byteIndex < destinationBytesPerPixel; byteIndex++) {
					byte value = byteIndex < copyBytesPerPixel ? sourceCopy.get(sourcePixelOffset + byteIndex) : 0;
					destination.put(destinationPixelOffset + byteIndex, value);
				}
			}
		}

		syncMipToNative(mipLevel);
	}

	ByteBuffer readRegion(int mipLevel, int srcX, int srcY, int width, int height) {
		int bytesPerPixel = getFormat().pixelSize();
		ByteBuffer source = this.mipStorage[mipLevel].duplicate().order(ByteOrder.nativeOrder());
		ByteBuffer copy = ByteBuffer.allocateDirect(width * height * bytesPerPixel).order(ByteOrder.nativeOrder());
		int mipWidth = getWidth(mipLevel);

		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				int sourcePixelOffset = ((srcY + row) * mipWidth + (srcX + column)) * bytesPerPixel;
				int copyPixelOffset = (row * width + column) * bytesPerPixel;

				for (int byteIndex = 0; byteIndex < bytesPerPixel; byteIndex++) {
					copy.put(copyPixelOffset + byteIndex, source.get(sourcePixelOffset + byteIndex));
				}
			}
		}

		return copy;
	}

	void copyRegionTo(MetalTexture destination, int mipLevel, int dstX, int dstY, int srcX, int srcY, int width, int height) {
		destination.writeRegion(readRegion(mipLevel, srcX, srcY, width, height), getFormat().pixelSize(), width, mipLevel, dstX, dstY, width, height, 0, 0);
	}

	void fillRegion(int mipLevel, int dstX, int dstY, int width, int height, int packedValue) {
		ByteBuffer destination = this.mipStorage[mipLevel].duplicate().order(ByteOrder.nativeOrder());
		int bytesPerPixel = getFormat().pixelSize();
		int mipWidth = getWidth(mipLevel);

		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				int destinationPixelOffset = ((dstY + row) * mipWidth + (dstX + column)) * bytesPerPixel;
				for (int byteIndex = 0; byteIndex < bytesPerPixel; byteIndex++) {
					destination.put(destinationPixelOffset + byteIndex, (byte) (packedValue >>> (byteIndex * 8)));
				}
			}
		}

		syncMipToNative(mipLevel);
	}

	ByteBuffer snapshotStorage(int mipLevel) {
		return this.mipStorage[mipLevel].duplicate().order(ByteOrder.nativeOrder());
	}

	long nativeTextureHandle() {
		return this.nativeTextureHandle;
	}

	boolean hasNativeTextureHandle() {
		return this.nativeTextureHandle != 0L;
	}

	MetalBridge metalBridge() {
		return this.metalBridge;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;
		if (this.nativeTextureHandle != 0L && this.metalBridge != null) {
			this.metalBridge.releaseTexture(this.nativeTextureHandle);
		}
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	private long createNativeTextureHandle(GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
		if (this.metalBridge == null || format != GpuFormat.RGBA8_UNORM) {
			return 0L;
		}

		try {
			return this.metalBridge.createTexture(
				width,
				height,
				depthOrLayers,
				mipLevels,
				(usage() & USAGE_RENDER_ATTACHMENT) != 0,
				(usage() & USAGE_TEXTURE_BINDING) != 0,
				(usage() & USAGE_CUBEMAP_COMPATIBLE) != 0
			);
		} catch (UnsupportedOperationException | IllegalStateException error) {
			return 0L;
		}
	}

	private void syncMipToNative(int mipLevel) {
		if (this.nativeTextureHandle == 0L || this.metalBridge == null) {
			return;
		}

		try {
			for (int layer = 0; layer < getDepthOrLayers(); layer++) {
				this.metalBridge.uploadTextureRgba8(
					this.nativeTextureHandle,
					mipLevel,
					layer,
					layerPixels(mipLevel, layer),
					getWidth(mipLevel),
					getHeight(mipLevel)
				);
			}
		} catch (UnsupportedOperationException | IllegalStateException ignored) {
			// Native texture sync is optional until the bridge advertises the full path.
		}
	}

	private ByteBuffer layerPixels(int mipLevel, int layer) {
		int bytesPerPixel = getFormat().pixelSize();
		int layerBytes = getWidth(mipLevel) * getHeight(mipLevel) * bytesPerPixel;
		ByteBuffer storage = this.mipStorage[mipLevel].duplicate().order(ByteOrder.nativeOrder());
		int offset = layer * layerBytes;
		storage.position(offset);
		storage.limit(offset + layerBytes);
		return storage.slice().order(ByteOrder.nativeOrder());
	}
}
