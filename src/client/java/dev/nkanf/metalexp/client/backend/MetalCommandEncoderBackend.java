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
import org.lwjgl.system.MemoryUtil;

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
		return new MetalRenderPassBackend(gpuTextureView);
	}

	@Override
	public RenderPassBackend createRenderPass(Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt, GpuTextureView gpuTextureView1, OptionalDouble optionalDouble) {
		return new MetalRenderPassBackend(gpuTextureView);
	}

	@Override
	public void submitRenderPass() {
	}

	@Override
	public void clearColorTexture(GpuTexture gpuTexture, int i) {
		((MetalTexture) gpuTexture).fillRegion(0, 0, 0, gpuTexture.getWidth(0), gpuTexture.getHeight(0), i);
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture1, double v) {
		clearColorTexture(gpuTexture, i);
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture1, double v, int i1, int i2, int i3, int i4) {
		((MetalTexture) gpuTexture).fillRegion(0, i1, i2, i3, i4, i);
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
		ByteBuffer source = MemoryUtil.memByteBuffer(
			nativeImage.getPointer(),
			nativeImage.getWidth() * nativeImage.getHeight() * nativeImage.format().components()
		);
		((MetalTexture) gpuTexture).writeRegion(source, nativeImage.format().components(), nativeImage.getWidth(), i, i2, i3, i4, i5, i6, i7);
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, ByteBuffer byteBuffer, NativeImage.Format format, int i, int i1, int i2, int i3, int i4, int i5) {
		((MetalTexture) gpuTexture).writeRegion(byteBuffer, format.components(), i4, i, i2, i3, i4, i5, 0, 0);
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, long l, Runnable runnable, int i) {
		copyTextureToBuffer(gpuTexture, gpuBuffer, l, runnable, i, 0, 0, gpuTexture.getWidth(i), gpuTexture.getHeight(i));
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, long l, Runnable runnable, int i, int i1, int i2, int i3, int i4) {
		ByteBuffer source = ((MetalTexture) gpuTexture).readRegion(i, i1, i2, i3, i4);
		((MetalBuffer) gpuBuffer).write(l, source);
		runnable.run();
	}

	@Override
	public void copyTextureToTexture(GpuTexture gpuTexture, GpuTexture gpuTexture1, int i, int i1, int i2, int i3, int i4, int i5, int i6) {
		((MetalTexture) gpuTexture).copyRegionTo((MetalTexture) gpuTexture1, i, i1, i2, i3, i4, i5, i6);
	}

	@Override
	public GpuFence createFence() {
		return new MetalFence();
	}

	@Override
	public void writeTimestamp(GpuQueryPool gpuQueryPool, int i) {
	}
}
