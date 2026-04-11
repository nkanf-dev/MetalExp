package dev.nkanf.metalexp.client.backend;

import dev.nkanf.metalexp.bridge.MetalBridge;

import java.nio.ByteBuffer;
import java.util.Objects;

final class MetalSurfaceLease implements AutoCloseable {
	private final MetalBridge metalBridge;
	private final MetalSurfaceDescriptor descriptor;
	private long pendingCommandContextHandle;
	private boolean closed;

	MetalSurfaceLease(MetalBridge metalBridge, MetalSurfaceDescriptor descriptor) {
		this.metalBridge = Objects.requireNonNull(metalBridge, "metalBridge");
		this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
	}

	MetalSurfaceDescriptor descriptor() {
		return this.descriptor;
	}

	MetalBridge metalBridge() {
		return this.metalBridge;
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

	void blitRgba8(ByteBuffer rgbaPixels, int width, int height) {
		this.ensureOpen();
		this.metalBridge.blitSurfaceRgba8(this.descriptor.nativeSurfaceHandle(), rgbaPixels, width, height);
	}

	void blitTexture(long nativeTextureHandle) {
		this.ensureOpen();
		long nativeCommandContextHandle = this.metalBridge.createCommandContext();
		try {
			this.metalBridge.blitSurfaceTexture(nativeCommandContextHandle, this.descriptor.nativeSurfaceHandle(), nativeTextureHandle);
			this.metalBridge.submitCommandContext(nativeCommandContextHandle);
		} finally {
			this.metalBridge.releaseCommandContext(nativeCommandContextHandle);
		}
	}

	void blitTexture(long nativeCommandContextHandle, long nativeTextureHandle) {
		this.ensureOpen();
		this.metalBridge.blitSurfaceTexture(nativeCommandContextHandle, this.descriptor.nativeSurfaceHandle(), nativeTextureHandle);
	}

	long acquirePendingCommandContext() {
		this.ensureOpen();
		if (this.pendingCommandContextHandle == 0L) {
			this.pendingCommandContextHandle = this.metalBridge.createCommandContext();
		}

		return this.pendingCommandContextHandle;
	}

	void submitPendingCommands() {
		this.ensureOpen();
		if (this.pendingCommandContextHandle == 0L) {
			return;
		}

		try {
			this.metalBridge.submitCommandContext(this.pendingCommandContextHandle);
		} finally {
			this.metalBridge.releaseCommandContext(this.pendingCommandContextHandle);
			this.pendingCommandContextHandle = 0L;
		}
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

		if (this.pendingCommandContextHandle != 0L) {
			this.metalBridge.releaseCommandContext(this.pendingCommandContextHandle);
			this.pendingCommandContextHandle = 0L;
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
