package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

final class MetalTextureView extends GpuTextureView {
	private boolean closed;

	MetalTextureView(GpuTexture gpuTexture, int baseMipLevel, int mipLevels) {
		super(gpuTexture, baseMipLevel, mipLevels);
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
