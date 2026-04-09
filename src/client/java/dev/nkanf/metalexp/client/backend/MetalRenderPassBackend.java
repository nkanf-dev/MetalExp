package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.util.Collection;
import java.util.function.Supplier;

final class MetalRenderPassBackend implements RenderPassBackend {
	@Override
	public void pushDebugGroup(Supplier<String> supplier) {
	}

	@Override
	public void popDebugGroup() {
	}

	@Override
	public void setPipeline(RenderPipeline renderPipeline) {
	}

	@Override
	public void bindTexture(String name, GpuTextureView textureView, GpuSampler sampler) {
	}

	@Override
	public void setUniform(String name, GpuBuffer gpuBuffer) {
	}

	@Override
	public void setUniform(String name, com.mojang.blaze3d.buffers.GpuBufferSlice gpuBufferSlice) {
	}

	@Override
	public void enableScissor(int x, int y, int width, int height) {
	}

	@Override
	public void disableScissor() {
	}

	@Override
	public void setVertexBuffer(int slot, GpuBuffer gpuBuffer) {
	}

	@Override
	public void setIndexBuffer(GpuBuffer gpuBuffer, VertexFormat.IndexType indexType) {
	}

	@Override
	public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex) {
	}

	@Override
	public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, Collection<String> collection, T object) {
	}

	@Override
	public void draw(int vertexCount, int instanceCount) {
	}

	@Override
	public void writeTimestamp(GpuQueryPool gpuQueryPool, int index) {
	}
}
