package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;

final class MetalSurfaceBackend implements GpuSurfaceBackend {
	private final MetalSurfaceDescriptor surfaceDescriptor;
	private GpuSurface.Configuration configuration;
	private boolean closed;

	MetalSurfaceBackend(MetalSurfaceDescriptor surfaceDescriptor) {
		this.surfaceDescriptor = surfaceDescriptor;
	}

	MetalSurfaceDescriptor descriptor() {
		return this.surfaceDescriptor;
	}

	@Override
	public void configure(GpuSurface.Configuration configuration) throws SurfaceException {
		if (this.closed) {
			throw new SurfaceException("Metal surface backend is closed.");
		}

		this.configuration = configuration;
	}

	@Override
	public boolean isSuboptimal() {
		return false;
	}

	@Override
	public void acquireNextTexture() throws SurfaceException {
		throw new SurfaceException("Metal surface acquisition is not implemented yet.");
	}

	@Override
	public void blitFromTexture(CommandEncoderBackend commandEncoderBackend, GpuTextureView gpuTextureView) {
		throw new UnsupportedOperationException("Metal surface blit is not implemented yet.");
	}

	@Override
	public void present() {
		throw new UnsupportedOperationException("Metal surface present is not implemented yet.");
	}

	@Override
	public void close() {
		this.closed = true;
	}
}
