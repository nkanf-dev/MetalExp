package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.nkanf.metalexp.bridge.MetalBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class MetalBuffer extends GpuBuffer {
	private final MetalBridge metalBridge;
	private final ByteBuffer storage;
	private final long nativeBufferHandle;
	private boolean closed;

	MetalBuffer(MetalBridge metalBridge, int usage, long size) {
		super(usage, size);
		this.metalBridge = metalBridge;
		this.storage = ByteBuffer.allocateDirect(Math.toIntExact(size)).order(ByteOrder.nativeOrder());
		this.nativeBufferHandle = createNativeBufferHandle(size);
	}

	ByteBuffer sliceStorage(long offset, long length) {
		ByteBuffer duplicate = this.storage.duplicate().order(ByteOrder.nativeOrder());
		duplicate.position(Math.toIntExact(offset));
		duplicate.limit(Math.toIntExact(offset + length));
		return duplicate.slice().order(ByteOrder.nativeOrder());
	}

	void write(long offset, ByteBuffer source) {
		int length = source.remaining();
		ByteBuffer destination = sliceStorage(offset, length);
		destination.put(source.duplicate());
		syncRangeToNative(offset, length);
	}

	GpuBuffer.MappedView map(long offset, long length) {
		return new MetalMappedBufferView(
			sliceStorage(offset, length),
			() -> syncRangeToNative(offset, Math.toIntExact(length))
		);
	}

	long nativeBufferHandle() {
		return this.nativeBufferHandle;
	}

	boolean hasNativeBufferHandle() {
		return this.nativeBufferHandle != 0L;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;
		if (this.nativeBufferHandle != 0L && this.metalBridge != null) {
			this.metalBridge.releaseBuffer(this.nativeBufferHandle);
		}
	}

	private long createNativeBufferHandle(long size) {
		if (this.metalBridge == null) {
			return 0L;
		}

		try {
			return this.metalBridge.createBuffer(size);
		} catch (UnsupportedOperationException | IllegalStateException error) {
			return 0L;
		}
	}

	private void syncRangeToNative(long offset, int length) {
		if (length <= 0 || this.nativeBufferHandle == 0L || this.metalBridge == null) {
			return;
		}

		try {
			this.metalBridge.uploadBuffer(this.nativeBufferHandle, offset, sliceStorage(offset, length));
		} catch (UnsupportedOperationException | IllegalStateException ignored) {
			// Native buffer sync is optional until the bridge exposes the full path.
		}
	}
}
