package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class MetalTexture extends GpuTexture {
	private final ByteBuffer[] mipStorage;
	private boolean closed;

	MetalTexture(int usage, String label, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
		super(usage, label, format, width, height, depthOrLayers, mipLevels);
		this.mipStorage = new ByteBuffer[mipLevels];

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
		int sourceWidth,
		int mipLevel,
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

		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				int sourcePixelOffset = ((srcY + row) * sourceWidth + (srcX + column)) * sourceBytesPerPixel;
				int destinationPixelOffset = ((dstY + row) * mipWidth + (dstX + column)) * destinationBytesPerPixel;

				for (int byteIndex = 0; byteIndex < destinationBytesPerPixel; byteIndex++) {
					byte value = byteIndex < copyBytesPerPixel ? sourceCopy.get(sourcePixelOffset + byteIndex) : 0;
					destination.put(destinationPixelOffset + byteIndex, value);
				}
			}
		}
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
	}

	ByteBuffer snapshotStorage(int mipLevel) {
		return this.mipStorage[mipLevel].duplicate().order(ByteOrder.nativeOrder());
	}

	@Override
	public void close() {
		this.closed = true;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}
}
