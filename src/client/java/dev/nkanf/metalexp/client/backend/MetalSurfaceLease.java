package dev.nkanf.metalexp.client.backend;

import dev.nkanf.metalexp.bridge.MetalBridge;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Objects;

final class MetalSurfaceLease implements AutoCloseable {
	private static final int MAX_IN_FLIGHT_COMMAND_CONTEXTS = 3;

	private final MetalBridge metalBridge;
	private final MetalSurfaceDescriptor descriptor;
	private final ArrayDeque<Long> inFlightCommandContextHandles = new ArrayDeque<>();
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
		this.releaseCompletedCommandContexts();
		this.metalBridge.acquireSurface(this.descriptor.nativeSurfaceHandle());
	}

	void blitRgba8(ByteBuffer rgbaPixels, int width, int height) {
		this.ensureOpen();
		this.metalBridge.blitSurfaceRgba8(this.descriptor.nativeSurfaceHandle(), rgbaPixels, width, height);
	}

	void blitTexture(long nativeTextureHandle) {
		this.ensureOpen();
		this.ensureInFlightCapacity();
		long nativeCommandContextHandle = this.metalBridge.createCommandContext();
		boolean submitted = false;
		try {
			this.metalBridge.blitSurfaceTexture(nativeCommandContextHandle, this.descriptor.nativeSurfaceHandle(), nativeTextureHandle);
			this.metalBridge.submitCommandContext(nativeCommandContextHandle);
			this.inFlightCommandContextHandles.addLast(nativeCommandContextHandle);
			submitted = true;
			this.releaseCompletedCommandContexts();
		} finally {
			if (!submitted) {
				this.metalBridge.releaseCommandContext(nativeCommandContextHandle);
			}
		}
	}

	void blitTexture(long nativeCommandContextHandle, long nativeTextureHandle) {
		this.ensureOpen();
		this.metalBridge.blitSurfaceTexture(nativeCommandContextHandle, this.descriptor.nativeSurfaceHandle(), nativeTextureHandle);
	}

	long acquirePendingCommandContext() {
		this.ensureOpen();
		this.releaseCompletedCommandContexts();
		if (this.pendingCommandContextHandle == 0L) {
			this.ensureInFlightCapacity();
			this.pendingCommandContextHandle = this.metalBridge.createCommandContext();
		}

		return this.pendingCommandContextHandle;
	}

	void submitPendingCommands() {
		this.ensureOpen();
		if (this.pendingCommandContextHandle == 0L) {
			this.releaseCompletedCommandContexts();
			return;
		}

		try {
			this.metalBridge.submitCommandContext(this.pendingCommandContextHandle);
			this.inFlightCommandContextHandles.addLast(this.pendingCommandContextHandle);
		} finally {
			this.pendingCommandContextHandle = 0L;
		}

		this.releaseCompletedCommandContexts();
	}

	void present() {
		this.ensureOpen();
		this.releaseCompletedCommandContexts();
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
		while (!this.inFlightCommandContextHandles.isEmpty()) {
			long nativeCommandContextHandle = this.inFlightCommandContextHandles.removeFirst();
			this.metalBridge.waitForCommandContext(nativeCommandContextHandle);
			this.metalBridge.releaseCommandContext(nativeCommandContextHandle);
		}
		this.closed = true;
		this.metalBridge.releaseSurface(this.descriptor.nativeSurfaceHandle());
	}

	private void ensureInFlightCapacity() {
		while (this.inFlightCommandContextHandles.size() >= MAX_IN_FLIGHT_COMMAND_CONTEXTS) {
			if (this.releaseCompletedCommandContexts()) {
				continue;
			}

			long nativeCommandContextHandle = this.inFlightCommandContextHandles.removeFirst();
			this.metalBridge.waitForCommandContext(nativeCommandContextHandle);
			this.metalBridge.releaseCommandContext(nativeCommandContextHandle);
		}
	}

	private boolean releaseCompletedCommandContexts() {
		boolean releasedAny = false;
		while (!this.inFlightCommandContextHandles.isEmpty()) {
			long nativeCommandContextHandle = this.inFlightCommandContextHandles.peekFirst();
			if (!this.metalBridge.isCommandContextComplete(nativeCommandContextHandle)) {
				break;
			}

			this.inFlightCommandContextHandles.removeFirst();
			this.metalBridge.releaseCommandContext(nativeCommandContextHandle);
			releasedAny = true;
		}

		return releasedAny;
	}

	private void ensureOpen() {
		if (this.closed) {
			throw new IllegalStateException("Metal surface lease is closed.");
		}
	}
}
