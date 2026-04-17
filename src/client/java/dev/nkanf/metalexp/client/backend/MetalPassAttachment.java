package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTextureView;

record MetalPassAttachment(
	String label,
	GpuFormat format,
	int width,
	int height,
	int mipLevel,
	boolean nativeBacked
) {
	static MetalPassAttachment from(GpuTextureView textureView) {
		MetalTexture texture = (MetalTexture) textureView.texture();
		return new MetalPassAttachment(
			texture.getLabel(),
			texture.getFormat(),
			texture.getWidth(textureView.baseMipLevel()),
			texture.getHeight(textureView.baseMipLevel()),
			textureView.baseMipLevel(),
			texture.hasNativeTextureHandle()
		);
	}

	String describe() {
		return "label=" + this.label
			+ ", format=" + this.format
			+ ", size=" + this.width + "x" + this.height
			+ ", mipLevel=" + this.mipLevel
			+ ", nativeBacked=" + this.nativeBacked;
	}
}
