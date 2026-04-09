package dev.nkanf.metalexp.client.backend;

import dev.nkanf.metalexp.bridge.MetalBridge;

import java.util.Objects;

final class MetalSurfaceLease implements AutoCloseable {
	private final MetalBridge metalBridge;
	private final MetalSurfaceDescriptor descriptor;
	private boolean closed;

	MetalSurfaceLease(MetalBridge metalBridge, MetalSurfaceDescriptor descriptor) {
		this.metalBridge = Objects.requireNonNull(metalBridge, "metalBridge");
		this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
	}

	MetalSurfaceDescriptor descriptor() {
		return this.descriptor;
	}

	long nativeSurfaceHandle() {
		return this.descriptor.nativeSurfaceHandle();
	}

	void configure(int width, int height, boolean vsync) {
		this.ensureOpen();
		this.metalBridge.configureSurface(this.descriptor.nativeSurfaceHandle(), width, height, vsync);
	}

	void acquire() {
		this.ensureOpen();
		this.metalBridge.acquireSurface(this.descriptor.nativeSurfaceHandle());
	}

	void present() {
		this.ensureOpen();
		this.metalBridge.presentSurface(this.descriptor.nativeSurfaceHandle());
	}

	boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;
		this.metalBridge.releaseSurface(this.descriptor.nativeSurfaceHandle());
	}

	private void ensureOpen() {
		if (this.closed) {
			throw new IllegalStateException("Metal surface lease is closed.");
		}
	}
}
