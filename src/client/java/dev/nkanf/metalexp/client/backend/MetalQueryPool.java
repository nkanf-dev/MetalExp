package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.GpuQueryPool;

import java.util.OptionalLong;

final class MetalQueryPool implements GpuQueryPool {
	private final int size;

	MetalQueryPool(int size) {
		this.size = size;
	}

	@Override
	public int size() {
		return this.size;
	}

	@Override
	public OptionalLong getValue(int index) {
		return OptionalLong.empty();
	}

	@Override
	public OptionalLong[] getValues(int start, int length) {
		OptionalLong[] values = new OptionalLong[length];
		for (int i = 0; i < length; i++) {
			values[i] = OptionalLong.empty();
		}

		return values;
	}

	@Override
	public void close() {
	}
}
