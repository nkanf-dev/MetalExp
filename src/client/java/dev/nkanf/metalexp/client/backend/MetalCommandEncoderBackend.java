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
	private static final MetalRenderPassBackend NO_OP_RENDER_PASS = new MetalRenderPassBackend();

	@Override
	public void submit() {
	}

	@Override
	public RenderPassBackend createRenderPass(Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt) {
		return NO_OP_RENDER_PASS;
	}

	@Override
	public RenderPassBackend createRenderPass(Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt, GpuTextureView gpuTextureView1, OptionalDouble optionalDouble) {
		return NO_OP_RENDER_PASS;
	}

	@Override
	public void submitRenderPass() {
	}

	@Override
	public void clearColorTexture(GpuTexture gpuTexture, int i) {
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture1, double v) {
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture1, double v, int i1, int i2, int i3, int i4) {
	}

	@Override
	public void clearDepthTexture(GpuTexture gpuTexture, double v) {
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
		ByteBuffer source = ((MetalBuffer) gpuBufferSlice.buffer()).sliceStorage(gpuBufferSlice.offset(), gpuBufferSlice.length());
		((MetalBuffer) gpuBufferSlice1.buffer()).write(gpuBufferSlice1.offset(), source);
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
	}

	@Override
	public GpuFence createFence() {
		return new MetalFence();
	}

	@Override
	public void writeTimestamp(GpuQueryPool gpuQueryPool, int i) {
	}
}
