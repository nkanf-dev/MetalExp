package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;

import java.nio.ByteBuffer;

final class MetalMappedBufferView implements GpuBuffer.MappedView {
	private final ByteBuffer data;

	MetalMappedBufferView(ByteBuffer data) {
		this.data = data;
	}

	@Override
	public ByteBuffer data() {
		return this.data;
	}

	@Override
	public void close() {
	}
}
