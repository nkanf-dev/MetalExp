package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;

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
		throw new UnsupportedOperationException("Metal surface blit is not implemented yet.");
	}

	@Override
	public void present() {
		if (this.closed || !this.acquired) {
			return;
		}

		this.surfaceLease.present();
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
