package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceLimits;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;

final class MetalDeviceBackend implements GpuDeviceBackend {
	private static final DeviceLimits DEFAULT_LIMITS = new DeviceLimits(16, 256, 16384);

	private final MetalSurfaceDescriptor surfaceDescriptor;
	private final DeviceInfo deviceInfo;
	private boolean closed;

	MetalDeviceBackend(MetalSurfaceDescriptor surfaceDescriptor) {
		this.surfaceDescriptor = surfaceDescriptor;
		this.deviceInfo = new DeviceInfo(
			"Metal Bootstrap Device",
			"Apple",
			"metalexp-bootstrap",
			true,
			"Metal",
			1.0F,
			DEFAULT_LIMITS,
			Set.of()
		);
	}

	MetalSurfaceDescriptor surfaceDescriptor() {
		return this.surfaceDescriptor;
	}

	@Override
	public GpuSurfaceBackend createSurface(long window) {
		return new MetalSurfaceBackend(this.surfaceDescriptor);
	}

	@Override
	public com.mojang.blaze3d.systems.CommandEncoderBackend createCommandEncoder() {
		throw unsupported("command encoding");
	}

	@Override
	public GpuSampler createSampler(AddressMode addressMode, AddressMode addressMode1, FilterMode filterMode, FilterMode filterMode1, int i, OptionalDouble optionalDouble) {
		throw unsupported("sampler creation");
	}

	@Override
	public GpuTexture createTexture(Supplier<String> supplier, int i, GpuFormat gpuFormat, int i1, int i2, int i3, int i4) {
		throw unsupported("texture creation");
	}

	@Override
	public GpuTexture createTexture(String s, int i, GpuFormat gpuFormat, int i1, int i2, int i3, int i4) {
		throw unsupported("texture creation");
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture) {
		throw unsupported("texture view creation");
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture, int i, int i1) {
		throw unsupported("texture view creation");
	}

	@Override
	public GpuBuffer createBuffer(Supplier<String> supplier, int i, long l) {
		throw unsupported("buffer creation");
	}

	@Override
	public GpuBuffer createBuffer(Supplier<String> supplier, int i, ByteBuffer byteBuffer) {
		throw unsupported("buffer creation");
	}

	@Override
	public List<String> getLastDebugMessages() {
		return List.of("MetalDeviceBackend bootstrap skeleton active.");
	}

	@Override
	public boolean isDebuggingEnabled() {
		return false;
	}

	@Override
	public CompiledRenderPipeline precompilePipeline(RenderPipeline renderPipeline, ShaderSource shaderSource) {
		throw unsupported("pipeline compilation");
	}

	@Override
	public void clearPipelineCache() {
	}

	@Override
	public void close() {
		this.closed = true;
	}

	@Override
	public GpuQueryPool createTimestampQueryPool(int i) {
		throw unsupported("timestamp query pools");
	}

	@Override
	public long getTimestampNow() {
		return 0L;
	}

	@Override
	public DeviceInfo getDeviceInfo() {
		return this.deviceInfo;
	}

	boolean isClosed() {
		return this.closed;
	}

	private static UnsupportedOperationException unsupported(String feature) {
		return new UnsupportedOperationException("Metal bootstrap backend does not implement " + feature + " yet.");
	}
}
