package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.GpuFormat;

import java.nio.ByteBuffer;

import java.util.Objects;

final class MetalSurfaceBackend implements GpuSurfaceBackend {
	private final MetalSurfaceLease surfaceLease;
	private GpuSurface.Configuration configuration;
	private boolean acquired;
	private boolean closed;

	MetalSurfaceBackend(MetalSurfaceLease surfaceLease) {
		this.surfaceLease = Objects.requireNonNull(surfaceLease, "surfaceLease");
	}

	MetalSurfaceDescriptor descriptor() {
		return this.surfaceLease.descriptor();
	}

	@Override
	public void configure(GpuSurface.Configuration configuration) throws SurfaceException {
		if (this.closed || this.surfaceLease.isClosed()) {
			throw new SurfaceException("Metal surface backend is closed.");
		}

		try {
			this.surfaceLease.configure(configuration.width(), configuration.height(), configuration.vsync());
		} catch (IllegalStateException | IllegalArgumentException error) {
			throw new SurfaceException(error.getMessage());
		}

		this.configuration = configuration;
	}

	@Override
	public boolean isSuboptimal() {
		return false;
	}

	@Override
	public void acquireNextTexture() throws SurfaceException {
		if (this.closed || this.surfaceLease.isClosed()) {
			throw new SurfaceException("Metal surface backend is closed.");
		}

		try {
			this.surfaceLease.acquire();
			this.acquired = true;
		} catch (IllegalStateException | IllegalArgumentException error) {
			this.acquired = false;
			throw new SurfaceException(error.getMessage());
		}
	}

	@Override
	public void blitFromTexture(CommandEncoderBackend commandEncoderBackend, GpuTextureView gpuTextureView) {
		if (this.closed || !this.acquired || this.surfaceLease.isClosed()) {
			throw new IllegalStateException("Metal surface backend must be acquired before blitting.");
		}

		if (commandEncoderBackend == null) {
			throw new IllegalArgumentException("Metal surface blit requires a command encoder.");
		}

		if (gpuTextureView == null || gpuTextureView.isClosed()) {
			throw new IllegalArgumentException("Metal surface blit requires a live color texture view.");
		}

		if (!(gpuTextureView.texture() instanceof MetalTexture metalTexture)) {
			throw new IllegalArgumentException("Metal surface blit requires a Metal texture.");
		}

		if (metalTexture.getFormat() != GpuFormat.RGBA8_UNORM) {
			throw new IllegalArgumentException("Metal surface blit currently only supports RGBA8_UNORM textures.");
		}

		if (metalTexture.hasNativeTextureHandle()) {
			if (commandEncoderBackend instanceof MetalCommandEncoderBackend metalCommandEncoderBackend) {
				this.surfaceLease.blitTexture(metalCommandEncoderBackend.commandContextHandle(), metalTexture.nativeTextureHandle());
			} else {
				this.surfaceLease.blitTexture(metalTexture.nativeTextureHandle());
			}
			return;
		}

		int mipLevel = gpuTextureView.baseMipLevel();
		ByteBuffer pixels = metalTexture.readRegion(
			mipLevel,
			0,
			0,
			gpuTextureView.getWidth(0),
			gpuTextureView.getHeight(0)
		);
		this.surfaceLease.blitRgba8(pixels, gpuTextureView.getWidth(0), gpuTextureView.getHeight(0));
	}

	@Override
	public void present() {
		if (this.closed || !this.acquired || this.surfaceLease.isClosed()) {
			this.acquired = false;
			return;
		}

		try {
			this.surfaceLease.present();
		} catch (IllegalStateException | IllegalArgumentException error) {
			// Presentation is best-effort during bootstrap; a closed/invalid lease
			// should not crash shutdown or fallback paths.
		}

		this.acquired = false;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;
		this.acquired = false;
		this.surfaceLease.close();
	}

	boolean isAcquired() {
		return this.acquired;
	}

	boolean isClosed() {
		return this.closed;
	}
}
