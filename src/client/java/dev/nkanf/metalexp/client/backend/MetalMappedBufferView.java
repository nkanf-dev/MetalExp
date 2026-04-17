package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;

import java.nio.ByteBuffer;

final class MetalMappedBufferView implements GpuBuffer.MappedView {
	private final ByteBuffer data;
	private final Runnable onClose;
	private boolean closed;

	MetalMappedBufferView(ByteBuffer data, Runnable onClose) {
		this.data = data;
		this.onClose = onClose;
	}

	@Override
	public ByteBuffer data() {
		return this.data;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;
		if (this.onClose != null) {
			this.onClose.run();
		}
	}
}
