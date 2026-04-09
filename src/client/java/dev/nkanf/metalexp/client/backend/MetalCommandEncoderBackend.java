package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

final class MetalCommandEncoderBackend implements CommandEncoderBackend {
	@Override
	public void submit() {
	}

	@Override
	public RenderPassBackend createRenderPass(Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt) {
		throw new UnsupportedOperationException("Metal render passes are not implemented yet.");
	}

	@Override
	public RenderPassBackend createRenderPass(Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt, GpuTextureView gpuTextureView1, OptionalDouble optionalDouble) {
		throw new UnsupportedOperationException("Metal render passes are not implemented yet.");
	}

	@Override
	public void submitRenderPass() {
	}

	@Override
	public void clearColorTexture(GpuTexture gpuTexture, int i) {
		throw new UnsupportedOperationException("Metal color clears are not implemented yet.");
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture1, double v) {
		throw new UnsupportedOperationException("Metal color/depth clears are not implemented yet.");
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture1, double v, int i1, int i2, int i3, int i4) {
		throw new UnsupportedOperationException("Metal region clears are not implemented yet.");
	}

	@Override
	public void clearDepthTexture(GpuTexture gpuTexture, double v) {
		throw new UnsupportedOperationException("Metal depth clears are not implemented yet.");
	}

	@Override
	public void writeToBuffer(GpuBufferSlice gpuBufferSlice, ByteBuffer byteBuffer) {
		((MetalBuffer) gpuBufferSlice.buffer()).write(gpuBufferSlice.offset(), byteBuffer);
	}

	@Override
	public GpuBuffer.MappedView mapBuffer(GpuBufferSlice gpuBufferSlice, boolean bl, boolean bl1) {
		return new MetalMappedBufferView(((MetalBuffer) gpuBufferSlice.buffer()).sliceStorage(gpuBufferSlice.offset(), gpuBufferSlice.length()));
	}

	@Override
	public void copyToBuffer(GpuBufferSlice gpuBufferSlice, GpuBufferSlice gpuBufferSlice1) {
		throw new UnsupportedOperationException("Metal buffer copies are not implemented yet.");
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
		throw new UnsupportedOperationException("Metal image uploads are not implemented yet.");
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, ByteBuffer byteBuffer, NativeImage.Format format, int i, int i1, int i2, int i3, int i4, int i5) {
		throw new UnsupportedOperationException("Metal texture uploads are not implemented yet.");
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, long l, Runnable runnable, int i) {
		throw new UnsupportedOperationException("Metal texture readback is not implemented yet.");
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, long l, Runnable runnable, int i, int i1, int i2, int i3, int i4) {
		throw new UnsupportedOperationException("Metal texture readback is not implemented yet.");
	}

	@Override
	public void copyTextureToTexture(GpuTexture gpuTexture, GpuTexture gpuTexture1, int i, int i1, int i2, int i3, int i4, int i5, int i6) {
		throw new UnsupportedOperationException("Metal texture copies are not implemented yet.");
	}

	@Override
	public GpuFence createFence() {
		return new MetalFence();
	}

	@Override
	public void writeTimestamp(GpuQueryPool gpuQueryPool, int i) {
	}
}
